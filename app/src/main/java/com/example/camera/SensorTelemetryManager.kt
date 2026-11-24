package com.example.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.GyroSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class OrientationState(
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isLevelAligned: Boolean = false
)

class SensorTelemetryManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _orientation = MutableStateFlow(OrientationState())
    val orientation: StateFlow<OrientationState> = _orientation.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var smoothRoll = 0f
    private var smoothPitch = 0f

    @Volatile
    private var recordingGyro = false
    private val gyroSamples = ArrayList<GyroSample>(2048)

    private var isStarted = false
    private var lastEmitMs = 0L
    private var lastEmittedRoll = 999f
    private var lastEmittedPitch = 999f
    private var lastEmittedAligned = false

    fun start() {
        if (isStarted) return
        isStarted = true
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        isStarted = false
        recordingGyro = false
        sensorManager.unregisterListener(this)
    }

    fun startGyroRecording() {
        synchronized(gyroSamples) {
            gyroSamples.clear()
            recordingGyro = true
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopAndSaveGyroTrack(outputFile: File): String? {
        gyroSensor?.let {
            sensorManager.unregisterListener(this, it)
        }
        val snapshot: List<GyroSample>
        synchronized(gyroSamples) {
            recordingGyro = false
            if (gyroSamples.isEmpty()) return null
            snapshot = ArrayList(gyroSamples)
            gyroSamples.clear()
        }
        return runCatching {
            BufferedWriter(FileWriter(outputFile)).use { writer ->
                for (s in snapshot) {
                    writer.write("${s.timestampNs},${s.wx},${s.wy},${s.wz}\n")
                }
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                updateFilteredOrientation(rollDeg, pitchDeg)
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val roll = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                val pitch = Math.toDegrees(atan2(gz.toDouble(), sqrt((gx * gx + gy * gy).toDouble()))).toFloat()
                updateFilteredOrientation(roll, pitch)
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (recordingGyro) {
                    synchronized(gyroSamples) {
                        if (recordingGyro && gyroSamples.size < 18_000) {
                            gyroSamples.add(
                                GyroSample(
                                    timestampNs = event.timestamp,
                                    wx = event.values[0],
                                    wy = event.values[1],
                                    wz = event.values[2]
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateFilteredOrientation(rawRoll: Float, rawPitch: Float) {
        val alpha = 0.25f
        smoothRoll += alpha * (rawRoll - smoothRoll)
        smoothPitch += alpha * (rawPitch - smoothPitch)
        val aligned = abs(smoothRoll) <= 1.2f || abs(abs(smoothRoll) - 90f) <= 1.2f
        val now = android.os.SystemClock.elapsedRealtime()
        val movedEnough = abs(smoothRoll - lastEmittedRoll) >= 0.45f ||
            abs(smoothPitch - lastEmittedPitch) >= 0.45f ||
            aligned != lastEmittedAligned
        if (!movedEnough || (now - lastEmitMs < 55L && aligned == lastEmittedAligned)) return
        lastEmitMs = now
        lastEmittedRoll = smoothRoll
        lastEmittedPitch = smoothPitch
        lastEmittedAligned = aligned
        _orientation.value = OrientationState(
            rollDegrees = smoothRoll,
            pitchDegrees = smoothPitch,
            isLevelAligned = aligned
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
