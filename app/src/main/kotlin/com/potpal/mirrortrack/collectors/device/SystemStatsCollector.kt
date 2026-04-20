package com.potpal.mirrortrack.collectors.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
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

/**
 * Collects system-level stats: running processes, memory pressure, CPU
 * usage, and thermal state. Uses DUMP permission for enhanced process
 * visibility beyond the app's own UID:
 *
 *   adb shell pm grant <pkg> android.permission.DUMP
 *
 * Without DUMP, getRunningAppProcesses() returns only the calling app.
 * With DUMP, it returns all running processes — giving a complete picture
 * of what's active on the device at any moment.
 *
 * This is the data that device management (MDM) software and OEM analytics
 * packages collect to profile device health and user behavior patterns.
 */
@Singleton
class SystemStatsCollector @Inject constructor() : Collector {
    override val id = "system_stats"
    override val displayName = "System Stats"
    override val rationale =
        "Collects running processes, memory pressure, CPU info, and thermal " +
        "state. With DUMP permission, sees all running apps — not just this one. " +
        "Requires ADB grant: DUMP."
    override val category = Category.DEVICE_IDENTITY
    override val requiredPermissions: List<String> = listOf("android.permission.DUMP")
    override val accessTier = AccessTier.ADB
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 30.minutes
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(
            "android.permission.DUMP"
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()

        val points = mutableListOf<DataPoint>()

        // ── Memory info ──────────────────────────────────────────────
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        points.add(DataPoint.long(id, category, "total_ram_bytes", memInfo.totalMem))
        points.add(DataPoint.long(id, category, "available_ram_bytes", memInfo.availMem))
        points.add(DataPoint.bool(id, category, "low_memory", memInfo.lowMemory))
        points.add(DataPoint.long(id, category, "low_memory_threshold_bytes", memInfo.threshold))

        val usedPct = ((memInfo.totalMem - memInfo.availMem) * 100.0 / memInfo.totalMem)
        points.add(DataPoint.double(id, category, "ram_used_pct", usedPct))

        // ── Running processes ────────────────────────────────────────
        // With DUMP permission, this returns all processes
        val processes = am.runningAppProcesses ?: emptyList()
        points.add(DataPoint.long(id, category, "running_process_count", processes.size.toLong()))

        // Categorize by importance
        val foreground = processes.count {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val visible = processes.count {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
        val service = processes.count {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        }
        val background = processes.count {
            it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        }

        points.add(DataPoint.long(id, category, "foreground_processes", foreground.toLong()))
        points.add(DataPoint.long(id, category, "visible_processes", visible.toLong()))
        points.add(DataPoint.long(id, category, "service_processes", service.toLong()))
        points.add(DataPoint.long(id, category, "background_processes", background.toLong()))

        // Top processes by importance (foreground + visible + service = what's active)
        val activeProcesses = processes
            .filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE }
            .sortedBy { it.importance }
            .take(20)

        for (proc in activeProcesses) {
            val name = proc.processName ?: continue
            val importance = when (proc.importance) {
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                else -> "other"
            }
            points.add(DataPoint.string(id, category, "process:$name", importance))
        }

        // ── Process memory (for our own app) ─────────────────────────
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        val jvmTotal = Runtime.getRuntime().totalMemory()
        val jvmFree = Runtime.getRuntime().freeMemory()

        points.add(DataPoint.long(id, category, "self_native_heap_bytes", nativeHeap))
        points.add(DataPoint.long(id, category, "self_jvm_used_bytes", jvmTotal - jvmFree))

        // ── Thermal status (API 29+) ─────────────────────────────────
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE)
                        as? android.os.PowerManager
                if (pm != null) {
                    val thermal = pm.currentThermalStatus
                    val statusName = when (thermal) {
                        android.os.PowerManager.THERMAL_STATUS_NONE -> "none"
                        android.os.PowerManager.THERMAL_STATUS_LIGHT -> "light"
                        android.os.PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                        android.os.PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                        android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                        android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                        android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                        else -> "unknown"
                    }
                    points.add(DataPoint.string(id, category, "thermal_status", statusName))
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Thermal status unavailable", e)
            }
        }

        // ── CPU info via /proc ───────────────────────────────────────
        try {
            val cpuInfo = java.io.File("/proc/stat").readLines().firstOrNull()
            if (cpuInfo != null && cpuInfo.startsWith("cpu ")) {
                // cpu  user nice system idle iowait irq softirq steal guest guest_nice
                val parts = cpuInfo.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 4) {
                    val total = parts.sum()
                    val idle = parts[3]
                    points.add(DataPoint.long(id, category, "cpu_total_jiffies", total))
                    points.add(DataPoint.long(id, category, "cpu_idle_jiffies", idle))
                }
            }
        } catch (_: Exception) { /* /proc/stat not readable on some devices */ }

        // ── Uptime ───────────────────────────────────────────────────
        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        points.add(DataPoint.long(id, category, "uptime_ms", uptimeMs))

        Logger.d(TAG, "Collected: ${processes.size} procs, RAM ${usedPct.toInt()}%")

        return points
    }

    companion object {
        private const val TAG = "SystemStats"
    }
}
