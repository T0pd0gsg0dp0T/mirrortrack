package com.potpal.mirrortrack.collectors

import com.potpal.mirrortrack.collectors.apps.AppOpsAuditCollector
import com.potpal.mirrortrack.collectors.apps.InstalledAppsCollector
import com.potpal.mirrortrack.collectors.apps.NotificationListenerCollector
import com.potpal.mirrortrack.collectors.apps.PrivacyDashboardCollector
import com.potpal.mirrortrack.collectors.apps.UsageStatsCollector
import com.potpal.mirrortrack.collectors.behavioral.AppLifecycleCollector
import com.potpal.mirrortrack.collectors.behavioral.BatteryCollector
import com.potpal.mirrortrack.collectors.behavioral.LogcatCollector
import com.potpal.mirrortrack.collectors.behavioral.ScreenStateCollector
import com.potpal.mirrortrack.collectors.device.BuildInfoCollector
import com.potpal.mirrortrack.collectors.device.HardwareCollector
import com.potpal.mirrortrack.collectors.device.IdentifiersCollector
import com.potpal.mirrortrack.collectors.device.IntegrityCollector
import com.potpal.mirrortrack.collectors.device.SystemStatsCollector
import com.potpal.mirrortrack.collectors.location.ActivityRecognitionCollector
import com.potpal.mirrortrack.collectors.location.LocationCollector
import com.potpal.mirrortrack.collectors.network.BluetoothCollector
import com.potpal.mirrortrack.collectors.network.CarrierCollector
import com.potpal.mirrortrack.collectors.network.ConnectivityCollector
import com.potpal.mirrortrack.collectors.network.IpCollector
import com.potpal.mirrortrack.collectors.network.NetworkUsageCollector
import com.potpal.mirrortrack.collectors.network.WifiCollector
import com.potpal.mirrortrack.collectors.personal.CalendarCollector
import com.potpal.mirrortrack.collectors.personal.ContactsCollector
import com.potpal.mirrortrack.collectors.personal.MediaExifCollector
import com.potpal.mirrortrack.collectors.sensors.BodySensorsCollector
import com.potpal.mirrortrack.collectors.sensors.EnvironmentSensorCollector
import com.potpal.mirrortrack.collectors.sensors.MotionSensorCollector
import com.potpal.mirrortrack.collectors.sensors.StepCollector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for which collectors exist. To add a new
 * collector:
 *   1. Create the Collector implementation.
 *   2. Add it to the list in CollectorRegistryModule.provideCollectors.
 *   3. That's it. UI surfaces it automatically via CategoryBrowser.
 *
 * Rationale for the explicit list (vs. classpath scanning or @IntoSet):
 * explicit is auditable. Grep for "provideCollectors" and you see every
 * data source the app has. With multibinding sets, collectors can sneak
 * in via third-party modules — which is exactly what we don't want in a
 * self-surveillance tool.
 */
@Singleton
class CollectorRegistry @Inject constructor(
    private val collectors: List<@JvmSuppressWildcards Collector>
) {
    fun all(): List<Collector> = collectors
    fun byId(id: String): Collector? = collectors.firstOrNull { it.id == id }
    fun byCategory(category: Category): List<Collector> =
        collectors.filter { it.category == category }
}

@Module
@InstallIn(SingletonComponent::class)
object CollectorRegistryModule {

    @Provides
    @Singleton
    fun provideCollectors(
        // --- APPS ---
        appOpsAudit: AppOpsAuditCollector,
        installedApps: InstalledAppsCollector,
        notificationListener: NotificationListenerCollector,
        privacyDashboard: PrivacyDashboardCollector,
        usageStats: UsageStatsCollector,
        // --- BEHAVIORAL ---
        appLifecycle: AppLifecycleCollector,
        battery: BatteryCollector,
        logcat: LogcatCollector,
        screenState: ScreenStateCollector,
        // --- DEVICE_IDENTITY ---
        buildInfo: BuildInfoCollector,
        hardware: HardwareCollector,
        identifiers: IdentifiersCollector,
        integrity: IntegrityCollector,
        systemStats: SystemStatsCollector,
        // --- LOCATION ---
        activityRecognition: ActivityRecognitionCollector,
        location: LocationCollector,
        // --- NETWORK ---
        bluetooth: BluetoothCollector,
        carrier: CarrierCollector,
        connectivity: ConnectivityCollector,
        ip: IpCollector,
        networkUsage: NetworkUsageCollector,
        wifi: WifiCollector,
        // --- PERSONAL ---
        calendar: CalendarCollector,
        contacts: ContactsCollector,
        mediaExif: MediaExifCollector,
        // --- SENSORS ---
        bodySensors: BodySensorsCollector,
        environmentSensor: EnvironmentSensorCollector,
        motionSensor: MotionSensorCollector,
        stepCollector: StepCollector
    ): List<@JvmSuppressWildcards Collector> = listOf(
        // Apps (5 — includes ADB-level AppOps audit)
        appOpsAudit,
        installedApps,
        notificationListener,
        privacyDashboard,
        usageStats,
        // Behavioral (4 — includes ADB-level logcat)
        appLifecycle,
        battery,
        logcat,
        screenState,
        // Device Identity (5 — includes ADB-level system stats)
        buildInfo,
        hardware,
        identifiers,
        integrity,
        systemStats,
        // Location
        activityRecognition,
        location,
        // Network (6 — includes ADB-level per-app usage)
        bluetooth,
        carrier,
        connectivity,
        ip,
        networkUsage,
        wifi,
        // Personal
        calendar,
        contacts,
        mediaExif,
        // Sensors
        bodySensors,
        environmentSensor,
        motionSensor,
        stepCollector
    )
}
