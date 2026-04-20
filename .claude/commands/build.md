# Build debug APK

Run:

```bash
./gradlew :app:assembleDebug
```

If the build fails with a Kotlin/Compose compiler mismatch, check
`gradle/libs.versions.toml` for the Kotlin version and confirm the Compose
compiler plugin version matches (they're bundled from Kotlin 2.0+).

If the build fails on `net.zetetic:sqlcipher-android`, verify `mavenCentral()`
is in `settings.gradle.kts` repositories.
