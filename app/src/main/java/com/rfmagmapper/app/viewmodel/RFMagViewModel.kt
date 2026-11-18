package com.rfmagmapper.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfmagmapper.app.dsp.HeatmapInterpolator
import com.rfmagmapper.app.dsp.RfPhysics
import com.rfmagmapper.app.engine.BleScannerEngine
import com.rfmagmapper.app.engine.GnssAnchorEngine
import com.rfmagmapper.app.engine.MagnetometerEngine
import com.rfmagmapper.app.engine.PdrBarometerEngine
import com.rfmagmapper.app.engine.WifiScannerEngine
import com.rfmagmapper.app.export.ExportManager
import com.rfmagmapper.app.model.BleSummary
import com.rfmagmapper.app.model.GnssAnchor
import com.rfmagmapper.app.model.GridCell
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.model.HotspotMarker
import com.rfmagmapper.app.model.MagReading
import com.rfmagmapper.app.model.PdrState
import com.rfmagmapper.app.model.SamplePoint
import com.rfmagmapper.app.model.WifiScanSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RFMagViewModel(application: Application) : AndroidViewModel(application) {

    private val wifiEngine = WifiScannerEngine(application)
    private val bleEngine = BleScannerEngine(application)
    private val magEngine = MagnetometerEngine(application)
    private val pdrEngine = PdrBarometerEngine(application)
    private val gnssEngine = GnssAnchorEngine(application)

    val wifiState: StateFlow<WifiScanSummary> = wifiEngine.state
    val bleState: StateFlow<BleSummary> = bleEngine.state
    val magState: StateFlow<MagReading> = magEngine.state
    val pdrState: StateFlow<PdrState> = pdrEngine.state
    val gnssState: StateFlow<GnssAnchor> = gnssEngine.state

    private val _isMapping = MutableStateFlow(false)
    val isMapping: StateFlow<Boolean> = _isMapping.asStateFlow()

    private val _selectedLayer = MutableStateFlow(HeatmapLayer.COMPOSITE)
    val selectedLayer: StateFlow<HeatmapLayer> = _selectedLayer.asStateFlow()

    private val _samples = MutableStateFlow<List<SamplePoint>>(emptyList())
    val samples: StateFlow<List<SamplePoint>> = _samples.asStateFlow()

    private val _gridCells = MutableStateFlow<List<GridCell>>(emptyList())
    val gridCells: StateFlow<List<GridCell>> = _gridCells.asStateFlow()

    private val _hotspots = MutableStateFlow<List<HotspotMarker>>(emptyList())
    val hotspots: StateFlow<List<HotspotMarker>> = _hotspots.asStateFlow()

    private val _showWifiThrottleGuide = MutableStateFlow(true)
    val showWifiThrottleGuide: StateFlow<Boolean> = _showWifiThrottleGuide.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var samplingJob: Job? = null
    private var lastHotspotVibrateMs = 0L

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        pdrEngine.onStepCallback = { updatedPdr ->
            if (_isMapping.value) {
                recordSampleAt(updatedPdr)
            }
        }
    }

    fun startEnginesAndMapping() {
        if (_isMapping.value) return
        _isMapping.value = true

        wifiEngine.start(viewModelScope)
        bleEngine.start(viewModelScope)
        magEngine.start()
        pdrEngine.start()
        gnssEngine.start()

        samplingJob?.cancel()
        samplingJob = viewModelScope.launch(Dispatchers.Default) {
            // Initial sample at origin
            delay(400L)
            recordSampleAt(pdrState.value)
            while (isActive) {
                delay(1500L)
                recordSampleAt(pdrState.value)
            }
        }
    }

    fun stopMapping() {
        _isMapping.value = false
        samplingJob?.cancel()
        samplingJob = null
        wifiEngine.stop()
        bleEngine.stop()
        magEngine.stop()
        pdrEngine.stop()
        gnssEngine.stop()
    }

    fun selectLayer(layer: HeatmapLayer) {
        _selectedLayer.value = layer
        recomputeHeatmap()
    }

    fun setStepLength(meters: Float) {
        pdrEngine.setStepLength(meters)
    }

    fun zeroHeading() {
        pdrEngine.zeroHeading()
        _statusBanner.value = "已重置陀螺儀航向基準 (0°)"
    }

    fun rezeroMagBaseline() {
        magEngine.rezeroDcBaseline()
        _statusBanner.value = "已重新校準靜態直流磁場基準值"
    }

    fun triggerManualStep() {
        pdrEngine.triggerManualStep()
        vibratePulse(30L)
    }

    fun clearMap() {
        _samples.value = emptyList()
        _gridCells.value = emptyList()
        _hotspots.value = emptyList()
        pdrEngine.resetOrigin()
        _statusBanner.value = "已清除熱區地圖與軌跡"
    }

    fun dismissWifiThrottleGuide() {
        _showWifiThrottleGuide.value = false
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    private fun recordSampleAt(pdr: PdrState) {
        val wifi = wifiState.value
        val ble = bleState.value
        val mag = magState.value

        val composite = RfPhysics.computeCompositeIndex(
            wifiTotalDbm = wifi.totalEquivalentDbm,
            bleCount = ble.activeEmitterCount,
            bleTotalDbm = ble.totalEquivalentDbm,
            acMag60HzRmsUt = mag.ac60HzRmsUt
        )

        val point = SamplePoint(
            x = pdr.xMeters,
            y = pdr.yMeters,
            floor = pdr.currentFloor,
            timestampMs = System.currentTimeMillis(),
            wifiTotalDbm = wifi.totalEquivalentDbm,
            wifiTotalMw = wifi.totalPowerMw,
            wifiApCount = wifi.apCount,
            wifiTop3 = wifi.top3Aps,
            bleCount = ble.activeEmitterCount,
            bleMaxRssi = ble.strongestRssiDbm,
            bleTotalMw = ble.totalPowerDensityMw,
            acMag60HzRmsUt = mag.ac60HzRmsUt,
            dcMagAnomalyUt = mag.dcAnomalyUt,
            compositeIndex = composite
        )

        val updatedList = _samples.value + point
        _samples.value = updatedList

        // Haptic alert if entering a high-intensity red zone
        val norm = HeatmapInterpolator.normalizeValue(
            HeatmapInterpolator.extractLayerValue(point, _selectedLayer.value),
            _selectedLayer.value
        )
        val now = System.currentTimeMillis()
        if (norm >= 0.78f && now - lastHotspotVibrateMs > 4000L) {
            lastHotspotVibrateMs = now
            vibratePulse(80L)
        }

        recomputeHeatmap()
    }

    private fun recomputeHeatmap() {
        val currentSamples = _samples.value
        val layer = _selectedLayer.value
        val floor = pdrState.value.currentFloor
        viewModelScope.launch(Dispatchers.Default) {
            val result = HeatmapInterpolator.interpolate(currentSamples, layer, floor)
            _gridCells.value = result.cells
            _hotspots.value = result.hotspots
        }
    }

    fun exportPng(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = ExportManager.exportHeatmapPng(
                context = context,
                layer = _selectedLayer.value,
                cells = _gridCells.value,
                hotspots = _hotspots.value,
                trajectory = pdrState.value.trajectory,
                activeFloor = pdrState.value.currentFloor,
                gnssAnchor = gnssState.value
            )
            _statusBanner.value = if (res.success) {
                "PNG 熱區圖已匯出至 ${res.displayPath}"
            } else {
                "PNG 匯出失敗: ${res.errorMessage}"
            }
        }
    }

    fun exportJson(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = ExportManager.exportRawJson(
                context = context,
                samples = _samples.value,
                gnssAnchor = gnssState.value
            )
            _statusBanner.value = if (res.success) {
                "JSON 原始數據已匯出至 ${res.displayPath}"
            } else {
                "JSON 匯出失敗: ${res.errorMessage}"
            }
        }
    }

    private fun vibratePulse(durationMs: Long) {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMapping()
    }
}
