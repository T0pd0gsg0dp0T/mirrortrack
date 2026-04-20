package com.potpal.mirrortrack.collectors.apps

import android.app.AppOpsManager
import android.content.Context
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

/**
 * Queries AppOpsManager for other apps' permission usage — which apps used
 * camera, microphone, location, contacts, and when. Requires the
 * development-level GET_APP_OPS_STATS permission granted via ADB:
 *
 *   adb shell pm grant <pkg> android.permission.GET_APP_OPS_STATS
 *
 * This is the same data source that Android's Privacy Dashboard reads,
 * but available on API 19+ instead of 31+ and with richer granularity.
 */
@Singleton
class AppOpsAuditCollector @Inject constructor() : Collector {
    override val id = "appops_audit"
    override val displayName = "App Permission Audit"
    override val rationale =
        "Queries which apps on your device accessed camera, microphone, location, " +
        "contacts, clipboard, and other sensitive resources — and when. " +
        "Requires ADB grant: GET_APP_OPS_STATS."
    override val category = Category.APPS
    override val requiredPermissions: List<String> = listOf("android.permission.GET_APP_OPS_STATS")
    override val accessTier = AccessTier.ADB
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 2.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(
            "android.permission.GET_APP_OPS_STATS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return emptyList()

        val points = mutableListOf<DataPoint>()

        // Ops we care about — these are the privacy-sensitive operations
        // that tracker SDKs access silently
        val targetOps = intArrayOf(
            AppOpsManager.OPSTR_CAMERA.opInt(),
            AppOpsManager.OPSTR_RECORD_AUDIO.opInt(),
            AppOpsManager.OPSTR_COARSE_LOCATION.opInt(),
            AppOpsManager.OPSTR_FINE_LOCATION.opInt(),
            AppOpsManager.OPSTR_READ_CONTACTS.opInt(),
            AppOpsManager.OPSTR_READ_CALENDAR.opInt(),
            AppOpsManager.OPSTR_READ_CALL_LOG.opInt(),
            AppOpsManager.OPSTR_READ_SMS.opInt(),
        )

        try {
            // getPackagesForOps is @hide but accessible with GET_APP_OPS_STATS
            val method = appOps.javaClass.getMethod(
                "getPackagesForOps", IntArray::class.java
            )
            val result = method.invoke(appOps, targetOps) as? List<*> ?: return points

            for (pkgOps in result) {
                if (pkgOps == null) continue
                val pkg = tryGetString(pkgOps, "getPackageName") ?: continue
                val ops = tryGetList(pkgOps, "getOps") ?: continue

                for (op in ops) {
                    if (op == null) continue
                    val opCode = tryGetInt(op, "getOp") ?: continue
                    val opName = opCodeToName(opCode)
                    val mode = tryGetInt(op, "getMode") ?: -1
                    val lastAccess = tryGetLong(op, "getTime") ?: 0L

                    if (lastAccess <= 0) continue

                    val modeStr = when (mode) {
                        AppOpsManager.MODE_ALLOWED -> "allowed"
                        AppOpsManager.MODE_IGNORED -> "ignored"
                        AppOpsManager.MODE_ERRORED -> "errored"
                        AppOpsManager.MODE_DEFAULT -> "default"
                        else -> "mode_$mode"
                    }

                    val json = buildString {
                        append("""{"package":"${escapeJson(pkg)}",""")
                        append(""""op":"$opName",""")
                        append(""""mode":"$modeStr",""")
                        append(""""last_access_ms":$lastAccess}""")
                    }

                    points.add(DataPoint.json(
                        id, category,
                        "appop:$pkg:$opName",
                        json
                    ))
                }
            }

            points.add(DataPoint.long(id, category, "packages_audited", result.size.toLong()))
            Logger.d(TAG, "Audited ${result.size} packages, ${points.size} ops")

        } catch (e: Exception) {
            Logger.w(TAG, "AppOps audit failed — is GET_APP_OPS_STATS granted?", e)
            points.add(DataPoint.string(id, category, "status", "permission_denied"))
        }

        return points
    }

    private fun opCodeToName(opCode: Int): String = try {
        val method = AppOpsManager::class.java.getMethod("opToName", Int::class.javaPrimitiveType)
        method.invoke(null, opCode) as? String ?: "op_$opCode"
    } catch (_: Exception) { "op_$opCode" }

    /** Map OPSTR_* constants to int op codes via reflection */
    private fun String.opInt(): Int = try {
        val method = AppOpsManager::class.java.getMethod(
            "strOpToOp", String::class.java
        )
        method.invoke(null, this) as? Int ?: -1
    } catch (_: Exception) { -1 }

    private fun tryGetString(obj: Any, method: String): String? = try {
        obj.javaClass.getMethod(method).invoke(obj)?.toString()
    } catch (_: Exception) { null }

    private fun tryGetInt(obj: Any, method: String): Int? = try {
        obj.javaClass.getMethod(method).invoke(obj) as? Int
    } catch (_: Exception) { null }

    private fun tryGetLong(obj: Any, method: String): Long? = try {
        obj.javaClass.getMethod(method).invoke(obj) as? Long
    } catch (_: Exception) { null }

    private fun tryGetList(obj: Any, method: String): List<*>? = try {
        obj.javaClass.getMethod(method).invoke(obj) as? List<*>
    } catch (_: Exception) { null }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "AppOpsAudit"
    }
}
