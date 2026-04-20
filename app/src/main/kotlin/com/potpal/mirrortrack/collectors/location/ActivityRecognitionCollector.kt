package com.potpal.mirrortrack.collectors.location

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A toy rule-based activity classifier based on accelerometer RMS over a 5-second window.
 * This is NOT an ML model — it uses simple threshold heuristics and should be treated as
 * a rough approximation only.
 */
@Singleton
class ActivityRecognitionCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "activity_recognition"
    override val displayName: String = "Activity Recognition"
    override val rationale: String =
        "Classifies user activity (still/walking/running/vehicle) using a lightweight " +
            "rule-based accelerometer heuristic. Requires ACTIVITY_RECOGNITION permission on Android 10+."
    override val category: Category = Category.LOCATION
    override val requiredPermissions: List<String> = listOf(Manifest.permission.ACTIVITY_RECOGNITION)
    override val accessTier: AccessTier = AccessTier.RUNTIME
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 5.minutes
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            Logger.w("ActivityRecognitionCollector", "SensorManager unavailable")
            return listOf(
                DataPoint.string(id, category, "activity", "unknown"),
                DataPoint.double(id, category, "confidence_estimate", 0.0)
            )
        }

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Logger.w("ActivityRecognitionCollector", "Accelerometer unavailable")
            return listOf(
                DataPoint.string(id, category, "activity", "unknown"),
                DataPoint.double(id, category, "confidence_estimate", 0.0)
            )
        }

        // Collect accelerometer samples for 5 seconds
        val samples = collectAccelSamples(sensorManager, accel, durationMs = 5_000L)

        if (samples.isEmpty()) {
            return listOf(
                DataPoint.string(id, category, "activity", "unknown"),
                DataPoint.double(id, category, "confidence_estimate", 0.0)
            )
        }

        // Compute RMS of magnitude deviations from gravity (~9.81)
        val gravity = 9.81
        val magnitudes = samples.map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
        val deviations = magnitudes.map { it - gravity }
        val rms = sqrt(deviations.map { it * it }.average())

        // Simple threshold-based classification
        val (activity, confidence) = when {
            rms < 0.3 -> "still" to 0.85
            rms < 1.5 -> "walking" to 0.65
            rms < 4.0 -> "running" to 0.55
            else -> "vehicle" to 0.40
        }

        Logger.d("ActivityRecognitionCollector", "RMS=$rms -> activity=$activity, confidence=$confidence")

        return listOf(
            DataPoint.string(id, category, "activity", activity),
            DataPoint.double(id, category, "confidence_estimate", confidence)
        )
    }

    private suspend fun collectAccelSamples(
        sensorManager: SensorManager,
        sensor: Sensor,
        durationMs: Long
    ): List<Triple<Double, Double, Double>> {
        return withTimeoutOrNull(durationMs + 2_000L) {
            suspendCancellableCoroutine { continuation ->
                val samples = mutableListOf<Triple<Double, Double, Double>>()
                val startTime = System.currentTimeMillis()

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        samples.add(
                            Triple(
                                event.values[0].toDouble(),
                                event.values[1].toDouble(),
                                event.values[2].toDouble()
                            )
                        )
                        if (System.currentTimeMillis() - startTime >= durationMs) {
                            sensorManager.unregisterListener(this)
                            continuation.resume(samples.toList())
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                continuation.invokeOnCancellation {
                    sensorManager.unregisterListener(listener)
                }

                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } ?: emptyList()
    }
}
