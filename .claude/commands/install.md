# Install to connected device and tail logcat

```bash
./gradlew :app:installDebug && \
  adb shell am start -n com.potpal.mirrortrack.debug/com.potpal.mirrortrack.ui.MainActivity && \
  adb logcat -c && \
  adb logcat --pid=$(adb shell pidof -s com.potpal.mirrortrack.debug) '*:V'
```

The pid-filtered logcat only shows output from our app, not the whole system.
If the app isn't running yet when logcat starts, the pid lookup fails — wait
a second after `am start` completes, then rerun.
