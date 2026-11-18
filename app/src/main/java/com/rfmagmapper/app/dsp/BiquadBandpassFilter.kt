package com.rfmagmapper.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2nd-order IIR Biquad Bandpass Filter (Constant 0 dB peak gain).
 *
 * Designed for 100 Hz magnetometer sampling (Nyquist = 50 Hz):
 * A 60 Hz AC magnetic field aliases to |100 - 60| = 40 Hz.
 * With center frequency f0 = 40 Hz and bandwidth BW = 4 Hz (38-42 Hz), Q = f0 / BW = 10.0.
 */
class BiquadBandpassFilter(
    sampleRateHz: Double = 100.0,
    centerFreqHz: Double = 40.0,
    qFactor: Double = 10.0
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val w0 = 2.0 * PI * centerFreqHz / sampleRateHz
        val alpha = sin(w0) / (2.0 * qFactor)
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha

        b0 = alpha / a0
        b1 = 0.0
        b2 = -alpha / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun process(sample: Double): Double {
        val y = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}

/**
 * Dual-channel 3-axis magnetometer processor:
 * 1) 60 Hz AC magnetic field RMS (via 40 Hz aliased 2nd-order IIR bandpass on each axis)
 * 2) Static DC magnetic anomaly Delta-B relative to initial baseline
 */
class MagnetometerDspProcessor(
    sampleRateHz: Double = 100.0,
    private val rmsWindowSize: Int = 50,
    private val baselineSampleCount: Int = 50
) {
    private val filterX = BiquadBandpassFilter(sampleRateHz, 40.0, 10.0)
    private val filterY = BiquadBandpassFilter(sampleRateHz, 40.0, 10.0)
    private val filterZ = BiquadBandpassFilter(sampleRateHz, 40.0, 10.0)

    private val acSqBuffer = DoubleArray(rmsWindowSize)
    private var acBufferIdx = 0
    private var acFilled = 0

    private var baselineSum = 0.0
    private var baselineCount = 0
    var dcBaselineUt: Float = 0f
        private set
    var isBaselineReady: Boolean = false
        private set

    private var dcLowPassUt = 0.0
    private var hasFirstSample = false

    fun resetBaseline() {
        baselineSum = 0.0
        baselineCount = 0
        dcBaselineUt = 0f
        isBaselineReady = false
        hasFirstSample = false
        filterX.reset()
        filterY.reset()
        filterZ.reset()
        acSqBuffer.fill(0.0)
        acBufferIdx = 0
        acFilled = 0
    }

    data class Output(
        val totalFluxUt: Float,
        val ac60HzRmsUt: Float,
        val dcBaselineUt: Float,
        val dcAnomalyUt: Float,
        val isBaselineReady: Boolean
    )

    fun processSample(bx: Float, by: Float, bz: Float): Output {
        val totalFlux = sqrt((bx * bx + by * by + bz * bz).toDouble())

        // Channel 1: 40 Hz aliased 60 Hz AC magnetic field per axis
        val acX = filterX.process(bx.toDouble())
        val acY = filterY.process(by.toDouble())
        val acZ = filterZ.process(bz.toDouble())
        val acInstSq = acX * acX + acY * acY + acZ * acZ

        acSqBuffer[acBufferIdx] = acInstSq
        acBufferIdx = (acBufferIdx + 1) % rmsWindowSize
        if (acFilled < rmsWindowSize) acFilled++

        var meanSq = 0.0
        for (i in 0 until acFilled) {
            meanSq += acSqBuffer[i]
        }
        meanSq /= acFilled.coerceAtLeast(1)
        val acRms = sqrt(meanSq).toFloat()

        // Channel 2: DC Magnetic Anomaly (low-pass smoothed |B| vs baseline)
        if (!hasFirstSample) {
            dcLowPassUt = totalFlux
            hasFirstSample = true
        } else {
            dcLowPassUt = 0.9 * dcLowPassUt + 0.1 * totalFlux
        }

        if (!isBaselineReady) {
            baselineSum += totalFlux
            baselineCount++
            if (baselineCount >= baselineSampleCount) {
                dcBaselineUt = (baselineSum / baselineCount).toFloat()
                isBaselineReady = true
            } else {
                dcBaselineUt = (baselineSum / baselineCount).toFloat()
            }
        }

        val dcAnomaly = if (isBaselineReady) {
            kotlin.math.abs(dcLowPassUt - dcBaselineUt).toFloat()
        } else {
            0f
        }

        return Output(
            totalFluxUt = totalFlux.toFloat(),
            ac60HzRmsUt = acRms,
            dcBaselineUt = dcBaselineUt,
            dcAnomalyUt = dcAnomaly,
            isBaselineReady = isBaselineReady
        )
    }
}
