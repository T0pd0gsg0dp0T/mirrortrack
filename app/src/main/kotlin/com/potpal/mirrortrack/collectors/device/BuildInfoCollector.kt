package com.potpal.mirrortrack.collectors.device

import android.content.Context
import android.os.Build
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Device build & firmware identifiers. These are what every ad SDK reads in
 * its first seconds to build a device fingerprint. Values are mostly stable
 * across the life of the device — BOOTLOADER and SECURITY_PATCH change with
 * OTAs, the rest are immutable — so a slow poll cadence is fine.
 *
 * No runtime permission required. Safe to default-enable per CLAUDE.md rule #4
 * exception for zero-risk collectors.
 */
@Singleton
class BuildInfoCollector @Inject constructor() : Collector {

    override val id = "build_info"
    override val displayName = "Build & Firmware"
    override val rationale =
        "Reads android.os.Build constants: manufacturer, model, Android version, " +
            "security patch date. No permission required. These are the primary " +
            "signals in device-fingerprint identifiers."
    override val category = Category.DEVICE_IDENTITY
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier = AccessTier.NONE
    override val defaultEnabled = true
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 365.days

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun collect(context: Context): List<DataPoint> {
        return listOf(
            DataPoint.string(id, category, "manufacturer", Build.MANUFACTURER),
            DataPoint.string(id, category, "model", Build.MODEL),
            DataPoint.string(id, category, "brand", Build.BRAND),
            DataPoint.string(id, category, "device", Build.DEVICE),
            DataPoint.string(id, category, "product", Build.PRODUCT),
            DataPoint.string(id, category, "hardware", Build.HARDWARE),
            DataPoint.string(id, category, "board", Build.BOARD),
            DataPoint.string(id, category, "bootloader", Build.BOOTLOADER),
            DataPoint.string(id, category, "fingerprint", Build.FINGERPRINT),
            DataPoint.string(id, category, "host", Build.HOST),
            DataPoint.string(id, category, "tags", Build.TAGS),
            DataPoint.string(id, category, "type", Build.TYPE),
            DataPoint.string(id, category, "user", Build.USER),
            DataPoint.string(id, category, "display", Build.DISPLAY),
            DataPoint.long(id, category, "time", Build.TIME),

            DataPoint.string(id, category, "version_release", Build.VERSION.RELEASE),
            DataPoint.string(id, category, "version_codename", Build.VERSION.CODENAME),
            DataPoint.long(id, category, "version_sdk_int", Build.VERSION.SDK_INT.toLong()),
            DataPoint.string(id, category, "version_security_patch", Build.VERSION.SECURITY_PATCH),
            DataPoint.string(id, category, "version_incremental", Build.VERSION.INCREMENTAL),
            DataPoint.string(id, category, "version_base_os", Build.VERSION.BASE_OS),

            DataPoint.json(id, category, "supported_abis", Build.SUPPORTED_ABIS.joinToString(
                prefix = "[\"", separator = "\",\"", postfix = "\"]"
            ))
        )
    }
}
