package com.potpal.mirrortrack.collectors.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class LocationCollector @Inject constructor() : Collector {
    override val id = "location"
    override val displayName = "Location"
    override val rationale =
        "Streams GPS/network location fixes using LocationManager directly (no Play Services). " +
        "Requires foreground location permissions."
    override val category = Category.LOCATION
    override val requiredPermissions = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval = null // streamed
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean =
        requiredPermissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    override fun stream(context: Context): Flow<DataPoint> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(DataPoint.double(id, category, "lat", location.latitude))
                trySend(DataPoint.double(id, category, "lon", location.longitude))
                trySend(DataPoint.double(id, category, "altitude_m", location.altitude))
                trySend(DataPoint.double(id, category, "accuracy_m", location.accuracy.toDouble()))
                trySend(DataPoint.double(id, category, "bearing_deg", location.bearing.toDouble()))
                trySend(DataPoint.double(id, category, "speed_mps", location.speed.toDouble()))
                trySend(DataPoint.string(id, category, "provider", location.provider ?: "unknown"))
                trySend(DataPoint.bool(id, category, "is_mock", location.isFromMockProvider))
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 300_000L, 50f, listener, Looper.getMainLooper())
                    Logger.d(TAG, "Subscribed to $provider")
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
            close()
        }

        awaitClose {
            runCatching { lm.removeUpdates(listener) }
        }
    }

    companion object {
        private const val TAG = "LocationCollector"
    }
}
