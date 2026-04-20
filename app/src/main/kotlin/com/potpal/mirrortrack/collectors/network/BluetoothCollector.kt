package com.potpal.mirrortrack.collectors.network

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Singleton
class BluetoothCollector @Inject constructor() : Collector {
    override val id = "bluetooth"
    override val displayName = "Bluetooth"
    override val rationale =
        "Records paired devices and performs a 10-second BLE scan for nearby " +
        "device fingerprinting."
    override val category = Category.NETWORK
    override val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 30.minutes
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val btm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter = btm.adapter ?: return false
        if (!adapter.isEnabled) return false
        return requiredPermissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val btm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = btm.adapter ?: return emptyList()

        val points = mutableListOf<DataPoint>()

        // Paired devices
        try {
            val paired = adapter.bondedDevices.orEmpty()
            val json = paired.joinToString(",", "[", "]") { d ->
                """{"name":"${d.name ?: ""}","address":"${d.address ?: ""}","type":${d.type},"bond_state":${d.bondState}}"""
            }
            points.add(DataPoint.json(id, category, "paired_devices", json))
        } catch (e: SecurityException) {
            Logger.e(TAG, "Paired devices denied", e)
        }

        // BLE scan 10s
        val scanner = adapter.bluetoothLeScanner
        if (scanner != null) {
            try {
                val results = mutableListOf<ScanResult>()
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        results.add(result)
                    }
                    override fun onScanFailed(errorCode: Int) {
                        Logger.e(TAG, "BLE scan failed: $errorCode")
                    }
                }

                scanner.startScan(null, ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
                delay(10_000)
                scanner.stopScan(callback)

                val json = results.joinToString(",", "[", "]") { r ->
                    """{"address":"${r.device?.address ?: ""}","name":"${r.device?.name ?: ""}","rssi":${r.rssi}}"""
                }
                points.add(DataPoint.json(id, category, "scan_results", json))
            } catch (e: SecurityException) {
                Logger.e(TAG, "BLE scan denied", e)
            }
        }

        return points
    }

    companion object {
        private const val TAG = "BtCollector"
    }
}
