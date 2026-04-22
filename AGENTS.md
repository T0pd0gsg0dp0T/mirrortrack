# Repository Guidelines

## Project Structure & Module Organization

MirrorTrack is a single Android app module. Kotlin source lives in `app/src/main/kotlin/com/potpal/mirrortrack/`, grouped by responsibility:

- `collectors/`: opt-in data collectors and the `Collector` contract.
- `data/`: SQLCipher Room database, DAO, crypto, and keystore code.
- `scheduling/`: foreground service, WorkManager jobs, and retention.
- `ui/`: Compose screens, navigation, theme, insights, settings, and unlock flow.
- `export/`: encrypted import/export and desktop payload helpers.

Android resources are in `app/src/main/res/`. Unit tests live in `app/src/test/kotlin/`. Desktop analysis helpers are in `desktop-analysis/`. Project context and hard privacy rules are in `CLAUDE.md`.

## Build, Test, and Development Commands

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:testDebugUnitTest` runs JVM unit tests.
- `./gradlew :app:assembleDebug testDebugUnitTest` builds and tests together.
- `./gradlew :app:lintDebug` runs Android lint.
- `./gradlew :app:installDebug` installs the debug APK on a connected device.
- `adb shell am start -n com.potpal.mirrortrack.debug/com.potpal.mirrortrack.ui.MainActivity` launches the debug app.

Use `.claude/commands/` for common local workflows such as install, usage-stats grants, ADB permissions, and database dump analysis.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic Compose patterns. Prefer `suspend` functions and `Flow` over callbacks. Avoid `!!` in non-test code. Inject long-lived `Context` values with `@ApplicationContext`. Collectors should be stateless or immutable, named `<Name>Collector`, and registered in `CollectorRegistry`.

Do not add analytics, crash reporting, Firebase, Google Play Services, or hidden network egress. Use `util/Logger.kt`; do not call `android.util.Log` directly.

## Testing Guidelines

Tests use JUnit under `app/src/test/kotlin/`. Name test files after the unit under test, for example `DataPointTest.kt` or `CollectorHealthTrackerTest.kt`. Add focused tests for parsing, scoring, crypto-adjacent logic, collector health, and insight calculations. Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug` before pushing.

## Commit & Pull Request Guidelines

Commit messages are short, imperative, and descriptive, for example `Improve sleep insights and README pitch` or `Add fallback data sources to all insight computation functions`.

Pull requests should include a concise summary, testing performed, affected collectors/permissions, screenshots for UI changes, and any privacy or security implications. Never commit personal artifacts: `*.db`, `*.sqlite*`, exports, keystores, keys, salts, transcripts, logs, screenshots with private data, or local analysis output.

## Security & Configuration Tips

Every collector must be opt-in (`defaultEnabled = false`) and have a clear rationale. New permissions require manifest updates and in-app explanation. The public-IP collector is the only allowed use of `INTERNET` unless explicitly approved.
