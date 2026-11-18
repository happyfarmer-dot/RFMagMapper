package com.rfmagmapper.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.rfmagmapper.app.model.GnssAnchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Module E: Dual-Frequency GNSS (L1 + L5) Outdoor Reference Anchor.
 * Uses LocationManager + GnssMeasurementsEvent to capture L1 (~1575.42 MHz) and
 * L5 (~1176.45 MHz) carrier frequencies, satellite CN0, and coordinates as the
 * geospatial reference tag for the indoor RF/magnetic heatmap.
 */
class GnssAnchorEngine(context: Context) {

    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(GnssAnchor())
    val state: StateFlow<GnssAnchor> = _state.asStateFlow()

    private var active = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.value = _state.value.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = location.altitude,
                accuracyMeters = location.accuracy,
                hasFix = true,
                timestampMs = System.currentTimeMillis()
            )
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val gnssMeasurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            val measurements = eventArgs.measurements
            if (measurements.isEmpty()) return

            var l1Count = 0
            var l5Count = 0
            var cn0Sum = 0.0
            var cn0Count = 0

            for (m in measurements) {
                if (m.cn0DbHz > 0) {
                    cn0Sum += m.cn0DbHz
                    cn0Count++
                }
                if (m.hasCarrierFrequencyHz()) {
                    val freqMhz = m.carrierFrequencyHz / 1_000_000f
                    when {
                        // L1 / E1 / B1 band (~1561 - 1602 MHz)
                        freqMhz in 1555f..1610f -> l1Count++
                        // L5 / E5a / B2a band (~1176.45 MHz)
                        abs(freqMhz - 1176.45f) < 15f -> l5Count++
                    }
                }
            }

            val avgCn0 = if (cn0Count > 0) (cn0Sum / cn0Count).toFloat() else 0f
            val isDual = l1Count > 0 && l5Count > 0

            _state.value = _state.value.copy(
                avgCn0DbHz = avgCn0,
                l1SatCount = l1Count,
                l5SatCount = l5Count,
                isDualFrequencyL1L5 = isDual,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (active) return
        active = true
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1.0f,
                    locationListener
                )
            }
            locationManager.registerGnssMeasurementsCallback(gnssMeasurementsCallback)
        } catch (_: SecurityException) {
            active = false
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!active) return
        active = false
        try {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
        } catch (_: Exception) {
        }
    }
}
