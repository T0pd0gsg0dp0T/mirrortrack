package com.potpal.mirrortrack.collectors.behavioral

import android.content.Context
import android.content.pm.PackageManager
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Reads system logcat from all processes. Requires the development-level
 * READ_LOGS permission granted via ADB:
 *
 *   adb shell pm grant <pkg> android.permission.READ_LOGS
 *
 * Without READ_LOGS, `logcat` only returns this app's own logs (useless
 * for cross-app behavioral analysis). With it, we see:
 *
 *   - ActivityManager: app launches, process deaths, ANRs
 *   - ConnectivityService: network transitions
 *   - WifiService: SSID associations and scans
 *   - PowerManagerService: wake locks, sleep/wake cycles
 *   - WindowManager: focus changes between apps
 *   - PackageManager: installs, updates, uninstalls
 *
 * This is the single richest behavioral data source on a non-rooted
 * Android device. Tracker SDKs with system-level access (OEM pre-installs,
 * carrier bloatware) have this data permanently.
 */
@Singleton
class LogcatCollector @Inject constructor() : Collector {
    override val id = "logcat"
    override val displayName = "System Logcat"
    override val rationale =
        "Reads system-wide logcat to capture app launches, crashes, " +
        "network transitions, and process lifecycle events from all apps. " +
        "Requires ADB grant: READ_LOGS."
    override val category = Category.BEHAVIORAL
    override val requiredPermissions: List<String> = listOf("android.permission.READ_LOGS")
    override val accessTier = AccessTier.ADB
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 15.minutes
    override val defaultRetention: Duration = 30.days

    @Volatile
    private var lastPollEpoch: String? = null

    override suspend fun isAvailable(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(
            "android.permission.READ_LOGS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()

        try {
            // Read logcat since last poll, or last 15 minutes
            val args = mutableListOf("logcat", "-d", "-v", "epoch", "-b", "main,system")
            val since = lastPollEpoch
            if (since != null) {
                args.addAll(listOf("-t", since))
            } else {
                args.addAll(listOf("-t", "900")) // last 900 lines as fallback
            }

            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor()

            // Update epoch for next poll
            lastPollEpoch = (System.currentTimeMillis() / 1000).toString()

            var appLaunches = 0
            var processDied = 0
            var anrs = 0
            var crashes = 0
            var networkChanges = 0
            var wifiEvents = 0
            var installs = 0
            val appFocusChanges = mutableMapOf<String, Int>()

            for (line in lines) {
                when {
                    // ActivityManager: app starts
                    line.contains("ActivityManager") && line.contains("START u0") -> {
                        appLaunches++
                        val pkg = extractPackage(line, "cmp=")
                        if (pkg != null) {
                            appFocusChanges[pkg] = (appFocusChanges[pkg] ?: 0) + 1
                        }
                    }
                    // Process deaths
                    line.contains("ActivityManager") && line.contains("Process ") && line.contains(" has died") -> {
                        processDied++
                    }
                    // ANRs
                    line.contains("ANR in ") -> {
                        anrs++
                        val pkg = extractAfter(line, "ANR in ")
                        if (pkg != null) {
                            points.add(DataPoint.string(id, category, "anr", pkg))
                        }
                    }
                    // Fatal exceptions (crashes)
                    line.contains("FATAL EXCEPTION") -> {
                        crashes++
                    }
                    // Connectivity changes
                    line.contains("ConnectivityService") && line.contains("NetworkInfo") -> {
                        networkChanges++
                    }
                    // WiFi state changes
                    line.contains("WifiService") || (line.contains("wpa_supplicant") && line.contains("CTRL-EVENT")) -> {
                        wifiEvents++
                    }
                    // Package install/update
                    line.contains("PackageManager") && (line.contains("INSTALL") || line.contains("UPDATE")) -> {
                        installs++
                    }
                }
            }

            points.add(DataPoint.long(id, category, "lines_parsed", lines.size.toLong()))
            points.add(DataPoint.long(id, category, "app_launches", appLaunches.toLong()))
            points.add(DataPoint.long(id, category, "process_deaths", processDied.toLong()))
            points.add(DataPoint.long(id, category, "anr_count", anrs.toLong()))
            points.add(DataPoint.long(id, category, "crash_count", crashes.toLong()))
            points.add(DataPoint.long(id, category, "network_transitions", networkChanges.toLong()))
            points.add(DataPoint.long(id, category, "wifi_events", wifiEvents.toLong()))
            points.add(DataPoint.long(id, category, "package_installs", installs.toLong()))

            // Top 10 most-launched apps this period
            appFocusChanges.entries
                .sortedByDescending { it.value }
                .take(10)
                .forEach { (pkg, count) ->
                    points.add(DataPoint.long(id, category, "focus:$pkg", count.toLong()))
                }

            Logger.d(TAG, "Parsed ${lines.size} lines: $appLaunches launches, $crashes crashes")

        } catch (e: Exception) {
            Logger.w(TAG, "Logcat collection failed — is READ_LOGS granted?", e)
            points.add(DataPoint.string(id, category, "status", "permission_denied"))
        }

        return points
    }

    private fun extractPackage(line: String, prefix: String): String? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val rest = line.substring(idx + prefix.length)
        return rest.substringBefore("/").substringBefore(" ").substringBefore("}")
    }

    private fun extractAfter(line: String, prefix: String): String? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        return line.substring(idx + prefix.length).trim().substringBefore(" ").substringBefore("(")
    }

    companion object {
        private const val TAG = "LogcatCollector"
    }
}
