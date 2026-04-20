package com.potpal.mirrortrack.collectors.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private val Context.identifiersDataStore by preferencesDataStore(name = "mirrortrack_identifiers")

@Singleton
class IdentifiersCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "identifiers"
    override val displayName: String = "Device Identifiers"
    override val rationale: String =
        "Collects stable device identifiers. ANDROID_ID is scoped per signing key and user, " +
            "so it is not a global device identifier and cannot be used to track across apps " +
            "signed by different keys."
    override val category: Category = Category.DEVICE_IDENTITY
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 365.days

    companion object {
        private val KEY_INSTALL_UUID = stringPreferencesKey("install_uuid")
    }

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()

        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            points.add(DataPoint.string(id, category, "android_id", androidId ?: "unavailable"))

            val installUuid = getOrCreateInstallUuid(context)
            points.add(DataPoint.string(id, category, "install_uuid", installUuid))

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            }

            points.add(
                DataPoint.long(id, category, "first_install_time", packageInfo.firstInstallTime)
            )
            points.add(
                DataPoint.long(id, category, "last_update_time", packageInfo.lastUpdateTime)
            )

            val signingInfo = packageInfo.signingInfo
            val sigSha256 = if (signingInfo != null) {
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                certs?.firstOrNull()?.let { cert ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.digest(cert.toByteArray())
                        .joinToString(":") { "%02X".format(it) }
                } ?: "unavailable"
            } else {
                "unavailable"
            }
            points.add(DataPoint.string(id, category, "package_signing_sha256", sigSha256))
        } catch (e: Exception) {
            Logger.e("IdentifiersCollector", "Error collecting identifiers", e)
        }

        return points
    }

    private suspend fun getOrCreateInstallUuid(context: Context): String {
        return context.identifiersDataStore.data
            .map { prefs -> prefs[KEY_INSTALL_UUID] }
            .first()
            ?: run {
                val newUuid = UUID.randomUUID().toString()
                context.identifiersDataStore.edit { prefs ->
                    if (prefs[KEY_INSTALL_UUID] == null) {
                        prefs[KEY_INSTALL_UUID] = newUuid
                    }
                }
                context.identifiersDataStore.data
                    .map { prefs -> prefs[KEY_INSTALL_UUID] }
                    .first() ?: newUuid
            }
    }
}
