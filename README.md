# MirrorTrack

**See your own data the way trackers do.**

MirrorTrack turns your Android phone into a private intelligence lab for your own life. It captures the same signals that advertisers, brokers, and tracker SDKs use to profile you, including device identity, app attention, location patterns, ambient sensors, sleep clues, notifications, network behavior, and local voice context, then translates them into clear insight cards you can actually understand. The difference is ownership: every data point stays on your device in a SQLCipher-encrypted vault that only you can unlock. No cloud account, no broker pipeline, no hidden upload. Just the profile others try to build about you, made visible and kept under your control.

## The Problem

Your phone leaks an extraordinary amount of information about you. Advertising SDKs embedded in the apps you use every day collect your device fingerprint, location history, app usage patterns, screen behavior, sensor readings, network identifiers, and more — then transmit it all to data brokers and ad exchanges you've never heard of.

Existing privacy tools like Exodus Privacy and TrackerControl show you *what leaves your device*. MirrorTrack takes the complementary approach: it collects the same fields a tracker SDK would, so you can inspect your own data portrait at the source — before it ever reaches a network boundary.

## What It Does

MirrorTrack runs 31 data collectors across 7 categories, feeding a unified encrypted database that powers 26 insight cards with real behavioral inference:

### Data Collection (31 Collectors)

| Category | Collectors | Examples |
|----------|-----------|----------|
| **Device** | 5 | Build info, hardware specs, unique identifiers, integrity checks, system stats |
| **Behavioral** | 4 | Screen on/off, battery state, app lifecycle, logcat streams |
| **Location** | 2 | GPS/network fixes, activity recognition (walking/driving/still) |
| **Network** | 6 | WiFi SSIDs, Bluetooth devices, carrier info, public IP, connectivity state, per-app data usage |
| **Apps** | 5 | Installed packages, usage stats, notification listener, AppOps audit, privacy dashboard |
| **Sensors** | 5 | Accelerometer/gyroscope, environment (light/pressure/temp), ambient sound level, step counter, body sensors |
| **Personal** | 4 | Calendar events, contacts metadata, photo EXIF data, on-device voice context |

Every collector is **opt-in** — disabled by default, toggled individually in Settings. Each permission is gated behind an in-app explanation before any runtime request.

### Insight Engine (26 Cards)

The Insights screen processes raw data points into behavioral intelligence — the same kind of profiles that ad tech builds about you:

| Card | What It Reveals |
|------|----------------|
| **Today** | Daily dashboard: data points, unlocks, screen time, steps, battery delta |
| **Sleep Timeline** | Last-72-hour sleep inference from phone inactivity, ambient light, ambient sound, and speech quietness |
| **App Attention** | Top apps by foreground time with week-over-week delta |
| **Anomaly Feed** | Statistical outliers in your behavior (unusual unlock counts, screen time spikes) |
| **Location Clusters** | GPS fix clustering with interactive dot map and user-renamable places |
| **Unlock Latency** | Median time from notification to screen unlock, per app |
| **Fingerprint Stability** | Device identity field tracking — what changed and when |
| **Monthly Trends** | 6-month behavioral comparison: unlocks, screen time, steps |
| **Engagement Score** | DAU/WAU stickiness, session frequency, retention flags |
| **Privacy Radar** | Per-app privacy invasion score from camera/mic/location/contacts access |
| **Data Flow** | Network TX/RX per app with suspicious upload ratio flagging |
| **App Compulsion** | Most-launched apps by frequency with inter-launch gap |
| **Device Health** | RAM pressure, process counts, thermal status, uptime |
| **Identity Entropy** | Fingerprint uniqueness quantified in bits (1-in-N device calculation) |
| **Home & Work** | Location inference from GPS clusters + time-of-day analysis |
| **Circadian Rhythm** | 24-hour unlock histogram, chronotype classification, activity spread |
| **Routine Predictability** | Hourly entropy scoring, weekday/weekend cosine distance |
| **Social Pressure** | Notification-to-unlock correlation per app, response rate ranking |
| **App Portfolio** | Installed app categorization with demographic inference |
| **Charging Behavior** | Charge cycles, discharge depth, overnight patterns |
| **WiFi Footprint** | SSID frequency distribution, Shannon entropy mobility score |
| **Session Fragmentation** | App-switching rate as attention span proxy |
| **Dwell Times** | Per-location visit duration with classification (home/work/transit/retail) |
| **Weekday vs Weekend** | Side-by-side behavioral comparison with divergence scoring |
| **Income Inference** | Socioeconomic tier from device model + carrier + app signals |
| **Commute Pattern** | Departure/return times, transport mode, consistency scoring |
| **Voice Context** | Local speech-window summaries for conversation density and context tags without storing raw audio |

### Security

- **SQLCipher encryption** — AES-256-CBC with HMAC-SHA512 page authentication
- **Argon2id key derivation** — passphrase stretched with memory-hard KDF
- **Passphrase hygiene** — never touches disk unencrypted, zeroed from memory after use
- **Android Keystore** wraps the salt at rest
- **No cloud backup** — `android:allowBackup="false"` with data extraction rules
- **No logging** of collected data in release builds

## Architecture

```
Collector (interface)
  |-- polled: suspend fun collect() -> List<DataPoint>
  |-- streamed: fun stream() -> Flow<DataPoint>
            |
            v
        Ingestor (singleton)
          |-- in-memory ring buffer (4096 capacity)
          |-- flush trigger: size >= 100 OR elapsed >= 60s
          |-- overflow monitoring with drop counters
            |
            v
        MirrorDatabase (SQLCipher Room)
          |-- data_points table (unified schema)
          |-- RetentionWorker (periodic expiry)
            |
            v
        InsightsViewModel
          |-- 25 async computations
          |-- behavioral inference engine
          |-- anomaly detection
```

**Unified DataPoint schema** — every collector emits the same shape:

```kotlin
data class DataPoint(
    val timestamp: Long,        // epoch millis
    val collectorId: String,    // "build_info", "screen_state"
    val category: Category,     // enum: DEVICE, BEHAVIORAL, LOCATION, ...
    val key: String,            // "manufacturer", "screen_on"
    val value: String,          // JSON-encoded
    val valueType: ValueType    // STRING | LONG | DOUBLE | BOOLEAN | JSON
)
```

One row per field. A single `BuildInfoCollector` poll produces ~15 rows. This is deliberate: zero schema churn as new collectors arrive, and DuckDB pivots trivially on desktop for offline analysis.

**Adding a new collector** is a single-file operation:
1. Create `collectors/<category>/<Name>Collector.kt` implementing `Collector`
2. Register it in `CollectorRegistry` (Hilt multibinding)
3. Done. It appears automatically in Settings and Category Browser.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1, JDK 17 |
| UI | Jetpack Compose (BOM 2024.11) |
| DI | Hilt 2.52 |
| Database | Room 2.6.1 + SQLCipher 4.6.1 |
| KDF | Argon2kt 1.6 |
| Scheduling | WorkManager 2.9 + Foreground Service |
| Settings | DataStore (Preferences) |
| Async | kotlinx.coroutines + Flow |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

**Explicitly excluded:** Firebase, Google Play Services, Crashlytics, any analytics SDK, any ad SDK. Not even transitively. The `INTERNET` permission exists solely for one public-IP lookup collector.

## Build & Install

```bash
# Requires Android SDK + JDK 17
./gradlew :app:assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant ADB-tier permissions for advanced collectors
adb shell pm grant com.potpal.mirrortrack android.permission.PACKAGE_USAGE_STATS
adb shell appops set com.potpal.mirrortrack GET_USAGE_STATS allow
```

## Desktop Analysis

Pull the encrypted database off the device and analyze offline with DuckDB:

```bash
cd desktop-analysis
pip install -r requirements.txt

# Decrypt (prompts for passphrase)
python decrypt.py /tmp/mirrortrack.db.enc /tmp/salt.bin

# Explore with pre-built queries
python explore.py /tmp/mirrortrack.db
```

## Repo Layout

```
mirrortrack/
+-- CLAUDE.md                         Architecture rules & constraints
+-- .claude/commands/                  Dev slash commands (build, install, dump-db)
+-- app/src/main/kotlin/.../
|   +-- collectors/
|   |   +-- Collector.kt              Interface + AccessTier enum
|   |   +-- DataPoint.kt              Unified event shape
|   |   +-- Ingestor.kt               Batching write gateway
|   |   +-- CollectorRegistry.kt      Hilt multibinding
|   |   +-- device/                    BuildInfo, Hardware, Identifiers, Integrity, SystemStats
|   |   +-- behavioral/               Screen, Battery, AppLifecycle, Logcat, Boot
|   |   +-- location/                  GPS, ActivityRecognition
|   |   +-- network/                   WiFi, Bluetooth, Carrier, IP, Connectivity, Usage
|   |   +-- apps/                      Installed, UsageStats, Notifications, AppOps, Privacy
|   |   +-- sensors/                   Motion, Environment, Step, Body
|   |   +-- personal/                  Calendar, Contacts, MediaExif
|   +-- data/
|   |   +-- MirrorDatabase.kt         Room + SQLCipher
|   |   +-- CryptoManager.kt          Argon2id KDF + salt management
|   |   +-- KeystoreManager.kt        Android Keystore wrapping
|   |   +-- DatabaseModule.kt         Hilt + DatabaseHolder lifecycle
|   +-- scheduling/
|   |   +-- CollectionForegroundService.kt
|   |   +-- PolledCollectionWorker.kt
|   |   +-- CollectorHealthTracker.kt
|   |   +-- RetentionWorker.kt
|   +-- export/
|   |   +-- ExportManager.kt          Encrypted zip export
|   |   +-- ImportManager.kt          Backup restore with salt handling
|   |   +-- TrackerPayloadGenerator.kt
|   +-- ui/
|   |   +-- unlock/                    Passphrase entry + key derivation
|   |   +-- feed/                      Real-time data point stream
|   |   +-- insights/                  25-card behavioral analysis (2800+ lines)
|   |   +-- categories/                Category browser + detail drill-down
|   |   +-- settings/                  Per-collector toggles + health indicators
|   |   +-- permissions/               Runtime permission flow
|   +-- settings/CollectorPreferences.kt  DataStore-backed preferences
|   +-- util/Logger.kt                    Debug-gated logging wrapper
+-- app/src/test/                      17 unit tests
+-- desktop-analysis/                  Python decrypt + DuckDB exploration
+-- gradle/libs.versions.toml         Central version catalog
```

## Permission Tiers

| Tier | Access | Examples |
|------|--------|----------|
| **NONE** | Available immediately | Build info, sensors, battery |
| **RUNTIME** | Standard permission dialog | Location, contacts, calendar, Bluetooth |
| **SPECIAL_ACCESS** | Manual Settings toggle | Usage stats, notification listener |
| **RESTRICTED** | Play Store policy gated | `QUERY_ALL_PACKAGES` |
| **ADB** | Developer shell grant | `READ_LOGS`, `GET_APP_OPS_STATS`, `DUMP` |

## Threat Model

**Protects against:**
- Casual device inspection without the passphrase
- Cloud backup extraction (`adb backup` disabled, data extraction rules set)
- A lost/stolen locked phone
- Third-party app data access (SQLCipher + app-private storage)

**Does NOT protect against:**
- Rooted attacker with the unlocked phone in hand
- Passphrase brute force with a weak passphrase
- Malware with root or accessibility service access
- Hardware attacks (cold boot, JTAG)
- Forgetting your passphrase (there is no recovery — that's the point)

## Out of Scope (by design)

- **No VPN/network interception** — other tools (PCAPdroid, TrackerControl) do this better
- **No AccessibilityService** — Play Store ban territory, ethical concerns
- **No root-required features** — must work on stock Android
- **No cloud sync** — the entire point is local-only
- **No multi-user** — single device owner by design

## License

TBD. Leaning GPL-3.0 to align with the privacy tooling ecosystem (TrackerControl, F-Droid norms).
