package com.rfmagmapper.app.dsp

import com.rfmagmapper.app.model.WifiBand
import kotlin.math.log10
import kotlin.math.pow

object RfPhysics {

    private const val MIN_MW = 1e-11 // -110 dBm floor

    /**
     * Converts dBm to linear milliwatt power: P_mW = 10^(dBm / 10).
     */
    fun dbmToMilliwatts(dbm: Double): Double {
        return 10.0.pow(dbm / 10.0)
    }

    /**
     * Converts linear milliwatt power back to dBm: 10 * log10(P_mW).
     */
    fun milliwattsToDbm(mw: Double): Double {
        return if (mw <= MIN_MW) -110.0 else 10.0 * log10(mw)
    }

    /**
     * Sums a collection of RSSI values (in dBm) in the linear mW domain
     * and returns Pair(totalMw, totalEquivalentDbm).
     * Never takes an arithmetic mean of dBm!
     */
    fun sumRssiInLinearPower(rssiList: List<Int>): Pair<Double, Double> {
        if (rssiList.isEmpty()) return 0.0 to -110.0
        var totalMw = 0.0
        for (rssi in rssiList) {
            totalMw += dbmToMilliwatts(rssi.toDouble())
        }
        return totalMw to milliwattsToDbm(totalMw)
    }

    /**
     * Classifies Wi-Fi frequency (MHz) into 2.4 GHz, 5 GHz, or 6 GHz (Wi-Fi 6E / Wi-Fi 7).
     */
    fun classifyWifiBand(frequencyMhz: Int): WifiBand {
        return when (frequencyMhz) {
            in 2400..2500 -> WifiBand.BAND_2_4G
            in 4900..5924 -> WifiBand.BAND_5G
            in 5925..7125 -> WifiBand.BAND_6G
            else -> WifiBand.UNKNOWN
        }
    }

    /**
     * Computes a 0..100 composite RF + Electromagnetic environment index.
     * Weighted across:
     * - Wi-Fi total equivalent power (-90 dBm -> 0, -25 dBm -> 100), weight 40%
     * - BLE active emitter density & power, weight 20%
     * - 60 Hz AC magnetic field RMS (0 uT -> 0, >= 15 uT -> 100), weight 40%
     */
    fun computeCompositeIndex(
        wifiTotalDbm: Double,
        bleCount: Int,
        bleTotalDbm: Double,
        acMag60HzRmsUt: Float
    ): Float {
        val wifiNorm = ((wifiTotalDbm + 90.0) / 65.0).coerceIn(0.0, 1.0)
        val bleCountNorm = (bleCount / 25.0).coerceIn(0.0, 1.0)
        val blePowerNorm = ((bleTotalDbm + 95.0) / 60.0).coerceIn(0.0, 1.0)
        val bleNorm = 0.6 * bleCountNorm + 0.4 * blePowerNorm
        val magNorm = (acMag60HzRmsUt / 15.0).toDouble().coerceIn(0.0, 1.0)

        val score = (0.40 * wifiNorm + 0.20 * bleNorm + 0.40 * magNorm) * 100.0
        return score.toFloat().coerceIn(0f, 100f)
    }
}
