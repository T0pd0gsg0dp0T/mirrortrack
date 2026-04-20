package com.potpal.mirrortrack.collectors

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * Base contract for all data collectors.
 *
 * Two execution models are supported and a collector picks one:
 *
 *   Polled   — override [collect]. Called by CollectionScheduler at
 *              [defaultPollInterval]. Returns a snapshot of DataPoints.
 *              Example: BuildInfoCollector, BatteryCollector, WifiCollector.
 *
 *   Streamed — override [stream]. Returns a long-lived Flow of DataPoints
 *              produced by an OS event source (BroadcastReceiver, sensor
 *              listener, NotificationListenerService). [defaultPollInterval]
 *              must be null. Ingestor collects from the flow while the
 *              collector is enabled.
 *              Example: ScreenStateCollector, MotionSensorCollector.
 *
 * Collectors must be:
 *  - Stateless, or hold only immutable configuration set at construction.
 *  - Safe to call [collect] repeatedly from any dispatcher.
 *  - Free of references to short-lived Contexts (Activity, Fragment).
 *    Use @ApplicationContext if injecting via Hilt.
 */
interface Collector {
    /** Stable identifier, snake_case. Stored in DataPoint.collectorId. */
    val id: String

    /** Human-facing label for UI. */
    val displayName: String

    /** One-sentence explanation of what this collector reads and why. */
    val rationale: String

    val category: Category

    /** Android runtime permissions required. Empty list = no runtime prompt. */
    val requiredPermissions: List<String>

    /** Permission tier — drives which UI onboarding path this collector uses. */
    val accessTier: AccessTier

    /**
     * True iff this collector ships enabled by default. Per CLAUDE.md rule #4,
     * this must be false for every new collector introducing new data access.
     * Only genuinely zero-risk collectors (e.g. Build.MODEL) may default true.
     */
    val defaultEnabled: Boolean get() = false

    /**
     * Null for streamed collectors. For polled collectors, the default cadence.
     * User can override per-collector in settings.
     */
    val defaultPollInterval: Duration? get() = null

    /**
     * Returns true if this collector can run right now — hardware present,
     * permissions granted, special access granted. Called before every
     * collect()/stream() to short-circuit when state has changed.
     */
    suspend fun isAvailable(context: Context): Boolean

    /**
     * Default retention for this collector's data. Null = keep forever.
     * User can override per-collector in settings.
     */
    val defaultRetention: Duration? get() = null

    /** Polled entry point. Default = not polled. */
    suspend fun collect(context: Context): List<DataPoint> = emptyList()

    /** Streamed entry point. Default = no stream. */
    fun stream(context: Context): Flow<DataPoint> = emptyFlow()
}

/**
 * Drives onboarding UX and Play Store policy disclosures.
 *
 * NONE           — available at boot, no prompt.
 * RUNTIME        — standard runtime permission prompt.
 * SPECIAL_ACCESS — user must enable in Settings manually (Usage access,
 *                  Notification listener, etc.). Requires in-app walkthrough.
 * RESTRICTED     — Play Store policy flag. Keep short, justify in store listing.
 */
/**
 * Drives onboarding UX and Play Store policy disclosures.
 *
 * NONE           — available at boot, no prompt.
 * RUNTIME        — standard runtime permission prompt.
 * SPECIAL_ACCESS — user must enable in Settings manually (Usage access,
 *                  Notification listener, etc.). Requires in-app walkthrough.
 * RESTRICTED     — Play Store policy flag. Keep short, justify in store listing.
 * ADB            — development-level permission, only grantable via
 *                  `adb shell pm grant` or `adb shell appops set`. Survives
 *                  reboot. Unlocks data categories inaccessible to normal apps.
 */
enum class AccessTier { NONE, RUNTIME, SPECIAL_ACCESS, RESTRICTED, ADB }
