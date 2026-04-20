package com.potpal.mirrortrack.collectors.device

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.StatFs
import android.view.WindowManager
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class HardwareCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "hardware"
    override val displayName: String = "Hardware Info"
    override val rationale: String =
        "Collects hardware specifications including RAM, storage, CPU, and display characteristics. " +
            "No permissions required."
    override val category: Category = Category.DEVICE_IDENTITY
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier: AccessTier = AccessTier.NONE
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 365.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()

        try {
            // RAM
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            points.add(DataPoint.long(id, category, "total_ram_bytes", memInfo.totalMem))
            points.add(DataPoint.long(id, category, "available_ram_bytes", memInfo.availMem))

            // Internal storage
            val statFs = StatFs(context.filesDir.absolutePath)
            val totalStorage = statFs.blockCountLong * statFs.blockSizeLong
            val availableStorage = statFs.availableBlocksLong * statFs.blockSizeLong
            points.add(DataPoint.long(id, category, "total_storage_bytes", totalStorage))
            points.add(DataPoint.long(id, category, "available_storage_bytes", availableStorage))

            // CPU
            val abiArray = JSONArray(Build.SUPPORTED_ABIS.toList())
            points.add(DataPoint.json(id, category, "cpu_abi_list", abiArray.toString()))
            points.add(DataPoint.long(id, category, "cpu_core_count", Runtime.getRuntime().availableProcessors().toLong()))

            // Display
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = context.resources.displayMetrics
            points.add(DataPoint.long(id, category, "screen_width_px", displayMetrics.widthPixels.toLong()))
            points.add(DataPoint.long(id, category, "screen_height_px", displayMetrics.heightPixels.toLong()))
            points.add(DataPoint.long(id, category, "density_dpi", displayMetrics.densityDpi.toLong()))

            val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: windowManager.defaultDisplay.refreshRate
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.refreshRate
            }
            points.add(DataPoint.double(id, category, "refresh_rate_hz", refreshRate.toDouble()))

            // Accessibility / theme
            points.add(DataPoint.double(id, category, "font_scale", context.resources.configuration.fontScale.toDouble()))

            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val darkMode = nightMode == Configuration.UI_MODE_NIGHT_YES
            points.add(DataPoint.bool(id, category, "dark_mode", darkMode))
        } catch (e: Exception) {
            Logger.e("HardwareCollector", "Error collecting hardware data", e)
        }

        return points
    }
}
