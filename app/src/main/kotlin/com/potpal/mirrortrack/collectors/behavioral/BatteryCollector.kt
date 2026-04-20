package com.potpal.mirrortrack.collectors.behavioral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
class BatteryCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "battery"
    override val displayName: String = "Battery"
    override val rationale: String =
        "Tracks battery state changes (level, charging, health). Zero-risk, requires no permissions."
    override val category: Category = Category.BEHAVIORAL
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = true
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                    val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

                    val isCharging = plugged != 0

                    trySend(DataPoint.long(id, category, "level", level.toLong()))
                    trySend(DataPoint.long(id, category, "scale", scale.toLong()))
                    trySend(DataPoint.bool(id, category, "is_charging", isCharging))
                    trySend(DataPoint.long(id, category, "charge_plug", plugged.toLong()))
                    trySend(DataPoint.long(id, category, "status", status.toLong()))
                    trySend(DataPoint.long(id, category, "health", health.toLong()))
                    trySend(DataPoint.long(id, category, "temperature_tenths_c", temperature.toLong()))
                    trySend(DataPoint.long(id, category, "voltage_mv", voltage.toLong()))
                    trySend(DataPoint.string(id, category, "technology", technology ?: "unknown"))
                } catch (e: Exception) {
                    Logger.e("BatteryCollector", "Error processing battery intent", e)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        Logger.d("BatteryCollector", "Registered battery receiver")

        awaitClose {
            context.unregisterReceiver(receiver)
            Logger.d("BatteryCollector", "Unregistered battery receiver")
        }
    }
}
