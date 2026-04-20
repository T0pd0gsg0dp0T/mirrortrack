# Grant all ADB development permissions + operational exemptions

Grants development-level permissions (READ_LOGS, GET_APP_OPS_STATS, DUMP),
operational exemptions (background execution, Doze whitelist), and
special-access appops (USAGE_STATS, QUERY_ALL_PACKAGES).

All grants survive reboot. None require root.

```bash
PKG=com.potpal.mirrortrack.debug

echo "=== Granting development permissions ==="
adb shell pm grant $PKG android.permission.READ_LOGS && echo "  READ_LOGS ✓"
adb shell pm grant $PKG android.permission.GET_APP_OPS_STATS && echo "  GET_APP_OPS_STATS ✓"
adb shell pm grant $PKG android.permission.DUMP && echo "  DUMP ✓"

echo "=== Granting AppOps ==="
adb shell appops set $PKG android:get_usage_stats allow && echo "  USAGE_STATS ✓"
adb shell appops set $PKG QUERY_ALL_PACKAGES allow && echo "  QUERY_ALL_PACKAGES ✓"
adb shell appops set $PKG RUN_IN_BACKGROUND allow && echo "  RUN_IN_BACKGROUND ✓"
adb shell appops set $PKG RUN_ANY_IN_BACKGROUND allow && echo "  RUN_ANY_IN_BACKGROUND ✓"

echo "=== Doze whitelist ==="
adb shell dumpsys deviceidle whitelist +$PKG && echo "  Doze whitelist ✓"

echo ""
echo "=== Verification ==="
adb shell dumpsys package $PKG | grep -E "READ_LOGS|GET_APP_OPS|DUMP" | head -5
echo ""
echo "All ADB permissions granted. Enable collectors in Settings > toggle each ADB collector on."
```
