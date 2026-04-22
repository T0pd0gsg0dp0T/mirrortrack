# MirrorTrack — Claude Code Context

## What this is

A personal Android app that collects the same categories of device/behavioral/sensor data that third-party tracker SDKs typically harvest, but stores it only in a local SQLCipher-encrypted database for the device owner's own inspection. No network transmission of collected data ever leaves the device. No analytics, no telemetry, no crash reporting.

The repo is public, so keep it code-only: never commit device exports, decrypted databases, screenshots containing personal data, keystores, salts, transcripts, logs, or local analysis outputs.

Current app shape:
- 31 opt-in collectors across device, behavioral, location, network, apps, sensors, and personal data.
- 26 insight cards, including the last-72-hour Sleep Timeline and Voice Context.
- Sleep inference uses phone inactivity plus available ambient light, ambient sound, and voice quietness evidence. Screen-off alone is not considered a strong signal.

## Non-negotiable rules

1. **No network egress of collected data.** The `INTERNET` permission is currently declared only for public-IP lookup (one collector) and MUST NOT be used by any other code path. If you add a feature that needs network access, ask first.
2. **No Google/Facebook/Amazon/Firebase/Crashlytics/Analytics SDKs.** Full stop. Not even transitive. Run `./gradlew :app:dependencies` and reject any PR that introduces them.
3. **No `play-services-*` artifacts.** Use `LocationManager` directly, not `FusedLocationProviderClient`. Yes, it's slightly worse; that's the trade.
4. **Every collector is opt-in.** Default state for any new collector is `defaultEnabled = false`. User must toggle it on in Settings.
5. **Every permission is gated behind in-app explanation.** No silent runtime requests. The rationale string is mandatory on every `Collector`.
6. **DB passphrase never touches disk unencrypted** and is zeroed from memory after use (`CharArray.fill('\u0000')`, `ByteArray.fill(0)`).
7. **No logging of collected DataPoints** to logcat in release builds. `BuildConfig.DEBUG` gates all verbose logging.
8. **No personal artifacts in git.** Before making publicity or release changes, scan tracked files/history for `*.db`, `*.sqlite*`, exports, keystores, keys, transcripts, logs, and payload dumps.

## Stack

- Kotlin 2.1.x, JDK 17
- Android Gradle Plugin 8.7.x
- minSdk 26, targetSdk 34, compileSdk 34
- Jetpack Compose (BOM 2024.11.x)
- Room 2.6.1 + SQLCipher for Android (`net.zetetic:sqlcipher-android:4.6.1`)
- Hilt 2.52 for DI
- kotlinx.coroutines + Flow
- WorkManager 2.9.x for periodic collection
- Argon2kt 1.6.x for KDF
- Vosk for opt-in, on-device voice transcription windows
- DataStore (Preferences) for settings — not SharedPreferences

## Architecture

```
Collector (interface)
  ├── polled: suspend fun collect() -> List<DataPoint>
  └── streamed: fun stream() -> Flow<DataPoint>
            │
            ▼
        Ingestor (singleton)
          ├── in-memory ring buffer
          ├── flush trigger: size >= 100 OR elapsed >= 60s
          └── batch insert to Room
            │
            ▼
        MirrorDatabase (SQLCipher Room)
          ├── data_points table (unified schema)
          └── RetentionWorker (per-collector TTL)
            │
            ▼
        InsightsViewModel
          ├── async insight computations
          ├── data provenance / confidence metadata
          └── educational info panels in card headers
```

Adding a new collector is a single-file operation:
1. Create `collectors/<category>/<Name>Collector.kt` implementing `Collector`
2. Register it in `CollectorRegistry` (Hilt multibinding)
3. If it needs a new permission, add to manifest + update `PermissionsScreen`
4. Done. No schema migration, no UI changes required for it to appear in Category Browser.

## DataPoint schema (unified)

Every collector emits the same shape:

```kotlin
data class DataPoint(
    val timestamp: Long,        // epoch millis, System.currentTimeMillis()
    val collectorId: String,    // "build_info", "screen_state"
    val category: Category,     // enum
    val key: String,            // "manufacturer", "screen_on"
    val value: String,          // JSON-encoded; numbers as strings OK
    val valueType: ValueType    // STRING | LONG | DOUBLE | BOOLEAN | JSON
)
```

One row per field. A single BuildInfoCollector poll produces ~15 rows. This is deliberate: DuckDB pivots trivially on desktop, and it means zero schema churn as new collectors arrive.

## Permission tiers (see collectors/Collector.kt)

- **NONE**: Tier 1 data, available at boot. Build info, sensors (most), battery.
- **RUNTIME**: Standard runtime permissions. Location, contacts, etc.
- **SPECIAL_ACCESS**: User must manually enable in Settings. `PACKAGE_USAGE_STATS`, `BIND_NOTIFICATION_LISTENER_SERVICE`.
- **RESTRICTED**: Play Store policy flag. `QUERY_ALL_PACKAGES`. Keep the list short and justified.
- **ADB**: Development-only grants such as `READ_LOGS`, `GET_APP_OPS_STATS`, and `DUMP`. Never assume normal users have these.

## Current notable collectors

- `voice_transcription`: opt-in microphone collector using on-device Vosk. Stores transcript-derived signals and context tags locally; no raw audio is retained.
- `ambient_sound`: opt-in microphone collector that stores only short-window aggregate loudness metrics (`ambient_sound_dbfs`, RMS, peak, label); no raw audio is retained.
- `environment_sensor`: ambient light/proximity/pressure/temperature stream. Light data improves Sleep Timeline confidence.
- `screen_state`: screen on/off events. Useful for inactivity windows, but weak alone.
- ADB-backed collectors include logcat, AppOps audit, and system stats. They are for development/self-audit workflows, not Play Store-normal access.

## Commands

See `.claude/commands/` for reusable slash commands:
- `/build` — assembleDebug
- `/install` — install to connected device
- `/grant-adb-permissions` — grant development-only ADB permissions
- `/grant-usage-stats` — dev helper to grant PACKAGE_USAGE_STATS without the Settings walk
- `/dump-db` — pull encrypted DB off device, decrypt to /tmp, open in sqlite3

## Out of scope (explicitly)

- VpnService / network observation of other apps. Decided against.
- AccessibilityService text capture. Decided against (Play Store ban + ethics).
- Root-required features. Decided against.
- Cloud sync. Decided against.
- Multi-user / family account support. Single-user by design.
- Raw audio retention. Decided against; microphone collectors must store only local derived signals unless explicitly redesigned.

## Code style

- Suspend functions and Flow over callbacks and LiveData
- No `!!` operator in non-test code
- All `Context` references must be `@ApplicationContext` injected (no leaks)
- Collectors must be stateless or hold only immutable config
- No direct `android.util.Log` — use the wrapper in `util/Logger.kt` that respects `BuildConfig.DEBUG`

## When in doubt

- Prefer explicit over clever
- Prefer documented Android APIs over reflection
- Prefer failing closed (no data) over failing open (wrong data)
- Ask before adding a dependency
