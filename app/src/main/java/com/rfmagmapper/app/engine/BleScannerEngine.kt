package com.rfmagmapper.app.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.rfmagmapper.app.dsp.RfPhysics
import com.rfmagmapper.app.model.BleSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Module B: Bluetooth LE Ambient RF Scanner.
 * Scans using SCAN_MODE_LOW_LATENCY and computes active emitter count,
 * strongest single device RSSI, and linear total BLE power density (mW / dBm).
 */
class BleScannerEngine(context: Context) {

    private val bluetoothManager =
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val scanner: BluetoothLeScanner?
        get() = bluetoothManager?.adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow(BleSummary())
    val state: StateFlow<BleSummary> = _state.asStateFlow()

    private data class SeenBleDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val lastSeenMs: Long
    )

    private val activeDevices = ConcurrentHashMap<String, SeenBleDevice>()
    private var aggregateJob: Job? = null
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device?.address ?: return
            val name = try {
                result.device?.name ?: result.scanRecord?.deviceName ?: addr
            } catch (_: SecurityException) {
                addr
            }
            activeDevices[addr] = SeenBleDevice(
                address = addr,
                name = name,
                rssi = result.rssi,
                lastSeenMs = System.currentTimeMillis()
            )
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        val leScanner = scanner
        if (leScanner != null && !scanning) {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                leScanner.startScan(null, settings, scanCallback)
                scanning = true
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        aggregateJob?.cancel()
        aggregateJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val windowMs = 8000L
                val iterator = activeDevices.entries.iterator()
                while (iterator.hasNext()) {
                    if (now - iterator.next().value.lastSeenMs > windowMs) {
                        iterator.remove()
                    }
                }

                val snapshot = activeDevices.values.toList()
                if (snapshot.isEmpty()) {
                    _state.value = BleSummary(lastUpdateMs = now)
                } else {
                    val (totalMw, totalDbm) = RfPhysics.sumRssiInLinearPower(snapshot.map { it.rssi })
                    val strongest = snapshot.maxByOrNull { it.rssi }
                    _state.value = BleSummary(
                        activeEmitterCount = snapshot.size,
                        strongestRssiDbm = strongest?.rssi ?: -110,
                        strongestDeviceName = strongest?.name ?: "--",
                        totalPowerDensityMw = totalMw,
                        totalEquivalentDbm = totalDbm,
                        lastUpdateMs = now
                    )
                }
                delay(500L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        aggregateJob?.cancel()
        aggregateJob = null
        if (scanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            scanning = false
        }
    }
}
