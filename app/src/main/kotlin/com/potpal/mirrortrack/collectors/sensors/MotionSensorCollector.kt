package com.potpal.mirrortrack.collectors.sensors

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class MotionSensorCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "motion_sensor"
    override val displayName: String = "Motion Sensors"
    override val rationale: String =
        "Streams accelerometer, gyroscope, and magnetometer data downsampled to 1 Hz. " +
            "No permissions required; only subscribes when the sensor hardware is present."
    override val category: Category = Category.SENSORS
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultRetention: Duration = 7.days

    override suspend fun isAvailable(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // 1 Hz downsampling: track last emit time per sensor type
        val lastEmitNs = mutableMapOf<Int, Long>()
        val oneSecondNs = 1_000_000_000L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sensorType = event.sensor.type
                val now = System.nanoTime()
                val lastNs = lastEmitNs[sensorType] ?: 0L

                if (now - lastNs < oneSecondNs) return
                lastEmitNs[sensorType] = now

                try {
                    when (sensorType) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            trySend(DataPoint.double(id, category, "accel_x", event.values[0].toDouble()))
                            trySend(DataPoint.double(id, category, "accel_y", event.values[1].toDouble()))
                            trySend(DataPoint.double(id, category, "accel_z", event.values[2].toDouble()))
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            trySend(DataPoint.double(id, category, "gyro_x", event.values[0].toDouble()))
                            trySend(DataPoint.double(id, category, "gyro_y", event.values[1].toDouble()))
                            trySend(DataPoint.double(id, category, "gyro_z", event.values[2].toDouble()))
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            trySend(DataPoint.double(id, category, "mag_x", event.values[0].toDouble()))
                            trySend(DataPoint.double(id, category, "mag_y", event.values[1].toDouble()))
                            trySend(DataPoint.double(id, category, "mag_z", event.values[2].toDouble()))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("MotionSensorCollector", "Error emitting sensor data", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accel?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("MotionSensorCollector", "Registered accelerometer listener")
        }
        gyro?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("MotionSensorCollector", "Registered gyroscope listener")
        }
        mag?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("MotionSensorCollector", "Registered magnetometer listener")
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
            Logger.d("MotionSensorCollector", "Unregistered all motion sensor listeners")
        }
    }
}
