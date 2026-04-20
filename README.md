# MirrorTrack

A personal Android app that collects the same categories of device,
behavioral, and sensor data that third-party tracker SDKs typically harvest —
but stores it only in a local SQLCipher-encrypted database for the device
owner's own inspection.

No cloud. No analytics. No crash reporting. No transmission. All collection
is opt-in per-category.

## Why

Most people have no idea how much their phone broadcasts about them, and the
existing tools (Exodus Privacy, TrackerControl, PCAPdroid) show *what leaves
the device*. MirrorTrack takes the complementary angle: it collects the
same fields a tracker SDK would, so you can inspect your own fingerprint
at the source. Useful for:

- Understanding what a given ad SDK actually sees
- Building a personal baseline for anomaly detection
- Offline analysis of your own behavioral patterns
- Curiosity

## Status

Scaffold / skeleton. Compiles, runs, has two reference collectors wired
end-to-end through the Ingestor + SQLCipher Room DB. Not yet:

- Unlock UI (passphrase entry → key derivation → DB open)
- Live feed screen
- Category browser
- Remaining ~35 collectors
- Foreground service runtime
- Export / purge
- Per-collector retention policy

See `CLAUDE.md` for the conventions and architectural rules the project
follows. That file is also the context Claude Code reads on first interaction.

## Repo layout

```
mirrortrack/
├── CLAUDE.md                      Conventions, constraints, non-negotiables
├── .claude/commands/              Slash commands for Claude Code
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml    Permission declarations, audited
│       ├── kotlin/com/potpal/mirrortrack/
│       │   ├── MirrorTrackApp.kt
│       │   ├── collectors/
│       │   │   ├── Collector.kt           Interface
│       │   │   ├── DataPoint.kt           Unified event shape
│       │   │   ├── Category.kt            Enum
│       │   │   ├── Ingestor.kt            Batching write gateway
│       │   │   ├── CollectorRegistry.kt   Hilt binding of all collectors
│       │   │   ├── device/
│       │   │   │   └── BuildInfoCollector.kt       (polled, reference)
│       │   │   └── behavioral/
│       │   │       ├── ScreenStateCollector.kt     (streamed, reference)
│       │   │       └── BootReceiver.kt
│       │   ├── data/
│       │   │   ├── MirrorDatabase.kt      Room + SQLCipher
│       │   │   ├── DatabaseModule.kt      Hilt + DatabaseHolder lifecycle
│       │   │   ├── DataPointDao.kt
│       │   │   ├── CryptoManager.kt       Argon2id KDF
│       │   │   └── entities/DataPointEntity.kt
│       │   └── ui/MainActivity.kt         Stub Compose host
│       └── res/
├── gradle/libs.versions.toml      Central version catalog
├── desktop-analysis/
│   ├── decrypt.py                 Offline Argon2 + SQLCipher decrypt
│   ├── explore.py                 DuckDB starter queries
│   └── requirements.txt
└── README.md
```

## First build

```bash
# From repo root. Assumes Android SDK + JDK 17 installed.
./gradlew :app:assembleDebug
```

Install to a connected device:

```bash
./gradlew :app:installDebug
adb shell am start -n com.potpal.mirrortrack.debug/com.potpal.mirrortrack.ui.MainActivity
```

Current on-launch behavior: blank Compose screen with a placeholder string.
That's expected for the scaffold.

## Desktop analysis

After exporting an encrypted DB from the phone (feature not yet implemented
— for now, pull it with the `/dump-db` slash command):

```bash
cd desktop-analysis
pip install -r requirements.txt
python decrypt.py /tmp/mirrortrack.db.enc /tmp/salt.bin
python explore.py /tmp/mirrortrack.db
```

## Immediate next steps for Claude Code

Suggested order, each a tractable session:

1. **Unlock flow**: `UnlockScreen`, `UnlockViewModel`, wire to `CryptoManager`
   + `DatabaseHolder.open()`. Gate `MainActivity` content on `isOpen()`.
2. **LiveFeedScreen**: observe `dao.observeRecent()`, render reverse-chrono list.
3. **CollectionForegroundService**: ongoing notification, launches enabled
   collectors. Register `ScreenStateCollector.stream()` into `Ingestor`.
4. **CollectionScheduler**: WorkManager periodic worker, invokes enabled
   polled collectors at their configured cadence.
5. **Third collector**: `BatteryCollector`. Polled, event-driven hybrid
   (registerReceiver on `ACTION_BATTERY_CHANGED` is sticky — emit current
   state on subscribe, then stream changes). Good exercise for the hybrid case.
6. Then add collectors one per session until the full catalog (~40) is covered.

## Threat model (honest)

What this protects against:
- Casual inspection of the device by someone without the passphrase
- Cloud backup / `adb backup` extraction (disabled + data extraction rules)
- A lost/stolen locked phone

What this does NOT protect against:
- Rooted attacker with the unlocked phone in hand (until Keystore wrapping
  is added)
- Passphrase brute force if you pick a bad passphrase
- Malware with root or accessibility service access
- Hardware attacks (cold boot, JTAG)
- Your future self forgetting the passphrase (there's no recovery — that's
  the whole point)

## License

TBD. Lean toward GPL-3 if publishing, to match the TrackerControl / F-Droid
norm for privacy tooling.
