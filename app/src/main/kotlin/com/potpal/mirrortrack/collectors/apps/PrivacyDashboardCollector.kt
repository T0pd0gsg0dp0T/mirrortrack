package com.potpal.mirrortrack.collectors.apps

import android.content.Context
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
class PrivacyDashboardCollector @Inject constructor() : Collector {
    override val id = "privacy_dashboard"
    override val displayName = "Privacy Dashboard"
    override val rationale =
        "Reads permission-usage history from the Android 12+ Privacy Dashboard via reflection."
    override val category = Category.APPS
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier = AccessTier.NONE
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 6.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31

    override suspend fun collect(context: Context): List<DataPoint> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()

        return try {
            val pm = context.getSystemService("permissionmanager") ?: return listOf(
                DataPoint.string(id, category, "status", "unavailable")
            )

            val method = try {
                pm.javaClass.getMethod("getPermissionUsages",
                    Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            } catch (_: NoSuchMethodException) {
                return listOf(DataPoint.string(id, category, "status", "unavailable"))
            }

            val result = method.invoke(pm, false, 86_400_000L)
            val points = mutableListOf<DataPoint>()
            points.add(DataPoint.string(id, category, "status", "available"))

            if (result is List<*>) {
                points.add(DataPoint.long(id, category, "usage_entry_count", result.size.toLong()))
                for (usage in result.filterNotNull().take(100)) {
                    val pkg = tryGetString(usage, "getPackageName") ?: continue
                    val group = tryGetString(usage, "getPermissionGroupName") ?: ""
                    val lastAccess = tryGetLong(usage, "getLastAccessTime") ?: 0L
                    val json = """{"package":"$pkg","permission_group":"$group","last_access_ms":$lastAccess}"""
                    points.add(DataPoint.json(id, category, "usage:$pkg:$group", json))
                }
            }

            points
        } catch (e: Exception) {
            Logger.w(TAG, "Privacy dashboard failed", e)
            listOf(DataPoint.string(id, category, "status", "unavailable"))
        }
    }

    private fun tryGetString(obj: Any, method: String): String? = try {
        obj.javaClass.getMethod(method).invoke(obj)?.toString()
    } catch (_: Exception) { null }

    private fun tryGetLong(obj: Any, method: String): Long? = try {
        obj.javaClass.getMethod(method).invoke(obj) as? Long
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "PrivacyDashboard"
    }
}
