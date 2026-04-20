package com.potpal.mirrortrack.collectors.network

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.telephony.TelephonyManager
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Per-app network usage — bytes sent and received over WiFi and mobile,
 * broken down by application. Uses NetworkStatsManager which requires
 * PACKAGE_USAGE_STATS (grantable via Settings or ADB).
 *
 * This is the data that carriers sell to data brokers and that ad networks
 * use for "data plan sensitivity" targeting. A user who consumes 20 GB/month
 * is treated differently than one using 2 GB/month.
 *
 *   adb shell appops set <pkg> USAGE_STATS allow
 */
@Singleton
class NetworkUsageCollector @Inject constructor() : Collector {
    override val id = "network_usage"
    override val displayName = "Per-App Network Usage"
    override val rationale =
        "Tracks bytes sent/received per app over WiFi and mobile data. " +
        "Shows which apps are the heaviest data consumers and potential " +
        "data exfiltration vectors. Requires PACKAGE_USAGE_STATS."
    override val category = Category.NETWORK
    override val requiredPermissions: List<String> = listOf("android.permission.PACKAGE_USAGE_STATS")
    override val accessTier = AccessTier.ADB
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 1.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        if (Build.VERSION.SDK_INT < 23) return emptyList()

        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyList()

        val pm = context.packageManager
        val points = mutableListOf<DataPoint>()

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86_400_000L

        // Get subscriber ID for mobile stats (may be null)
        val subscriberId = try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            tm?.subscriberId
        } catch (_: SecurityException) { null }

        // Query WiFi usage per UID
        val wifiUsage = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (rx, tx)
        try {
            val wifiBucket = nsm.querySummary(
                ConnectivityManager.TYPE_WIFI, null, oneDayAgo, now
            )
            val bucket = android.app.usage.NetworkStats.Bucket()
            while (wifiBucket.hasNextBucket()) {
                wifiBucket.getNextBucket(bucket)
                val uid = bucket.uid
                val (prevRx, prevTx) = wifiUsage[uid] ?: (0L to 0L)
                wifiUsage[uid] = (prevRx + bucket.rxBytes) to (prevTx + bucket.txBytes)
            }
            wifiBucket.close()
        } catch (e: Exception) {
            Logger.w(TAG, "WiFi stats query failed", e)
        }

        // Query mobile usage per UID
        val mobileUsage = mutableMapOf<Int, Pair<Long, Long>>()
        if (subscriberId != null) {
            try {
                val mobileBucket = nsm.querySummary(
                    ConnectivityManager.TYPE_MOBILE, subscriberId, oneDayAgo, now
                )
                val bucket = android.app.usage.NetworkStats.Bucket()
                while (mobileBucket.hasNextBucket()) {
                    mobileBucket.getNextBucket(bucket)
                    val uid = bucket.uid
                    val (prevRx, prevTx) = mobileUsage[uid] ?: (0L to 0L)
                    mobileUsage[uid] = (prevRx + bucket.rxBytes) to (prevTx + bucket.txBytes)
                }
                mobileBucket.close()
            } catch (e: Exception) {
                Logger.w(TAG, "Mobile stats query failed", e)
            }
        }

        // Merge UIDs and resolve package names
        val allUids = (wifiUsage.keys + mobileUsage.keys).distinct()
        val entries = allUids.mapNotNull { uid ->
            val pkgName = pm.getNameForUid(uid) ?: "uid_$uid"
            val (wifiRx, wifiTx) = wifiUsage[uid] ?: (0L to 0L)
            val (mobileRx, mobileTx) = mobileUsage[uid] ?: (0L to 0L)
            val totalBytes = wifiRx + wifiTx + mobileRx + mobileTx
            if (totalBytes == 0L) return@mapNotNull null

            PackageNetUsage(pkgName, wifiRx, wifiTx, mobileRx, mobileTx, totalBytes)
        }.sortedByDescending { it.totalBytes }

        // Emit top 30 by total usage
        for (entry in entries.take(30)) {
            val json = buildString {
                append("""{"package":"${escapeJson(entry.pkg)}",""")
                append(""""wifi_rx":${entry.wifiRx},""")
                append(""""wifi_tx":${entry.wifiTx},""")
                append(""""mobile_rx":${entry.mobileRx},""")
                append(""""mobile_tx":${entry.mobileTx},""")
                append(""""total_bytes":${entry.totalBytes}}""")
            }
            points.add(DataPoint.json(id, category, "net_usage:${entry.pkg}", json))
        }

        // Summary stats
        val totalWifiRx = wifiUsage.values.sumOf { it.first }
        val totalWifiTx = wifiUsage.values.sumOf { it.second }
        val totalMobileRx = mobileUsage.values.sumOf { it.first }
        val totalMobileTx = mobileUsage.values.sumOf { it.second }

        points.add(DataPoint.long(id, category, "total_wifi_rx_bytes", totalWifiRx))
        points.add(DataPoint.long(id, category, "total_wifi_tx_bytes", totalWifiTx))
        points.add(DataPoint.long(id, category, "total_mobile_rx_bytes", totalMobileRx))
        points.add(DataPoint.long(id, category, "total_mobile_tx_bytes", totalMobileTx))
        points.add(DataPoint.long(id, category, "apps_with_traffic", entries.size.toLong()))

        Logger.d(TAG, "Collected network usage for ${entries.size} apps")

        return points
    }

    private data class PackageNetUsage(
        val pkg: String,
        val wifiRx: Long, val wifiTx: Long,
        val mobileRx: Long, val mobileTx: Long,
        val totalBytes: Long
    )

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "NetworkUsage"
    }
}
