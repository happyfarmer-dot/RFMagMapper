package com.rfmagmapper.app.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.rfmagmapper.app.model.PdrState
import com.rfmagmapper.app.model.TrajectoryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Module D: Indoor PDR (Pedestrian Dead Reckoning) + Barometer Floor Detection.
 * - Uses Sensor.TYPE_STEP_DETECTOR (default step length 0.65 m, adjustable)
 * - Uses Sensor.TYPE_GAME_ROTATION_VECTOR (magnetometer-free gyro+accel heading to avoid indoor magnetic interference)
 * - Uses Sensor.TYPE_PRESSURE to compute relative Delta-h and detect floor transitions (every 3.2 m)
 */
class PdrBarometerEngine(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gameRotation = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _state = MutableStateFlow(PdrState())
    val state: StateFlow<PdrState> = _state.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var rawHeadingRad = 0f
    private var headingOffsetRad: Float? = null

    private var baselineAltitudeMeters: Float? = null
    private var lastPressureEmitNs = 0L
    private var lastHardwareStepMs = 0L
    private var lastAccelStepMs = 0L

    var onStepCallback: ((PdrState) -> Unit)? = null

    fun start() {
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        linearAccel?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gameRotation?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setStepLength(meters: Float) {
        val clamped = meters.coerceIn(0.40f, 1.00f)
        _state.value = _state.value.copy(stepLengthMeters = clamped)
    }

    fun zeroHeading() {
        headingOffsetRad = rawHeadingRad
        _state.value = _state.value.copy(headingRad = 0f, headingDeg = 0f)
    }

    fun resetOrigin() {
        headingOffsetRad = rawHeadingRad
        baselineAltitudeMeters = null
        _state.value = PdrState(
            stepLengthMeters = _state.value.stepLengthMeters,
            pressureHpa = _state.value.pressureHpa,
            trajectory = listOf(TrajectoryPoint(0f, 0f, 1, System.currentTimeMillis()))
        )
    }

    /**
     * Allows manual step trigger for stationary desk testing or calibration.
     */
    fun triggerManualStep() {
        advanceOneStep()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                rawHeadingRad = orientationAngles[0]
                if (headingOffsetRad == null) {
                    headingOffsetRad = rawHeadingRad
                }
                val relRad = normalizeAngleRad(rawHeadingRad - (headingOffsetRad ?: 0f))
                val relDeg = Math.toDegrees(relRad.toDouble()).toFloat().let {
                    if (it < 0f) it + 360f else it
                }
                _state.value = _state.value.copy(
                    headingRad = relRad,
                    headingDeg = relDeg
                )
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                lastHardwareStepMs = System.currentTimeMillis()
                advanceOneStep()
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val now = System.currentTimeMillis()
                // Fallback for slow indoor walking when hardware step detector hasn't fired recently
                if (now - lastHardwareStepMs > 1200L && now - lastAccelStepMs > 420L) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val mag = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble())
                    if (mag > 1.45) {
                        lastAccelStepMs = now
                        advanceOneStep()
                    }
                }
            }

            Sensor.TYPE_PRESSURE -> {
                val nowNs = event.timestamp
                if (nowNs - lastPressureEmitNs < 250_000_000L) return
                lastPressureEmitNs = nowNs

                val pressureHpa = event.values[0]
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
                if (baselineAltitudeMeters == null) {
                    baselineAltitudeMeters = alt
                }
                val deltaH = alt - (baselineAltitudeMeters ?: alt)
                // Floor height = 3.2 meters
                val floorOffset = (deltaH / 3.2f).roundToInt()
                val currentFloor = 1 + floorOffset

                _state.value = _state.value.copy(
                    pressureHpa = pressureHpa,
                    deltaHeightMeters = deltaH,
                    currentFloor = currentFloor
                )
            }
        }
    }

    private fun advanceOneStep() {
        val cur = _state.value
        val stepLen = cur.stepLengthMeters
        val theta = cur.headingRad
        // +X = East (right), +Y = North (up in world coords; Canvas flips Y)
        val nextX = cur.xMeters + stepLen * sin(theta)
        val nextY = cur.yMeters + stepLen * cos(theta)
        val nextStepCount = cur.stepCount + 1
        val point = TrajectoryPoint(nextX, nextY, cur.currentFloor, System.currentTimeMillis())

        val updated = cur.copy(
            xMeters = nextX,
            yMeters = nextY,
            stepCount = nextStepCount,
            trajectory = cur.trajectory + point
        )
        _state.value = updated
        onStepCallback?.invoke(updated)
    }

    private fun normalizeAngleRad(angle: Float): Float {
        var a = angle
        while (a > PI) a -= (2.0 * PI).toFloat()
        while (a < -PI) a += (2.0 * PI).toFloat()
        return a
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
