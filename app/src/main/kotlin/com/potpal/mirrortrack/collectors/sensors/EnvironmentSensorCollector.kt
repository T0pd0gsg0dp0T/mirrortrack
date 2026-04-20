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
class EnvironmentSensorCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "environment_sensor"
    override val displayName: String = "Environment Sensors"
    override val rationale: String =
        "Streams ambient light, proximity, barometric pressure, and temperature data " +
            "downsampled to 0.2 Hz (one sample every 5 seconds). No permissions required. " +
            "Gracefully degrades when individual sensors are absent."
    override val category: Category = Category.SENSORS
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultRetention: Duration = 7.days

    override suspend fun isAvailable(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        // Available if at least one environment sensor is present
        return sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null
    }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        // 0.2 Hz downsampling = 1 sample every 5 seconds
        val lastEmitNs = mutableMapOf<Int, Long>()
        val fiveSecondsNs = 5_000_000_000L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sensorType = event.sensor.type
                val now = System.nanoTime()
                val lastNs = lastEmitNs[sensorType] ?: 0L

                if (now - lastNs < fiveSecondsNs) return
                lastEmitNs[sensorType] = now

                try {
                    when (sensorType) {
                        Sensor.TYPE_LIGHT -> {
                            trySend(DataPoint.double(id, category, "ambient_light_lux", event.values[0].toDouble()))
                        }
                        Sensor.TYPE_PROXIMITY -> {
                            trySend(DataPoint.double(id, category, "proximity_cm", event.values[0].toDouble()))
                        }
                        Sensor.TYPE_PRESSURE -> {
                            trySend(DataPoint.double(id, category, "barometric_pressure_hpa", event.values[0].toDouble()))
                        }
                        Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                            trySend(DataPoint.double(id, category, "ambient_temperature_c", event.values[0].toDouble()))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("EnvironmentSensorCollector", "Error emitting sensor data", e)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        light?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("EnvironmentSensorCollector", "Registered light sensor listener")
        }
        proximity?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("EnvironmentSensorCollector", "Registered proximity sensor listener")
        }
        pressure?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("EnvironmentSensorCollector", "Registered pressure sensor listener")
        }
        temperature?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            Logger.d("EnvironmentSensorCollector", "Registered temperature sensor listener")
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
            Logger.d("EnvironmentSensorCollector", "Unregistered all environment sensor listeners")
        }
    }
}
