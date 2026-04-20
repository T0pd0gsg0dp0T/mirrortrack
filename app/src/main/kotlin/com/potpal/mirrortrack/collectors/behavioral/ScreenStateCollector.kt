package com.potpal.mirrortrack.collectors.behavioral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * Screen on/off and user-present (unlock) events. Streamed, not polled —
 * these are delivered by the OS as broadcasts. Registered programmatically
 * at runtime (not in the manifest) because ACTION_SCREEN_ON/OFF are not
 * deliverable to manifest-registered receivers since Android 8.
 *
 * Emits one DataPoint per transition. The foreground service must be running
 * for this collector to stay alive across doze/standby.
 */
@Singleton
class ScreenStateCollector @Inject constructor() : Collector {

    override val id = "screen_state"
    override val displayName = "Screen State"
    override val rationale =
        "Records screen on/off and unlock (user present) transitions. No " +
            "permission required. These events are how analytics SDKs compute " +
            "session starts and engagement time."
    override val category = Category.BEHAVIORAL
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier = AccessTier.NONE
    override val defaultEnabled = true
    override val defaultPollInterval = null
    override val defaultRetention = 90.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val event = when (action) {
                    Intent.ACTION_SCREEN_ON -> "screen_on"
                    Intent.ACTION_SCREEN_OFF -> "screen_off"
                    Intent.ACTION_USER_PRESENT -> "user_present"
                    else -> return
                }
                trySend(DataPoint.string(id, category, "event", event))
            }
        }

        context.registerReceiver(receiver, filter)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
