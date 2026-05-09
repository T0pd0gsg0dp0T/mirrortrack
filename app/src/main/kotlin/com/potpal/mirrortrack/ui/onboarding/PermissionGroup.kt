package com.potpal.mirrortrack.ui.onboarding

import android.Manifest
import android.os.Build
import com.potpal.mirrortrack.ui.insights.InsightDependencyGraph

/**
 * One-screen-per-step grouping of permission asks. Each group bundles the
 * Android permissions/special-access requirements that share a coherent
 * user-facing concept (e.g. "Places you go") so we can explain one idea
 * at a time and show what insight cards it unlocks.
 *
 * The order in this enum is the onboarding flow order: light asks first,
 * heavier asks later.
 */
enum class PermissionGroup(
    val id: String,
    val title: String,
    val hook: String,
    val notSeen: String,
    val trackerComparison: String,
    val runtimePermissions: List<String>,
    val specialAccess: List<SpecialAccessKind> = emptyList(),
    val collectorIds: List<String>,
    val primaryUnlocks: List<String>,
    /**
     * True for the optional ADB / power-user step. ADB-tier permissions cannot
     * be granted via runtime dialogs or the system settings — they require
     * running pm grant / appops set commands from a computer. The onboarding
     * step renders the commands and explains the trade-offs; tapping the
     * primary CTA only marks the step decided, since the actual grant must
     * happen out-of-band.
     */
    val requiresAdb: Boolean = false,
    val adbCommands: String? = null
) {
    ACTIVITY(
        id = "activity",
        title = "Activity & Sleep",
        hook = "Pick up when you're walking, running, still — plus heart rate if your phone has the sensor.",
        notSeen = "Where you are. What apps you use. What you say.",
        trackerComparison = "Fitness, ad-tech, and health SDKs use these to build longitudinal physical-activity profiles, infer sleep schedules, label users as \"active\" or \"sedentary,\" and cross-reference with location to guess gym memberships, commute mode, even pregnancy. MirrorTrack stores hourly activity counts and step totals locally — no profiling, no cross-app linkage, no upload.",
        runtimePermissions = listOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        ),
        collectorIds = listOf("activity_recognition", "steps", "body_sensors"),
        primaryUnlocks = listOf(
            "Activity Profile",
            "Heart Rate",
            "Steps in Today's summary",
            "Stronger Sleep Timeline"
        )
    ),
    LOCATION(
        id = "location",
        title = "Places You Go",
        hook = "Where you live, where you work, the routes between, and how predictable your day looks from above.",
        notSeen = "Which apps used your location. What you do at those places.",
        trackerComparison = "Location is the most-resold data category in the industry. Ad SDKs, weather apps, map apps, and \"free\" games sample location every few minutes, sell it to data brokers, and join it with billions of points to identify your home, employer, religious affiliation, medical visits, and political activity. MirrorTrack keeps every fix on this device and rounds them into clusters you can rename.",
        runtimePermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ),
        collectorIds = listOf("location"),
        primaryUnlocks = listOf(
            "Home & Work",
            "Commute Pattern",
            "Location Clusters",
            "Dwell Times",
            "Travel Profile"
        )
    ),
    AUDIO(
        id = "audio",
        title = "What's Around You",
        hook = "Ambient loudness bands and short transcript-derived voice context.",
        notSeen = "No raw audio is ever saved. Only loudness numbers and short tag windows.",
        trackerComparison = "Some ad SDKs (notably the discontinued Alphonso \"automatic content recognition\") sampled the mic to fingerprint nearby TV audio. Voice assistants upload waveforms for cloud transcription. MirrorTrack's mic windows run for seconds, not minutes — Vosk transcribes locally, the audio is dropped before anything reaches storage, and only loudness numbers and word-tags are kept.",
        runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO),
        collectorIds = listOf(
            "ambient_sound",
            "speech_gated_transcription",
            "voice_transcription"
        ),
        primaryUnlocks = listOf(
            "Voice Context",
            "Stronger Sleep Timeline"
        )
    ),
    PHONE_USE(
        id = "phone_use",
        title = "Phone Use",
        hook = "Where your attention actually goes — which apps hold you, which notifications pull you back.",
        notSeen = "Notification body text past the title and app name.",
        trackerComparison = "Mobile attribution and engagement SDKs (Adjust, AppsFlyer, Branch, etc.) hook usage and notification signals to score \"engagement\" and feed retargeting bids — often joined with a stable advertising ID across hundreds of apps. MirrorTrack records the same per-app foreground time and notification rates, but only on this phone and only for you.",
        runtimePermissions = emptyList(),
        specialAccess = listOf(
            SpecialAccessKind.NOTIFICATION_LISTENER,
            SpecialAccessKind.USAGE_ACCESS
        ),
        collectorIds = listOf("notification_listener", "usage_stats"),
        primaryUnlocks = listOf(
            "App Attention",
            "Notification Heatmap",
            "Social Pressure",
            "Unlock After Notification",
            "Privacy Radar"
        )
    ),
    PEOPLE_AND_SCHEDULE(
        id = "people_schedule",
        title = "People & Schedule",
        hook = "Estimate the shape of your social graph, calendar density, photo activity, and communication style.",
        notSeen = "Photo pixels — only EXIF metadata. Message contents beyond sender/length aggregates.",
        trackerComparison = "\"Social graph extraction\" is what apps like LinkedIn, Truecaller, and many messengers ship contacts to a server for. EXIF photo metadata reveals camera, location, and time-of-day; ad networks have used it to deanonymize users. MirrorTrack hashes contact identifiers by default, never uploads media, and only counts events — not contents.",
        runtimePermissions = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                @Suppress("DEPRECATION")
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        },
        collectorIds = listOf("contacts", "calendar", "sms", "call_log", "media_exif"),
        primaryUnlocks = listOf(
            "Social Graph",
            "Calendar Density",
            "Photo Activity",
            "Communication Depth",
            "Spending Pulse"
        )
    ),
    NETWORK_SURROUNDINGS(
        id = "network_surroundings",
        title = "Network Surroundings",
        hook = "Wi-Fi and Bluetooth around you — together they act like an indoor location trail.",
        notSeen = "Network traffic contents. Only network names, signal strength, and pairing metadata.",
        trackerComparison = "Wi-Fi BSSIDs and BLE beacons are the backbone of indoor advertising and retail analytics. Companies like Skyhope, Cuebiq, and many SDK vendors join Wi-Fi scans against geolocation databases to track users without GPS. MirrorTrack only logs your nearby BSSIDs/MACs locally so you can see what your phone could expose.",
        runtimePermissions = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        },
        collectorIds = listOf("wifi", "bluetooth"),
        primaryUnlocks = listOf(
            "WiFi Footprint",
            "Bluetooth Ecosystem"
        )
    ),
    ADVANCED_ADB(
        id = "advanced_adb",
        title = "Power-User Mode (optional)",
        hook = "Developer-tier permissions you grant from a computer. Unlocks visibility into how other apps on your phone actually behave — the same surface that mobile-management agents and forensic tools use.",
        notSeen = "Still no audio, no message bodies, no remote access. These commands only grant on-device read access; nothing is uploaded.",
        trackerComparison = "READ_LOGS, GET_APP_OPS_STATS and DUMP are what corporate Mobile Device Management (MDM) agents, rooted spyware, and forensic suites use to inspect device behaviour. MirrorTrack uses them only locally — pointed at your phone, by you, with the same depth of access those tools have.",
        runtimePermissions = emptyList(),
        specialAccess = emptyList(),
        collectorIds = listOf("appops_audit", "logcat", "network_usage", "system_stats"),
        primaryUnlocks = listOf(
            "Privacy Radar (real per-app camera/mic/location accesses)",
            "Data Flow (per-app network bytes)",
            "App Compulsion Index",
            "Device Health (real RAM, processes, thermal)"
        ),
        requiresAdb = true,
        adbCommands = """PKG=com.potpal.mirrortrack.debug

adb shell pm grant ${'$'}PKG android.permission.READ_LOGS
adb shell pm grant ${'$'}PKG android.permission.GET_APP_OPS_STATS
adb shell pm grant ${'$'}PKG android.permission.DUMP
adb shell appops set ${'$'}PKG android:get_usage_stats allow

# Optional: keep MirrorTrack alive in the background reliably
adb shell appops set ${'$'}PKG RUN_IN_BACKGROUND allow
adb shell appops set ${'$'}PKG RUN_ANY_IN_BACKGROUND allow
adb shell dumpsys deviceidle whitelist +${'$'}PKG"""
    );

    /**
     * Cards from InsightDependencyGraph that are touched by this group's
     * collectors (primary or fallback). Used as a fallback display when the
     * curated [primaryUnlocks] list is empty, and to compute "locked card
     * counts" elsewhere.
     */
    fun derivedCardNames(): List<String> {
        val ids = collectorIds.toSet()
        return InsightDependencyGraph.cards
            .filter { card ->
                card.primary.any { it in ids } || card.fallbacks.any { it in ids }
            }
            .map { it.cardName }
    }
}

/**
 * Special-access permissions are not granted via the runtime permission
 * dialog — the user must enable them in system settings. We mirror the
 * existing [com.potpal.mirrortrack.ui.permissions.SpecialAccessType] enum
 * here to avoid a circular import; the Onboarding screen converts to the
 * permissions-screen enum when launching the system intent.
 */
enum class SpecialAccessKind {
    NOTIFICATION_LISTENER,
    USAGE_ACCESS
}
