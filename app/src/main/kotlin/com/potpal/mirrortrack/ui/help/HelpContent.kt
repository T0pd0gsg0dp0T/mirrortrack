package com.potpal.mirrortrack.ui.help

data class HelpSection(
    val title: String,
    val intro: String,
    val cards: List<HelpCard>
)

data class HelpCard(
    val title: String,
    val summary: String,
    val calculation: String,
    val dataUsed: String,
    val permissions: String,
    val notes: String
)

object HelpContent {
    val sections: List<HelpSection> = listOf(
        HelpSection(
            title = "How To Read MirrorTrack",
            intro = "Start here. These panels explain the parts of the Insights screen that affect every card: counts, confidence, diagnostics, and unavailable cards.",
            cards = listOf(
                HelpCard(
                    title = "Collection Header",
                    summary = "Shows how many encrypted data points MirrorTrack has stored and how those points are distributed across major categories.",
                    calculation = "The total is a direct database row count. Category totals come from grouping saved data points by collector category.",
                    dataUsed = "All stored data points in the local SQLCipher database.",
                    permissions = "No additional permission beyond whatever each enabled collector already uses.",
                    notes = "A rising count means collectors are writing. If it stays flat for long periods, collection or scheduling may be stalled."
                ),
                HelpCard(
                    title = "Diagnostics Toggle",
                    summary = "The speedometer icon reveals the plumbing behind each card.",
                    calculation = "When enabled, each card shows its source path, how many supporting points were used, how fresh the newest point is, and whether a fallback source was used.",
                    dataUsed = "Per-card InsightMeta generated in InsightsViewModel during refresh.",
                    permissions = "None.",
                    notes = "Use this when a card looks wrong. It will usually tell you whether the issue is stale data, weak evidence, or a fallback collector."
                ),
                HelpCard(
                    title = "Confidence Badges",
                    summary = "LOW, MED, and STALE are quality warnings rather than moral judgments.",
                    calculation = "LOW and MED come from the card metadata confidence tier. STALE appears when the newest supporting data is older than the card's freshness threshold.",
                    dataUsed = "InsightMeta confidence and newestDataMs for each card.",
                    permissions = "None.",
                    notes = "No badge means the card has high confidence. STALE usually means the collector is off, blocked, or simply has not run recently."
                ),
                HelpCard(
                    title = "Unavailable Insights",
                    summary = "Cards move here when there is not enough evidence to compute them yet.",
                    calculation = "Each unavailable row is shown when the underlying computed value is null or empty.",
                    dataUsed = "The same computed insight state that powers the main cards.",
                    permissions = "Varies by card.",
                    notes = "This is a useful troubleshooting list because it tells you exactly which behaviors or permissions are still missing."
                )
            )
        ),
        HelpSection(
            title = "Daily Behavior",
            intro = "These cards answer the first practical question: how much the phone is being used, when, and with what apparent intensity.",
            cards = listOf(
                HelpCard(
                    title = "Engagement Score",
                    summary = "Estimates how strong and consistent the device-use habit loop looks over the last 7 days.",
                    calculation = "Builds sessions from screen_on to screen_off pairs, or falls back to app lifecycle durations. It reports DAU/WAU ratio, average sessions per day, average session duration, total 7-day sessions, active days, and whether there was activity 7 and 30 days ago.",
                    dataUsed = "Primary: screen_state events. Fallbacks: app_lifecycle foreground/background and duration_ms.",
                    permissions = "None for primary/fallback collectors shown here.",
                    notes = "A high score usually means frequent, repeated, consistent phone interaction. That can reveal habit strength, dependency patterns, work rhythm, or when someone is reliably reachable."
                ),
                HelpCard(
                    title = "Today",
                    summary = "A compact daily rollup of how active the device has been since local midnight.",
                    calculation = "Counts all rows since start of day, estimates unlocks from screen_on events, derives screen time from screen_on/screen_off pairs, sums step deltas, tracks battery change, and counts how many collectors wrote today.",
                    dataUsed = "Primary: screen_state, steps, battery. Fallbacks: app_lifecycle, usage_stats, motion_sensor.",
                    permissions = "ACTIVITY_RECOGNITION for step data if the steps collector is enabled. Usage Access if the usage_stats fallback is needed.",
                    notes = "This is the fastest card for spotting whether the app is collecting live data as expected."
                ),
                HelpCard(
                    title = "Sleep",
                    summary = "Shows the last 72 hours of likely sleep windows and a 90-day nightly history.",
                    calculation = "Finds long screen-off gaps over 2 hours. For the 72-hour view it scores each gap using darkness, quietness, speech absence, and duration. Confidence starts at 0.45, rises with dark and quiet windows, and is filtered out below 0.4. The 90-day history picks the longest overnight gap between 8 PM and noon.",
                    dataUsed = "Primary: screen_state, environment_sensor ambient_light_lux, ambient_sound ambient_sound_dbfs. Fallbacks: app_lifecycle, voice_transcription conversation and speech density.",
                    permissions = "RECORD_AUDIO for ambient sound or voice windows. No runtime permission for ambient light. None for screen_state/app_lifecycle.",
                    notes = "This is a sleep inference, not a medical sleep stage detector. It estimates when the user was plausibly asleep, not guaranteed sleep."
                ),
                HelpCard(
                    title = "Anomaly Feed",
                    summary = "Flags days that break the recent baseline.",
                    calculation = "Looks for four main patterns: unlock spikes versus recent standard deviation, late-night unlock bursts, unusual battery drain rate, and location grid cells not seen earlier in the week.",
                    dataUsed = "Primary: screen_state, battery, location. Fallback: app_lifecycle for unlock-like events.",
                    permissions = "ACCESS_FINE_LOCATION for location-based anomalies. None for screen_state/app_lifecycle/battery.",
                    notes = "Anomalies matter because sudden changes often reveal travel, stress, illness, schedule disruption, outages, or a new app workflow."
                )
            )
        ),
        HelpSection(
            title = "Routine, Place, And Movement",
            intro = "These cards reconstruct where the phone tends to be, when repeated patterns happen, and how predictable those patterns look.",
            cards = listOf(
                HelpCard(
                    title = "Home & Work",
                    summary = "Infers two high-value anchor places from repeated location fixes.",
                    calculation = "Pairs latitude and longitude points, snaps them to a roughly 100 m grid, labels the most visited night cluster as Home and the strongest weekday daytime cluster as Work, then computes the straight-line distance between them.",
                    dataUsed = "Location collector latitude and longitude fixes.",
                    permissions = "ACCESS_FINE_LOCATION.",
                    notes = "This is one of the highest-risk inferences in the app because home and work together can identify a person very quickly."
                ),
                HelpCard(
                    title = "Commute Pattern",
                    summary = "Estimates whether there is a repeat commute between inferred home and work.",
                    calculation = "After Home and Work are known, the code scans 14 days of location transitions between those two grids, computes average departure time, return time, trip duration, estimated speed, and a consistency score from departure-time variance.",
                    dataUsed = "Home & Work inference plus location fixes.",
                    permissions = "ACCESS_FINE_LOCATION.",
                    notes = "Transport mode is a heuristic: under 6 km/h is walking, under 25 km/h is transit, otherwise driving."
                ),
                HelpCard(
                    title = "Location Clusters",
                    summary = "Groups repeated coordinates into recurring places.",
                    calculation = "Pairs nearby lat/lon samples by timestamp, rounds them to a three-decimal grid, counts visits per cell, and keeps the top clusters.",
                    dataUsed = "Location collector latitude and longitude fixes.",
                    permissions = "ACCESS_FINE_LOCATION.",
                    notes = "The cluster list is the raw material that later supports Home, Work, Commute, and Dwell Time."
                ),
                HelpCard(
                    title = "Dwell Times",
                    summary = "Shows how long the phone tends to remain in each recurring place.",
                    calculation = "Sorts location fixes by time, turns consecutive fixes in the same grid into visits, sums total dwell, averages visit duration, and classifies each place as home, work, transit, retail, or social using visit timing and duration.",
                    dataUsed = "Location collector latitude and longitude fixes and any user-assigned cluster names.",
                    permissions = "ACCESS_FINE_LOCATION.",
                    notes = "Short visits tend to look like transit or errands. Long recurring visits during the day or night usually imply stronger place identity."
                ),
                HelpCard(
                    title = "Circadian Rhythm",
                    summary = "Builds a 24-hour activity pattern and assigns a rough chronotype.",
                    calculation = "Counts activity by hour across the last 30 days, finds peak and trough hours, calculates the daily active-hour spread, and labels the pattern as early_bird, night_owl, shift_worker, bimodal, or balanced.",
                    dataUsed = "Primary: screen_state screen_on. Fallbacks: app_lifecycle foreground, logcat focus events, voice_transcription conversation windows.",
                    permissions = "READ_LOGS for the logcat fallback. RECORD_AUDIO for the voice fallback.",
                    notes = "This is useful because wake/sleep timing alone can reveal work style, caregiving, travel disruption, or overnight job patterns."
                ),
                HelpCard(
                    title = "Routine Predictability",
                    summary = "Measures how repeatable the daily schedule looks.",
                    calculation = "Builds a per-day 24-hour histogram, computes per-hour coefficient of variation across days, and turns that into an overall predictability score. It also measures how different weekday and weekend patterns are using cosine distance.",
                    dataUsed = "Primary: screen_state. Fallbacks: app_lifecycle, logcat focus, voice_transcription conversation windows.",
                    permissions = "READ_LOGS for logcat fallback. RECORD_AUDIO for voice fallback.",
                    notes = "A high routine score means the phone is following a repeated schedule. A low score means the daily timing is more irregular."
                ),
                HelpCard(
                    title = "Weekday vs Weekend",
                    summary = "Compares workweek behavior to weekend behavior.",
                    calculation = "Computes average weekday and weekend unlock volume, average screen time per day, top apps for each period, then combines unlock difference, screen-time difference, and app overlap into a balance score.",
                    dataUsed = "Primary: screen_state and usage_stats. Fallback: app_lifecycle synthesized screen events.",
                    permissions = "Usage Access for top-app comparison. None for screen_state/app_lifecycle.",
                    notes = "Large weekday/weekend separation usually signals a structured job or school schedule."
                ),
                HelpCard(
                    title = "Monthly Trends",
                    summary = "Shows whether overall usage is growing or shrinking over calendar months.",
                    calculation = "Groups unlocks, screen time, steps, and stored row counts by month for up to six months, then reports per-month averages and totals.",
                    dataUsed = "Primary: screen_state and steps. Fallback: app_lifecycle synthesized screen events.",
                    permissions = "ACTIVITY_RECOGNITION for step totals.",
                    notes = "This card stays unavailable until the database spans at least two different calendar months."
                )
            )
        ),
        HelpSection(
            title = "Interruption, Attention, And Privacy",
            intro = "These cards show how apps pull attention, whether notifications work, and where sensitive access happens.",
            cards = listOf(
                HelpCard(
                    title = "Social Pressure",
                    summary = "Measures how strongly notifications from each app pull the user back to the phone.",
                    calculation = "For each notification, the code finds the next unlock within 10 minutes. It computes response rate, median response delay, and a pressure score based on notification count, response rate, and response speed.",
                    dataUsed = "Primary: notification_listener and screen_state. Fallback: app_lifecycle foreground events as unlock proxies.",
                    permissions = "Special access: Notification Listener. None for screen_state/app_lifecycle.",
                    notes = "Fast, frequent responses usually indicate a socially or behaviorally sticky app."
                ),
                HelpCard(
                    title = "Unlock After Notification",
                    summary = "Shows which apps are followed by the fastest unlocks.",
                    calculation = "Matches each notification to the next unlock within 10 minutes, groups by app, and reports the median latency and sample count.",
                    dataUsed = "Primary: notification_listener plus screen_state screen_on. Fallback: app_lifecycle foreground events.",
                    permissions = "Special access: Notification Listener.",
                    notes = "This is simpler than Social Pressure. It focuses on delay and sample size rather than combining them into one pressure metric."
                ),
                HelpCard(
                    title = "Privacy Radar",
                    summary = "Ranks apps by sensitive-surface usage such as camera, microphone, location, and contacts.",
                    calculation = "Groups recent AppOps or Privacy Dashboard events by package, counts whether the app accessed camera, record_audio, fine/coarse location, or contacts, then computes a weighted privacy score capped at 100.",
                    dataUsed = "Primary: appops_audit. Fallback: privacy_dashboard on supported devices.",
                    permissions = "ADB: GET_APP_OPS_STATS for the primary collector. Privacy Dashboard fallback relies on Android system APIs rather than a user permission prompt.",
                    notes = "This is not saying the access was malicious. It is showing how much sensitive capability the app exercised."
                ),
                HelpCard(
                    title = "App Compulsion Index",
                    summary = "Looks for repeated app opening patterns that resemble checking loops.",
                    calculation = "Primary path sums logcat focus events by package, keeps apps with at least 3 launches, and calculates the average gap between launches. Fallback uses usage_stats launch_count and estimates average gap across a 7-day window.",
                    dataUsed = "Primary: logcat focus entries. Fallback: usage_stats package_usage launch_count.",
                    permissions = "ADB: READ_LOGS for the primary path. Usage Access for the fallback.",
                    notes = "High launch count combined with short gaps is the clearest signal of compulsive checking."
                ),
                HelpCard(
                    title = "Session Fragmentation",
                    summary = "Estimates how chopped up attention is during a typical phone session.",
                    calculation = "Builds sessions from screen on/off pairs, counts app_launches within each session, computes average switches per session, average time between switches, the most and least fragmented hours, and an attention score that falls as switching per minute rises.",
                    dataUsed = "Primary: screen_state and logcat app_launches. Fallback: app_lifecycle synthesized sessions.",
                    permissions = "ADB: READ_LOGS for app switching detail.",
                    notes = "This card is one of the clearest illustrations of how raw app-switching data can describe focus quality."
                )
            )
        ),
        HelpSection(
            title = "Context, Device State, And Personal Profile",
            intro = "These cards show what can be inferred from voice, charging, network environment, installed apps, and device characteristics.",
            cards = listOf(
                HelpCard(
                    title = "Voice Context",
                    summary = "Summarizes speech windows captured locally on-device.",
                    calculation = "Counts transcription windows, conversation-present windows, average speech density in words per minute, top inferred contexts, most frequent keyword tags, and the latest transcript snippet. If no windows exist yet, it reports installer/setup state for the bundled local voice model.",
                    dataUsed = "Voice transcription collector outputs such as window_duration_ms, conversation_present, speech_density_wpm, inferred_context, context_tags, and transcript_text.",
                    permissions = "RECORD_AUDIO.",
                    notes = "This processing is designed around local transcription. It still carries obvious privacy risk because even short context windows can reveal work, family, travel, or health-related topics."
                ),
                HelpCard(
                    title = "Device Health",
                    summary = "Explains the phone’s memory pressure, thermal status, uptime, and process load.",
                    calculation = "Primary path reads the latest system_stats values for RAM use, process counts, thermal status, uptime, and memory trend. Fallback reads a live platform snapshot from ActivityManager and PowerManager. Final fallback estimates only thermal state and uptime from battery data.",
                    dataUsed = "Primary: system_stats. Fallbacks: live Android APIs and battery collector.",
                    permissions = "No runtime permission required for the current implementation. Optional ADB DUMP can improve process visibility for the collector.",
                    notes = "This card helps distinguish user behavior problems from device-performance problems."
                ),
                HelpCard(
                    title = "Charging Behavior",
                    summary = "Looks for charging habits that reveal routine and battery dependence.",
                    calculation = "Detects charging cycles from battery is_charging transitions, or from battery level rising if charging-state data is sparse. It computes charges per day, average battery level at charge start, average charge duration, whether overnight charging is common, and the most common charge-start hour.",
                    dataUsed = "Battery level and is_charging data from the battery collector.",
                    permissions = "None.",
                    notes = "Overnight charging and low-start charging can reveal both schedule and device dependency."
                ),
                HelpCard(
                    title = "Income Inference",
                    summary = "Demonstrates how crude socioeconomic guesses can be made from device and app clues.",
                    calculation = "Classifies the phone into budget, mid_range, flagship, or ultra using brand/model heuristics; classifies the carrier as postpaid, prepaid, or MVNO; then adds app-category signals such as trading apps or multiple subscriptions to produce an overall tier.",
                    dataUsed = "Primary: build_info, carrier, and app portfolio. Fallbacks: connectivity for carrier names, usage_stats/logcat through the app portfolio fallback chain.",
                    permissions = "Restricted QUERY_ALL_PACKAGES if installed_apps is enabled. Usage Access or READ_LOGS may indirectly feed the fallback app portfolio path.",
                    notes = "This is intentionally rough. The point is to show how easily weak signals can still be turned into profiling."
                ),
                HelpCard(
                    title = "WiFi Footprint",
                    summary = "Treats network names as a location trail.",
                    calculation = "Counts unique SSIDs over the last 7 days, ranks the most common networks, computes a mobility score from SSID entropy, and infers the likely home network from nighttime Wi-Fi associations.",
                    dataUsed = "Primary: wifi current_ssid and scan_results. Fallback: connectivity active transport or ssid values.",
                    permissions = "ACCESS_FINE_LOCATION is generally required for Wi-Fi SSID visibility on modern Android.",
                    notes = "Even without GPS, Wi-Fi history is often enough to reveal home, workplace, and travel patterns."
                ),
                HelpCard(
                    title = "App Portfolio",
                    summary = "Uses the installed app set to infer interests, habits, and likely life patterns.",
                    calculation = "Builds the latest package inventory, classifies packages into lifestyle buckets using package-name pattern matching, counts system versus user apps, and emits simple inferences such as privacy_conscious, knowledge_worker, or likely_parent.",
                    dataUsed = "Primary: installed_apps. Fallbacks: usage_stats package names and logcat focus entries.",
                    permissions = "Restricted QUERY_ALL_PACKAGES for the primary collector. Usage Access or READ_LOGS for fallbacks.",
                    notes = "Installed apps are one of the densest profile sources in the app because they reveal long-term interests rather than just momentary behavior."
                )
            )
        ),
        HelpSection(
            title = "Expanded Personal Signals",
            intro = "These cards show how broader opt-in signals become profile features: travel, relationships, movement, health-adjacent sensors, schedule density, photos, device trust, spending, and communication style.",
            cards = listOf(
                HelpCard(
                    title = "Travel Profile",
                    summary = "Estimates whether the device is mostly local or showing travel-like movement.",
                    calculation = "Combines carrier country/roaming flags, public-IP changes, location clusters more than 50 km from inferred home, median radius of far clusters, and distinct EXIF GPS photo spots into a 0-1 travel score.",
                    dataUsed = "Primary: carrier and location. Fallback/context: public_ip and media_exif.",
                    permissions = "ACCESS_FINE_LOCATION for location; media permissions for EXIF photo metadata. Carrier and public-IP collectors do not require runtime permissions, but public-IP is the only allowed INTERNET use.",
                    notes = "Travel is a high-value ad, fraud, timing, and risk signal. This card intentionally keeps the inference rough."
                ),
                HelpCard(
                    title = "Social Graph",
                    summary = "Estimates the breadth of the device owner's relationship surface without storing raw names or numbers.",
                    calculation = "Counts contacts, distinct notification senders, unique call counterparts, unique SMS senders, and a calendar attendee proxy, then labels the total as isolated, small, moderate, or broad.",
                    dataUsed = "contacts, notification_listener, calendar, call_log, and sms.",
                    permissions = "READ_CONTACTS, READ_CALENDAR, READ_CALL_LOG, READ_SMS, and Notification Listener special access depending on enabled sources.",
                    notes = "Identifiers are hashed or aggregated by the collectors. The point is to show that metadata alone still exposes social shape."
                ),
                HelpCard(
                    title = "Activity Profile",
                    summary = "Shows coarse physical movement buckets over the last 30 days.",
                    calculation = "Groups activity_recognition samples into still, walking, running, vehicle, and bicycle percentages, averages confidence, and combines movement categories into a 0-1 movement index.",
                    dataUsed = "activity_recognition activity and confidence_estimate points.",
                    permissions = "ACTIVITY_RECOGNITION.",
                    notes = "This is a lightweight heuristic, not a medical or fitness-grade classifier."
                ),
                HelpCard(
                    title = "Heart Rate",
                    summary = "Summarizes heart-rate sensor patterns when BODY_SENSORS data exists.",
                    calculation = "Uses the 10th percentile as a resting estimate, median and max BPM for central tendency and peak exertion, percent of samples above 120 BPM, and a crude post-peak recovery window.",
                    dataUsed = "body_sensors heart_rate_bpm points.",
                    permissions = "BODY_SENSORS.",
                    notes = "This card is explicitly not medical advice. It exists to show how health-adjacent sensor data becomes a profile feature."
                ),
                HelpCard(
                    title = "Bluetooth Ecosystem",
                    summary = "Shows the paired and nearby Bluetooth device environment.",
                    calculation = "Parses paired_devices and BLE scan_results, counts paired devices, nearby unique addresses, average scan size, and infers rough brand categories from device names.",
                    dataUsed = "bluetooth paired_devices and scan_results JSON.",
                    permissions = "BLUETOOTH_SCAN and BLUETOOTH_CONNECT on Android 12+, or ACCESS_FINE_LOCATION on older Android versions.",
                    notes = "No external MAC lookup is performed. Brand inference is local string matching only."
                ),
                HelpCard(
                    title = "Calendar Density",
                    summary = "Measures how crowded and fragmented the schedule looks.",
                    calculation = "Deduplicates calendar events, counts events in the past 30 days and current week, estimates average workday load, recurring title hashes, median duration, and back-to-back event share.",
                    dataUsed = "calendar event JSON, including start_ms, end_ms, title hash, and organizer presence.",
                    permissions = "READ_CALENDAR.",
                    notes = "Calendar density predicts unavailable attention before the behavior happens."
                ),
                HelpCard(
                    title = "Photo Activity",
                    summary = "Summarizes local photo metadata without inspecting image pixels.",
                    calculation = "Counts photos over 30 and 7 days, photos with and without GPS, distinct rounded EXIF GPS locations, distinct camera make/model values, and the most common capture hour.",
                    dataUsed = "media_exif image JSON.",
                    permissions = "READ_MEDIA_IMAGES on Android 13+, READ_MEDIA_VISUAL_USER_SELECTED on Android 14 partial access, or READ_EXTERNAL_STORAGE on older Android.",
                    notes = "EXIF alone can reveal travel, routine, hobbies, and social activity."
                ),
                HelpCard(
                    title = "Notification Heatmap",
                    summary = "Builds a 7-by-24 interruption map from notifications.",
                    calculation = "Counts notification_listener events by day of week and hour, computes total notifications, average per hour, late-night share, work-hours share, top interrupters, and a calm/moderate/noisy/saturated label.",
                    dataUsed = "notification_listener notif_posted points.",
                    permissions = "Notification Listener special access.",
                    notes = "The heatmap shows when apps can reach the user, which is exactly what attention systems optimize."
                ),
                HelpCard(
                    title = "Integrity Trust",
                    summary = "Summarizes device trust and tamper indicators.",
                    calculation = "Reads root, su binary, debugger, ADB, developer options, test keys, emulator heuristics, and Play Integrity availability, then subtracts weighted penalties from a 100-point trust score.",
                    dataUsed = "integrity collector fields.",
                    permissions = "None.",
                    notes = "This mirrors the kind of hidden trust checks used by banks, streaming services, fraud systems, and some ad SDKs."
                ),
                HelpCard(
                    title = "Spending Pulse",
                    summary = "Shows finance-adjacent activity from SMS and notification metadata.",
                    calculation = "Counts transaction-like SMS, bank alerts, OTPs, finance-app notifications, a this-week transaction proxy, top payment apps, and an optional cadence estimate from notification peaks.",
                    dataUsed = "sms aggregate counts and notification_listener package names; logcat is listed only as a future fallback signal.",
                    permissions = "READ_SMS and Notification Listener special access.",
                    notes = "Raw message bodies are not persisted. The collector stores only aggregate counts and hashed sender-derived metadata."
                ),
                HelpCard(
                    title = "Communication Depth",
                    summary = "Estimates communication style from call and SMS aggregates.",
                    calculation = "Combines inbound/outbound/missed call counts, total call duration, unique counterparts, top-five counterpart share, inbox/sent SMS counts, and merged hourly histograms into voice-first, text-first, balanced, or quiet labels.",
                    dataUsed = "call_log and sms aggregate points, with contacts as context/fallback for graph breadth.",
                    permissions = "READ_CALL_LOG and READ_SMS.",
                    notes = "This card demonstrates how relationship intensity can be inferred from metadata without reading communication content."
                )
            )
        ),
        HelpSection(
            title = "Dense Technical Profile",
            intro = "These are lower in the Insights order because they are more technical, but they show how surveillance systems score identity, attention, and data movement.",
            cards = listOf(
                HelpCard(
                    title = "App Attention (7d)",
                    summary = "Ranks the apps that actually held foreground time over the last week.",
                    calculation = "Primary path reads total_foreground_ms from usage_stats package payloads. Fallback uses logcat focus counts and estimates roughly two minutes of foreground time per launch. It also compares current attention to the previous 7-day baseline.",
                    dataUsed = "Primary: usage_stats package_usage JSON. Fallback: logcat focus entries.",
                    permissions = "Special access: Usage Access for primary. ADB: READ_LOGS for fallback.",
                    notes = "This is more durable than raw launch count because it reflects sustained attention, not just quick checks."
                ),
                HelpCard(
                    title = "Data Flow",
                    summary = "Shows which apps moved the most data and flags heavy upload patterns.",
                    calculation = "Reads the latest per-app network_usage snapshot, extracts Wi-Fi and mobile receive/transmit totals, computes total bytes and the transmit/receive ratio, and marks an app suspicious if it sends more than 3x what it receives and uploaded more than 1 MB.",
                    dataUsed = "Network usage collector net_usage package JSON.",
                    permissions = "ADB-level PACKAGE_USAGE_STATS path used by the network usage collector in this app.",
                    notes = "This is meant to illustrate data exfiltration style patterns, not to prove malicious behavior."
                ),
                HelpCard(
                    title = "Fingerprint Stability",
                    summary = "Shows which device identity fields stay fixed and which ones change.",
                    calculation = "For each build, hardware, and identifier key, the code keeps the current value and searches backward to find the last time the value changed.",
                    dataUsed = "build_info, hardware, and identifiers collectors.",
                    permissions = "None.",
                    notes = "Stable values are what make device fingerprinting effective. Rare changes are what let a tracker detect resets, travel, or upgrades."
                ),
                HelpCard(
                    title = "Identity Entropy",
                    summary = "Estimates how uniquely identifiable the phone is from many small technical traits.",
                    calculation = "Each field is assigned an approximate cardinality from fingerprinting research or a heuristic estimate, converted to bits of entropy, then summed into a total uniqueness estimate. The highest-entropy fields are shown first.",
                    dataUsed = "Latest values from build_info, hardware, and identifiers.",
                    permissions = "None.",
                    notes = "This is a teaching card. The total is an estimate, but it shows how many harmless-looking fields combine into a strong identifier."
                )
            )
        )
    )
}
