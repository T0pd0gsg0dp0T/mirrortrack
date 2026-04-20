package com.potpal.mirrortrack.collectors.sensors

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class BodySensorsCollector @Inject constructor() : Collector {
    override val id = "body_sensors"
    override val displayName = "Heart Rate"
    override val rationale =
        "Records heart rate samples from the device's heart rate sensor, if present. " +
        "Requires BODY_SENSORS permission."
    override val category = Category.SENSORS
    override val requiredPermissions = listOf(Manifest.permission.BODY_SENSORS)
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval = null
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
    }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val heartSensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartSensor == null) {
            close()
            return@callbackFlow
        }

        var lastEmitTime = 0L
        val minInterval = 10_000L // 0.1Hz = 10s

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                if (now - lastEmitTime < minInterval) return
                lastEmitTime = now

                val bpm = event.values[0]
                if (bpm > 0) {
                    trySend(DataPoint.double(id, category, "heart_rate_bpm", bpm.toDouble()))
                    trySend(DataPoint.long(id, category, "accuracy_level", event.accuracy.toLong()))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sm.registerListener(listener, heartSensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }
}
