package com.potpal.mirrortrack.collectors.device

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class IntegrityCollector @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Collector {

    override val id: String = "integrity"
    override val displayName: String = "Device Integrity"
    override val rationale: String =
        "Checks for rooting, emulator, debugger, and developer-mode indicators. No permissions required."
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
            val suPaths = listOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su",
                "/data/local/su", "/su/bin/su"
            )
            val suBinaryPresent = suPaths.any { File(it).exists() }
            points.add(DataPoint.bool(id, category, "su_binary_present", suBinaryPresent))
            points.add(DataPoint.bool(id, category, "rooted_heuristic", suBinaryPresent))

            val testKeys = Build.TAGS?.contains("test-keys") == true
            points.add(DataPoint.bool(id, category, "test_keys", testKeys))

            val emulatorHeuristic = listOf(
                Build.FINGERPRINT.startsWith("generic"),
                Build.FINGERPRINT.startsWith("unknown"),
                Build.HARDWARE.contains("goldfish"),
                Build.HARDWARE.contains("ranchu"),
                Build.PRODUCT.contains("sdk"),
                Build.PRODUCT.contains("emulator"),
                Build.PRODUCT.contains("google_sdk")
            ).any { it }
            points.add(DataPoint.bool(id, category, "emulator_heuristic", emulatorHeuristic))

            points.add(DataPoint.bool(id, category, "debugger_attached", Debug.isDebuggerConnected()))

            val adbEnabled = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            } catch (e: Exception) {
                false
            }
            points.add(DataPoint.bool(id, category, "adb_enabled", adbEnabled))

            val devOptions = try {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                ) == 1
            } catch (e: Exception) {
                false
            }
            points.add(DataPoint.bool(id, category, "developer_options", devOptions))

            points.add(DataPoint.string(id, category, "play_integrity_attestation", "unavailable"))
        } catch (e: Exception) {
            Logger.e("IntegrityCollector", "Error collecting integrity data", e)
        }

        return points
    }
}
