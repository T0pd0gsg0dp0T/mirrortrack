# Grant PACKAGE_USAGE_STATS without the Settings walkthrough

During development, skip the Settings > Special app access > Usage access UI:

```bash
adb shell appops set com.potpal.mirrortrack.debug GET_USAGE_STATS allow
```

To revoke:

```bash
adb shell appops set com.potpal.mirrortrack.debug GET_USAGE_STATS default
```

Note: `appops` requires adb, not root. In production the user must do this
through Settings manually — there's no API to request it programmatically.
The UsageStatsCollector onboarding UX should deep-link to
`Settings.ACTION_USAGE_ACCESS_SETTINGS` with a clear explanation screen.
