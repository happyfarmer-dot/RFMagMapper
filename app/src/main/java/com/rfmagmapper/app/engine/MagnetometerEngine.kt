package com.rfmagmapper.app.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.rfmagmapper.app.dsp.MagnetometerDspProcessor
import com.rfmagmapper.app.model.MagReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Module C: 3-Axis Magnetometer Dual-Channel Low-Frequency Analysis @ 100 Hz.
 * Uses Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED (fallback TYPE_MAGNETIC_FIELD) at 10,000 us.
 * 1) 60 Hz AC Magnetic Field via 40 Hz aliased 2nd-order IIR bandpass filter (38-42 Hz)
 * 2) DC Magnetic Anomaly Delta-B (uT) relative to initial baseline
 */
class MagnetometerEngine(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val magSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val dsp = MagnetometerDspProcessor(sampleRateHz = 100.0)

    private val _state = MutableStateFlow(MagReading())
    val state: StateFlow<MagReading> = _state.asStateFlow()

    private var lastRateCheckNs = 0L
    private var sampleCounter = 0
    private var measuredHz = 100f
    private var lastUiEmitNs = 0L

    fun start() {
        magSensor?.let { sensor ->
            // 10,000 us = 100 Hz (requires HIGH_SAMPLING_RATE_SENSORS)
            sensorManager.registerListener(this, sensor, 10_000)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun rezeroDcBaseline() {
        dsp.resetBaseline()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val bx = event.values[0]
        val by = event.values[1]
        val bz = event.values[2]

        val out = dsp.processSample(bx, by, bz)

        val nowNs = event.timestamp
        if (lastRateCheckNs == 0L) {
            lastRateCheckNs = nowNs
        } else {
            sampleCounter++
            val elapsedNs = nowNs - lastRateCheckNs
            if (elapsedNs >= 1_000_000_000L) {
                measuredHz = (sampleCounter * 1e9f) / elapsedNs
                sampleCounter = 0
                lastRateCheckNs = nowNs
            }
        }

        // Throttle StateFlow emissions to ~20 Hz (50 ms) for smooth UI without over-rendering
        if (nowNs - lastUiEmitNs >= 50_000_000L) {
            lastUiEmitNs = nowNs
            _state.value = MagReading(
                rawX = bx,
                rawY = by,
                rawZ = bz,
                totalFluxUt = out.totalFluxUt,
                ac60HzRmsUt = out.ac60HzRmsUt,
                dcBaselineUt = out.dcBaselineUt,
                dcAnomalyUt = out.dcAnomalyUt,
                sampleRateHz = measuredHz,
                isBaselineReady = out.isBaselineReady
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
