package com.potpal.mirrortrack.collectors.behavioral

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class AppLifecycleCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "app_lifecycle"
    override val displayName: String = "App Lifecycle"
    override val rationale: String =
        "Tracks app foreground/background transitions and session duration using ProcessLifecycleOwner. " +
            "No permissions required."
    override val category: Category = Category.BEHAVIORAL
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        var sessionUuid: String? = null
        var sessionStartMs: Long = 0L
        var lastBackgroundMs: Long = 0L
        val sessionGraceMs = 5_000L // 5s grace period for brief backgrounds

        val observer = LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        val now = System.currentTimeMillis()
                        val timeSinceBackground = now - lastBackgroundMs

                        if (sessionUuid == null || timeSinceBackground > sessionGraceMs) {
                            // New session
                            sessionUuid = UUID.randomUUID().toString()
                            sessionStartMs = now
                            Logger.d("AppLifecycleCollector", "New session: $sessionUuid")
                        } else {
                            Logger.d(
                                "AppLifecycleCollector",
                                "Resuming session $sessionUuid (background was ${timeSinceBackground}ms)"
                            )
                        }

                        trySend(DataPoint.string(id, category, "app_foreground", sessionUuid ?: "unknown"))
                        trySend(DataPoint.string(id, category, "session_start", sessionUuid ?: "unknown"))
                        trySend(DataPoint.string(id, category, "session_uuid", sessionUuid ?: "unknown"))
                    }
                    Lifecycle.Event.ON_STOP -> {
                        val now = System.currentTimeMillis()
                        lastBackgroundMs = now
                        val durationMs = now - sessionStartMs

                        trySend(DataPoint.string(id, category, "app_background", sessionUuid ?: "unknown"))
                        trySend(DataPoint.string(id, category, "session_end", sessionUuid ?: "unknown"))
                        trySend(DataPoint.long(id, category, "duration_ms", durationMs))

                        Logger.d("AppLifecycleCollector", "Session $sessionUuid backgrounded, duration=${durationMs}ms")
                    }
                    else -> { /* ignore other lifecycle events */ }
                }
            } catch (e: Exception) {
                Logger.e("AppLifecycleCollector", "Error processing lifecycle event", e)
            }
        }

        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        withContext(Dispatchers.Main) {
            lifecycle.addObserver(observer)
        }
        Logger.d("AppLifecycleCollector", "Registered lifecycle observer")

        awaitClose {
            lifecycle.removeObserver(observer)
            Logger.d("AppLifecycleCollector", "Removed lifecycle observer")
        }
    }
}
