package com.potpal.mirrortrack.collectors.network

import android.content.Context
import android.telephony.TelephonyManager
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class CarrierCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "carrier"
    override val displayName: String = "Carrier Info"
    override val rationale: String =
        "Collects carrier and SIM metadata (name, MCC/MNC, roaming status). " +
            "No permissions required. Gracefully handles devices without telephony."
    override val category: Category = Category.NETWORK
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 365.days

    override suspend fun isAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null) {
                Logger.w("CarrierCollector", "TelephonyManager unavailable")
                return emptyList()
            }

            points.add(DataPoint.string(id, category, "carrier_name", tm.networkOperatorName ?: "unknown"))

            val simOperator = tm.simOperator ?: ""
            val mcc = if (simOperator.length >= 3) simOperator.substring(0, 3) else "unknown"
            val mnc = if (simOperator.length > 3) simOperator.substring(3) else "unknown"
            points.add(DataPoint.string(id, category, "mcc", mcc))
            points.add(DataPoint.string(id, category, "mnc", mnc))

            points.add(DataPoint.string(id, category, "sim_country_iso", tm.simCountryIso ?: "unknown"))
            points.add(DataPoint.string(id, category, "sim_operator", tm.simOperator ?: "unknown"))
            points.add(DataPoint.string(id, category, "network_country_iso", tm.networkCountryIso ?: "unknown"))
            points.add(DataPoint.string(id, category, "network_operator", tm.networkOperator ?: "unknown"))

            val phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "gsm"
                TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
                TelephonyManager.PHONE_TYPE_SIP -> "sip"
                TelephonyManager.PHONE_TYPE_NONE -> "none"
                else -> "unknown"
            }
            points.add(DataPoint.string(id, category, "phone_type", phoneType))

            points.add(DataPoint.bool(id, category, "is_roaming", tm.isNetworkRoaming))
        } catch (e: Exception) {
            Logger.e("CarrierCollector", "Error collecting carrier data", e)
        }

        return points
    }
}
