package com.potpal.mirrortrack.collectors.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
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

@Singleton
class WifiCollector @Inject constructor() : Collector {
    override val id = "wifi"
    override val displayName = "Wi-Fi"
    override val rationale =
        "Captures current Wi-Fi connection info and nearby scan results for " +
        "location-adjacent fingerprinting. Requires location permission."
    override val category = Category.NETWORK
    override val requiredPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 15.minutes
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean =
        requiredPermissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    @Suppress("DEPRECATION")
    override suspend fun collect(context: Context): List<DataPoint> {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()

        val points = mutableListOf<DataPoint>()
        try {
            val info = wm.connectionInfo
            if (info != null) {
                points.add(DataPoint.string(id, category, "current_ssid",
                    info.ssid?.removeSurrounding("\"") ?: ""))
                points.add(DataPoint.string(id, category, "current_bssid", info.bssid ?: ""))
                points.add(DataPoint.long(id, category, "current_rssi_dbm", info.rssi.toLong()))
                points.add(DataPoint.long(id, category, "current_link_speed_mbps", info.linkSpeed.toLong()))
                points.add(DataPoint.long(id, category, "current_frequency_mhz", info.frequency.toLong()))
            }

            val scans = wm.scanResults.orEmpty()
            val jsonArray = scans.joinToString(",", "[", "]") { r ->
                """{"ssid":"${r.SSID ?: ""}","bssid":"${r.BSSID ?: ""}","rssi":${r.level},"frequency":${r.frequency}}"""
            }
            points.add(DataPoint.json(id, category, "scan_results", jsonArray))
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        }

        return points
    }

    companion object {
        private const val TAG = "WifiCollector"
    }
}
