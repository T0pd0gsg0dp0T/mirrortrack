package com.potpal.mirrortrack.collectors.apps

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import kotlin.time.Duration.Companion.hours

@Singleton
class InstalledAppsCollector @Inject constructor() : Collector {
    override val id = "installed_apps"
    override val displayName = "Installed Apps"
    override val rationale =
        "Enumerates all installed packages with metadata. Requires QUERY_ALL_PACKAGES " +
        "(Play Store restricted permission)."
    override val category = Category.APPS
    override val requiredPermissions = listOf(Manifest.permission.QUERY_ALL_PACKAGES)
    override val accessTier = AccessTier.RESTRICTED
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 365.days

    override suspend fun isAvailable(context: Context): Boolean = true

    @Suppress("DEPRECATION")
    override suspend fun collect(context: Context): List<DataPoint> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getInstalledPackages(0)
        }

        return packages.mapNotNull { pkg ->
            val pkgName = pkg.packageName ?: return@mapNotNull null
            val isSystem = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            val installSource = try {
                if (Build.VERSION.SDK_INT >= 30) {
                    pm.getInstallSourceInfo(pkgName).installingPackageName ?: ""
                } else {
                    pm.getInstallerPackageName(pkgName) ?: ""
                }
            } catch (_: Exception) { "" }

            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                pkg.longVersionCode
            } else {
                pkg.versionCode.toLong()
            }

            val json = buildString {
                append("{\"package_name\":\"").append(escapeJson(pkgName)).append("\",")
                append("\"version_name\":\"").append(escapeJson(pkg.versionName ?: "")).append("\",")
                append("\"version_code\":").append(versionCode).append(",")
                append("\"install_time\":").append(pkg.firstInstallTime).append(",")
                append("\"update_time\":").append(pkg.lastUpdateTime).append(",")
                append("\"install_source\":\"").append(escapeJson(installSource)).append("\",")
                append("\"is_system\":").append(isSystem).append(",")
                append("\"enabled\":").append(pkg.applicationInfo?.enabled ?: false).append("}")
            }
            DataPoint.json(id, category, pkgName, json)
        }
    }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "InstalledApps"
    }
}
