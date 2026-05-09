package com.potpal.mirrortrack.ui.onboarding

/**
 * Plain-language explanations for things shown in onboarding bubbles.
 *
 * Three keyspaces, looked up via [descriptionFor]:
 *
 *   - Insight card name (e.g. "Home & Work")
 *   - Android permission constant (e.g. "android.permission.RECORD_AUDIO")
 *   - Collector id (e.g. "ambient_sound")
 */
object OnboardingContent {

    /** Returns (title, body) suitable for an info bubble, or null. */
    fun descriptionFor(key: String): Pair<String, String>? {
        cardDescriptions[key]?.let { return it.title to it.body }
        permissionDescriptions[key]?.let { return it.title to it.body }
        collectorDescriptions[key]?.let { return it.title to it.body }
        // Strip android.permission. prefix and try again.
        val short = key.substringAfterLast('.')
        permissionDescriptions[short]?.let { return it.title to it.body }
        return null
    }

    private data class Entry(val title: String, val body: String)

    private val cardDescriptions: Map<String, Entry> = mapOf(
        "Activity Profile" to Entry(
            "Activity Profile",
            "Hour-by-hour breakdown of still vs. walking vs. running vs. in-vehicle. Built from Android's activity-recognition signal — the same signal Google Fit and ride-share apps use."
        ),
        "Heart Rate" to Entry(
            "Heart Rate",
            "Heart-rate samples from a connected sensor or wearable (BODY_SENSORS). Stored locally; no cloud sync. Only appears if your device has a compatible sensor or paired watch."
        ),
        "Steps in Today's summary" to Entry(
            "Steps",
            "Daily step count drawn from the step-counter sensor. Combined with active hours and unlock count to populate the Today summary card."
        ),
        "Stronger Sleep Timeline" to Entry(
            "Sleep Timeline",
            "MirrorTrack infers sleep windows from inactivity, ambient light, and ambient sound. Granting microphone access lets the timeline factor in nighttime quiet — making sleep boundaries far more accurate than screen-off alone."
        ),
        "Home & Work" to Entry(
            "Home & Work",
            "Identifies your two most-visited clusters at night and during weekday daytime. Names default to \"Home\" and \"Work\" but you can rename them. Built only from on-device GPS history."
        ),
        "Commute Pattern" to Entry(
            "Commute Pattern",
            "Detects regular departures and arrivals between Home and Work, estimates typical departure time, average duration, and travel mode. No routes are uploaded; everything is computed from your local fix history."
        ),
        "Location Clusters" to Entry(
            "Location Clusters",
            "Groups nearby GPS fixes into recurring places. Each cluster shows how often you visit and how long you stay. You can rename clusters or hide them — clusters never sync off-device."
        ),
        "Dwell Times" to Entry(
            "Dwell Times",
            "How long you typically stay at each location cluster. Useful for spotting routine stops (gym, cafe) versus one-offs."
        ),
        "Travel Profile" to Entry(
            "Travel Profile",
            "Combines roaming flags, public-IP geolocation, GPS clusters far from home, and EXIF metadata from photos to estimate travel periods. Useful as a recap; nothing leaves the device."
        ),
        "Voice Context" to Entry(
            "Voice Context",
            "Categorizes short ambient-speech windows into context tags (work, home, social, media). Audio is transcribed locally with Vosk and discarded — only the derived tags and word counts are kept."
        ),
        "App Attention" to Entry(
            "App Attention",
            "Per-app foreground time over the last 7 days, plus a baseline delta showing whether each app is up or down vs. its longer-term average."
        ),
        "Notification Heatmap" to Entry(
            "Notification Heatmap",
            "Hour-by-day grid of notifications received. Reveals which apps and times of day generate the most interruption."
        ),
        "Social Pressure" to Entry(
            "Social Pressure",
            "Measures how often a notification is followed quickly by an unlock — a proxy for which apps reliably pull you back to the phone."
        ),
        "Unlock After Notification" to Entry(
            "Unlock After Notification",
            "Median latency between a notification arriving and you unlocking the phone, broken down by app. Lower numbers = stronger compulsive pull."
        ),
        "Privacy Radar" to Entry(
            "Privacy Radar",
            "Surfaces which other apps on your phone accessed sensitive surfaces (camera, mic, location, contacts) recently — built from Android's Privacy Dashboard data."
        ),
        "Social Graph" to Entry(
            "Social Graph",
            "Estimates the breadth and recency of your communication network from contacts, calendar invitees, call log, SMS metadata. Identifiers are hashed by default."
        ),
        "Calendar Density" to Entry(
            "Calendar Density",
            "Daily/weekly meeting density and longest unbroken focus blocks. Reads only event metadata — titles and bodies are not exported anywhere."
        ),
        "Photo Activity" to Entry(
            "Photo Activity",
            "EXIF-only: count and rough hour-of-day of photos taken. No image pixels are inspected, no thumbnails generated."
        ),
        "Communication Depth" to Entry(
            "Communication Depth",
            "Aggregates call duration and SMS exchange counts to estimate communication style — short bursts vs. long calls, balanced vs. one-sided."
        ),
        "Spending Pulse" to Entry(
            "Spending Pulse",
            "Watches notification metadata and SMS for bank-alert / OTP / transaction patterns. Only counts and senders — never message bodies."
        ),
        "WiFi Footprint" to Entry(
            "WiFi Footprint",
            "List of Wi-Fi networks seen and their frequency. Acts as a coarse location trail even without GPS, since BSSIDs are mostly stationary."
        ),
        "Bluetooth Ecosystem" to Entry(
            "Bluetooth Ecosystem",
            "Paired devices and nearby BLE scan summaries — earbuds, watches, beacons. Reveals which devices typically share your context."
        )
    )

    private val permissionDescriptions: Map<String, Entry> = mapOf(
        "ACTIVITY_RECOGNITION" to Entry(
            "ACTIVITY_RECOGNITION",
            "Lets the app read your phone's classification of your current movement (still / walking / running / vehicle). Granted by you with a runtime prompt."
        ),
        "BODY_SENSORS" to Entry(
            "BODY_SENSORS",
            "Required for heart-rate sensors. Some phones lack the sensor entirely; if granted but no sensor exists, the Heart Rate card stays empty."
        ),
        "ACCESS_FINE_LOCATION" to Entry(
            "ACCESS_FINE_LOCATION",
            "Precise GPS — sub-50m accuracy. Used for Home/Work clustering and commute detection. MirrorTrack uses Android's plain LocationManager (no Google Play services)."
        ),
        "ACCESS_COARSE_LOCATION" to Entry(
            "ACCESS_COARSE_LOCATION",
            "Cell-tower / Wi-Fi based location, accurate to a few hundred metres. Some Android versions require this alongside fine location."
        ),
        "RECORD_AUDIO" to Entry(
            "RECORD_AUDIO",
            "Lets MirrorTrack open the microphone for a few seconds at a time. The waveform never reaches storage — Vosk transcribes locally and the buffer is dropped before anything is saved."
        ),
        "READ_CONTACTS" to Entry(
            "READ_CONTACTS",
            "Reads contact names, phone numbers, and emails. Used to estimate social-graph breadth. By default, identifiers are hashed in the database (configurable in Settings)."
        ),
        "READ_CALENDAR" to Entry(
            "READ_CALENDAR",
            "Reads calendar event metadata: start, end, attendee count, calendar source. Titles and descriptions are not stored."
        ),
        "READ_SMS" to Entry(
            "READ_SMS",
            "Reads SMS metadata for the Spending Pulse and Communication Depth cards — sender, length, timestamp. Bodies are not stored."
        ),
        "READ_CALL_LOG" to Entry(
            "READ_CALL_LOG",
            "Reads call log metadata: number, direction, duration, timestamp. Used by Communication Depth and Social Graph."
        ),
        "READ_MEDIA_IMAGES" to Entry(
            "READ_MEDIA_IMAGES",
            "Allows EXIF metadata reads on photos in your gallery. Image pixels are never decoded — only headers."
        ),
        "READ_MEDIA_VIDEO" to Entry(
            "READ_MEDIA_VIDEO",
            "Allows reading video file metadata for the Photo Activity card. Video frames are never decoded."
        ),
        "READ_EXTERNAL_STORAGE" to Entry(
            "READ_EXTERNAL_STORAGE",
            "Pre-Android 13 fallback for reading photo EXIF metadata. Image pixels are not inspected."
        ),
        "BLUETOOTH_CONNECT" to Entry(
            "BLUETOOTH_CONNECT",
            "Lets the app see currently paired Bluetooth devices."
        ),
        "BLUETOOTH_SCAN" to Entry(
            "BLUETOOTH_SCAN",
            "Lets the app see nearby Bluetooth/BLE devices that are advertising. No connections are initiated."
        ),
        "NEARBY_WIFI_DEVICES" to Entry(
            "NEARBY_WIFI_DEVICES",
            "Android 13+ replacement for using location to access Wi-Fi scan results. Lets MirrorTrack list nearby SSIDs/BSSIDs without granting full location."
        ),
        "PACKAGE_USAGE_STATS" to Entry(
            "Usage Access",
            "Special access (system settings, not a runtime prompt). Lets MirrorTrack read foreground-app durations for App Attention. Reveals app sequence; never reads in-app content."
        ),
        "BIND_NOTIFICATION_LISTENER_SERVICE" to Entry(
            "Notification Listener",
            "Special access (system settings). Lets MirrorTrack see notifications as they arrive — app, title, time. Notification body text is not stored unless you opt in separately."
        ),
        "READ_LOGS" to Entry(
            "READ_LOGS (ADB)",
            "Lets MirrorTrack read system logcat. Used to detect app launches/restarts and rough memory pressure. Granted via 'adb shell pm grant'. The same permission MDM agents use to read device-wide behavioural signals."
        ),
        "GET_APP_OPS_STATS" to Entry(
            "GET_APP_OPS_STATS (ADB)",
            "Lets MirrorTrack read AppOps history — which apps used the camera, mic, location, contacts, and when. The 'Privacy Radar' card is built from this. Granted via 'adb shell pm grant'."
        ),
        "DUMP" to Entry(
            "DUMP (ADB)",
            "Lets MirrorTrack read 'dumpsys' output for system stats: RAM, processes, network usage, thermal. Granted via 'adb shell pm grant'. The same permission forensic tools use to snapshot device state."
        )
    )

    private val collectorDescriptions: Map<String, Entry> = mapOf(
        "activity_recognition" to Entry(
            "activity_recognition",
            "Reads Android's coarse activity classification (still / walking / running / vehicle) once a minute and stores the dominant label."
        ),
        "steps" to Entry(
            "steps",
            "Reads the step-counter sensor. Records cumulative steps and computes per-day totals locally."
        ),
        "body_sensors" to Entry(
            "body_sensors",
            "Subscribes to heart-rate samples from a paired wearable or onboard sensor. No data is uploaded."
        ),
        "location" to Entry(
            "location",
            "Polls GPS at a user-configurable interval (default 15 min) using Android's stock LocationManager. No Google Play Services."
        ),
        "ambient_sound" to Entry(
            "ambient_sound",
            "Opens the mic for ~1.5 seconds and stores only loudness numbers (RMS, dBFS, peak, label). No raw audio retained."
        ),
        "speech_gated_transcription" to Entry(
            "speech_gated_transcription",
            "Periodically samples ambient loudness; if sustained non-quiet speech is detected, fires a short Vosk transcription window. Audio is dropped after transcription."
        ),
        "voice_transcription" to Entry(
            "voice_transcription",
            "Periodic blind transcription windows (default every 15 minutes) using Vosk on-device. Useful when you'd rather have predictable sampling than speech-gating."
        ),
        "notification_listener" to Entry(
            "notification_listener",
            "Listens to incoming notifications via NotificationListenerService. Records app, title, and time."
        ),
        "usage_stats" to Entry(
            "usage_stats",
            "Reads daily app foreground durations from UsageStatsManager. Requires Usage Access in system settings."
        ),
        "contacts" to Entry(
            "contacts",
            "One-shot read of contact metadata. Identifiers are hashed before storage by default."
        ),
        "calendar" to Entry(
            "calendar",
            "Reads calendar event metadata only — start/end times, attendee count, calendar source."
        ),
        "sms" to Entry(
            "sms",
            "Reads SMS metadata (sender, length, timestamp). Bodies are not stored."
        ),
        "call_log" to Entry(
            "call_log",
            "Reads call log metadata (number, direction, duration, timestamp)."
        ),
        "media_exif" to Entry(
            "media_exif",
            "Reads only EXIF/metadata headers of photos and videos. Pixels are never decoded."
        ),
        "wifi" to Entry(
            "wifi",
            "Records nearby Wi-Fi networks (SSID, BSSID, signal strength) periodically."
        ),
        "bluetooth" to Entry(
            "bluetooth",
            "Records currently paired devices and a summary of nearby BLE advertisements."
        ),
        "appops_audit" to Entry(
            "appops_audit (ADB)",
            "Reads the AppOps history Android keeps for every app's privacy-sensitive operations. Powers Privacy Radar."
        ),
        "logcat" to Entry(
            "logcat (ADB)",
            "Tails the device logcat to detect app starts/stops and other behavioural signals. Powers App Compulsion and parts of Session Fragmentation."
        ),
        "network_usage" to Entry(
            "network_usage (ADB)",
            "Reads per-app network bytes from the system's NetworkStatsManager. Powers Data Flow."
        ),
        "system_stats" to Entry(
            "system_stats (ADB)",
            "Calls dumpsys for RAM, process counts, thermal status. Powers the trustworthy version of the Device Health card."
        )
    )

    // ── Tracker context: how industry actors use these ADB perms ──

    val adbPermissionsContext: String =
        "These permissions are intentionally fenced off behind ADB because they expose device-wide " +
            "behavioural signals: which apps run, what other apps see, network bytes, system load. " +
            "On a stock phone they are typically used by your employer's MDM agent, by forensics tools, " +
            "or by rooted spyware. MirrorTrack uses them locally only — pointed at your phone, by you."

    val adbInstructions: String =
        "On a computer with Android Studio platform-tools installed, enable USB debugging on the " +
            "phone (Settings → About phone → tap Build number seven times, then Settings → Developer " +
            "options → USB debugging) and plug it in. Then run the commands below. Grants survive reboot."

    val adbConsequences: String =
        "Granting these permissions is reversible. To revoke them, run 'adb shell pm revoke <pkg> " +
            "<permission>' or uninstall the app. They cannot be used by other apps on your phone — " +
            "only by MirrorTrack."
}
