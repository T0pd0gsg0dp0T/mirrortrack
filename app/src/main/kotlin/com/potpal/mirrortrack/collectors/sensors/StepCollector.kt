package com.potpal.mirrortrack.collectors.sensors

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class StepCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "steps"
    override val displayName: String = "Step Counter"
    override val rationale: String =
        "Tracks step count via STEP_COUNTER (cumulative delta) and STEP_DETECTOR (per-step events). " +
            "Requires ACTIVITY_RECOGNITION permission on Android 10+."
    override val category: Category = Category.SENSORS
    override val requiredPermissions: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }
    override val accessTier: AccessTier = AccessTier.RUNTIME
    override val defaultEnabled: Boolean = false
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        var lastStepCount: Long = -1L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try {
                    when (event.sensor.type) {
                        Sensor.TYPE_STEP_COUNTER -> {
                            val currentCount = event.values[0].toLong()
                            if (lastStepCount < 0) {
                                // First reading; establish baseline
                                lastStepCount = currentCount
                                trySend(DataPoint.long(id, category, "step_counter_delta", 0L))
                            } else if (currentCount < lastStepCount) {
                                // Reboot detected: counter reset
                                Logger.d("StepCollector", "Step counter reset detected (reboot)")
                                trySend(DataPoint.long(id, category, "step_counter_delta", currentCount))
                                lastStepCount = currentCount
                            } else {
                                val delta = currentCount - lastStepCount
                                lastStepCount = currentCount
                                trySend(DataPoint.long(id, category, "step_counter_delta", delta))
                            }
                            trySend(DataPoint.long(id, category, "step_counter_total", currentCount))
                        }
                        Sensor.TYPE_STEP_DETECTOR -> {
                            trySend(DataPoint.long(id, category, "step_detected", 1L))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("StepCollector", "Error emitting step data", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        stepCounter?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("StepCollector", "Registered step counter listener")
        }
        stepDetector?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("StepCollector", "Registered step detector listener")
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
            Logger.d("StepCollector", "Unregistered step sensor listeners")
        }
    }
}
