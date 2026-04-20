package com.potpal.mirrortrack.collectors.network

import android.content.Context
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class IpCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "public_ip"
    override val displayName: String = "Public IP Address"
    override val rationale: String =
        "Looks up the device's public IP address via https://api.ipify.org. " +
            "THIS IS THE ONLY COLLECTOR THAT MAKES AN OUTBOUND NETWORK REQUEST. " +
            "Disabled by default; must be explicitly opted in."
    override val category: Category = Category.NETWORK
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 1.hours
    override val defaultRetention: Duration = 90.days

    companion object {
        private const val LOOKUP_ENDPOINT = "https://api.ipify.org?format=text"
        private const val TIMEOUT_MS = 5_000
    }

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        var statusCode = -1

        try {
            val url = URL(LOOKUP_ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"

            try {
                statusCode = connection.responseCode
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }
                    points.add(DataPoint.string(id, category, "public_ip", ip))
                } else {
                    Logger.w("IpCollector", "IP lookup returned status $statusCode")
                    points.add(DataPoint.string(id, category, "public_ip", "unavailable"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Logger.e("IpCollector", "IP lookup failed", e)
            points.add(DataPoint.string(id, category, "public_ip", "unavailable"))
        }

        points.add(DataPoint.string(id, category, "lookup_endpoint", LOOKUP_ENDPOINT))
        points.add(DataPoint.long(id, category, "lookup_status_code", statusCode.toLong()))

        return points
    }
}
