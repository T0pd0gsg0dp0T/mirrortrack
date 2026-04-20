package com.potpal.mirrortrack.collectors.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Singleton
class ConnectivityCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "connectivity"
    override val displayName: String = "Connectivity"
    override val rationale: String =
        "Monitors network connectivity state including transport type, metering, bandwidth estimates, " +
            "and VPN status. Uses ACCESS_NETWORK_STATE (install-time, no runtime prompt)."
    override val category: Category = Category.NETWORK
    override val requiredPermissions: List<String> = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 15.minutes
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun collect(context: Context): List<DataPoint> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return emitConnectivityState(cm)
    }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial state
        emitConnectivityState(cm).forEach { trySend(it) }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                try {
                    buildDataPointsFromCapabilities(caps).forEach { trySend(it) }
                } catch (e: Exception) {
                    Logger.e("ConnectivityCollector", "Error in capabilities callback", e)
                }
            }

            override fun onLost(network: Network) {
                try {
                    trySend(DataPoint.string(id, category, "active_transport", "none"))
                    trySend(DataPoint.bool(id, category, "is_metered", false))
                    trySend(DataPoint.bool(id, category, "is_validated", false))
                    trySend(DataPoint.long(id, category, "link_downstream_kbps", 0L))
                    trySend(DataPoint.long(id, category, "link_upstream_kbps", 0L))
                    trySend(DataPoint.bool(id, category, "vpn_active", false))
                } catch (e: Exception) {
                    Logger.e("ConnectivityCollector", "Error in onLost callback", e)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        Logger.d("ConnectivityCollector", "Registered network callback")

        awaitClose {
            cm.unregisterNetworkCallback(callback)
            Logger.d("ConnectivityCollector", "Unregistered network callback")
        }
    }

    private fun emitConnectivityState(cm: ConnectivityManager): List<DataPoint> {
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return listOf(
                DataPoint.string(id, category, "active_transport", "none"),
                DataPoint.bool(id, category, "is_metered", false),
                DataPoint.bool(id, category, "is_validated", false),
                DataPoint.long(id, category, "link_downstream_kbps", 0L),
                DataPoint.long(id, category, "link_upstream_kbps", 0L),
                DataPoint.bool(id, category, "vpn_active", false)
            )
        return buildDataPointsFromCapabilities(caps)
    }

    private fun buildDataPointsFromCapabilities(caps: NetworkCapabilities): List<DataPoint> {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "unknown"
        }

        return listOf(
            DataPoint.string(id, category, "active_transport", transport),
            DataPoint.bool(
                id, category, "is_metered",
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            ),
            DataPoint.bool(
                id, category, "is_validated",
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ),
            DataPoint.long(id, category, "link_downstream_kbps", caps.linkDownstreamBandwidthKbps.toLong()),
            DataPoint.long(id, category, "link_upstream_kbps", caps.linkUpstreamBandwidthKbps.toLong()),
            DataPoint.bool(
                id, category, "vpn_active",
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
        )
    }
}
