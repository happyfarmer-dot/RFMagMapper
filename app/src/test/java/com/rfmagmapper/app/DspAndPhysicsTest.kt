package com.rfmagmapper.app

import com.rfmagmapper.app.dsp.BiquadBandpassFilter
import com.rfmagmapper.app.dsp.HeatmapInterpolator
import com.rfmagmapper.app.dsp.RfPhysics
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.model.SamplePoint
import com.rfmagmapper.app.model.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DspAndPhysicsTest {

    @Test
    fun testLinearPowerSummation() {
        val (mw, dbm) = RfPhysics.sumRssiInLinearPower(listOf(-50, -50))
        // Two equal -50 dBm sources = +3.01 dB = -46.99 dBm
        assertEquals(-46.9897, dbm, 0.01)
        assertEquals(2e-5, mw, 1e-7)
    }

    @Test
    fun testWifi7BandClassification() {
        assertEquals(WifiBand.BAND_2_4G, RfPhysics.classifyWifiBand(2437))
        assertEquals(WifiBand.BAND_5G, RfPhysics.classifyWifiBand(5180))
        assertEquals(WifiBand.BAND_6G, RfPhysics.classifyWifiBand(6175))
    }

    @Test
    fun test60HzAliased40HzBandpassFilter() {
        val filter = BiquadBandpassFilter(sampleRateHz = 100.0, centerFreqHz = 40.0, qFactor = 10.0)
        // Feed 2 seconds (200 samples) of 60 Hz sine wave at 100 Hz sample rate
        var sumSq60 = 0.0
        for (n in 0 until 200) {
            val t = n / 100.0
            val sample = 10.0 * sin(2.0 * PI * 60.0 * t)
            val y = filter.process(sample)
            if (n >= 100) sumSq60 += y * y
        }
        val rms60 = sqrt(sumSq60 / 100.0)
        // Theoretical RMS of amplitude 10 sine is 10 / sqrt(2) = 7.071
        assertEquals(7.071, rms60, 0.4)

        // Feed DC + 10 Hz interference -> should be heavily attenuated
        filter.reset()
        var sumSqLow = 0.0
        for (n in 0 until 200) {
            val t = n / 100.0
            val sample = 50.0 + 10.0 * sin(2.0 * PI * 10.0 * t)
            val y = filter.process(sample)
            if (n >= 100) sumSqLow += y * y
        }
        val rmsLow = sqrt(sumSqLow / 100.0)
        assertTrue("Low frequency should be attenuated, got $rmsLow", rmsLow < 0.5)
    }

    @Test
    fun testIdwInterpolationAndHotspotDetection() {
        val samples = listOf(
            SamplePoint(0f, 0f, 1, 1L, -40.0, 1e-4, 5, emptyList(), 5, -60, 1e-6, 1.0f, 0.5f, 40f),
            SamplePoint(2f, 2f, 1, 2L, -30.0, 1e-3, 8, emptyList(), 15, -50, 1e-5, 14.0f, 3.0f, 92f)
        )
        val result = HeatmapInterpolator.interpolate(samples, HeatmapLayer.MAG_60HZ, activeFloor = 1)
        assertTrue(result.cells.isNotEmpty())
        assertTrue(result.hotspots.isNotEmpty())
        assertTrue(result.hotspots.first().label.contains("60Hz"))
    }
}
