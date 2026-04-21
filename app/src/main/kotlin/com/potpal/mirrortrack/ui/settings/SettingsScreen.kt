package com.potpal.mirrortrack.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.scheduling.CollectorHealthTracker
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.export.ExportManager
import com.potpal.mirrortrack.export.ImportManager
import com.potpal.mirrortrack.export.TrackerPayloadGenerator
import com.potpal.mirrortrack.scheduling.CollectionForegroundService
import com.potpal.mirrortrack.scheduling.CollectionScheduler
import com.potpal.mirrortrack.settings.CollectorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectorUiState(
    val collector: Collector,
    val enabled: Boolean,
    val available: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val registry: CollectorRegistry,
    private val prefs: CollectorPreferences,
    private val scheduler: CollectionScheduler,
    private val ingestor: Ingestor,
    private val dao: DataPointDao,
    private val databaseHolder: DatabaseHolder,
    private val exportManager: ExportManager,
    private val importManager: ImportManager,
    private val trackerPayloadGenerator: TrackerPayloadGenerator
) : ViewModel() {

    fun getCollectorsByCategory(): Map<Category, List<Collector>> =
        registry.all().groupBy { it.category }

    fun isEnabled(collectorId: String): Flow<Boolean> = prefs.isEnabled(collectorId)

    fun toggleCollector(collectorId: String, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setEnabled(collectorId, enabled)
            scheduler.refreshAll()
            // Backfill: immediately run the collector once when first enabled
            // so insights populate without waiting for the next poll cycle
            if (enabled) {
                val collector = registry.byId(collectorId)
                if (collector != null) {
                    try {
                        if (collector.isAvailable(context)) {
                            val points = collector.collect(context)
                            if (points.isNotEmpty()) {
                                ingestor.submitAll(points)
                                ingestor.flush()
                            }
                        }
                    } catch (_: Exception) {
                        // Best-effort; don't fail the toggle
                    }
                }
            }
        }
    }

    fun getPollIntervalMinutes(collectorId: String): Flow<Int?> =
        prefs.getPollIntervalMinutes(collectorId)

    fun setPollInterval(collectorId: String, minutes: Int) {
        viewModelScope.launch {
            prefs.setPollIntervalMinutes(collectorId, minutes)
            scheduler.refreshAll()
        }
    }

    fun startService() {
        viewModelScope.launch {
            prefs.setServiceEnabled(true)
            CollectionForegroundService.startIfEnabled(context)
            scheduler.refreshAll()
        }
    }

    fun stopService() {
        viewModelScope.launch {
            prefs.setServiceEnabled(false)
            CollectionForegroundService.stop(context)
            scheduler.cancelAll()
        }
    }

    val isServiceEnabled: Flow<Boolean> = prefs.isServiceEnabled()

    fun setPanicPin(pin: String) {
        viewModelScope.launch {
            val salt = java.security.SecureRandom().let { sr ->
                ByteArray(16).also { sr.nextBytes(it) }
            }
            prefs.setPanicPinSalt(salt)
            val chars = pin.toCharArray()
            val key = com.potpal.mirrortrack.data.CryptoManager().deriveKey(context, chars)
            val hash = key.joinToString("") { "%02x".format(it) }
            key.fill(0)
            prefs.setPanicPinHash(hash)
        }
    }

    fun exportDb(uri: Uri) {
        viewModelScope.launch {
            ingestor.flush()
            exportManager.export(uri)
        }
    }

    fun generateTrackerPayload(uri: Uri) {
        viewModelScope.launch {
            trackerPayloadGenerator.generate(uri)
        }
    }

    private val _importResult = MutableStateFlow<ImportManager.ImportResult?>(null)
    val importResult: StateFlow<ImportManager.ImportResult?> = _importResult

    fun importDb(uri: Uri) {
        viewModelScope.launch {
            _importResult.value = importManager.import(uri)
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    // Self-audit data
    suspend fun getSelfAuditData(): SelfAuditData {
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS)
        val declaredPerms = pkgInfo.requestedPermissions?.toList() ?: emptyList()

        val usedPerms = registry.all()
            .filter { prefs.isEnabledSync(it.id) || it.defaultEnabled }
            .flatMap { it.requiredPermissions }
            .toSet()

        // Infrastructure permissions not tied to any collector — exclude from audit
        val infraPerms = setOf(
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE",
            "POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED",
            "INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE",
            "USE_BIOMETRIC", "WAKE_LOCK",
            "BIND_NOTIFICATION_LISTENER_SERVICE",
            "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )
        val unusedPerms = declaredPerms.filter { perm ->
            val shortName = perm.substringAfterLast('.')
            !usedPerms.contains(perm) && shortName !in infraPerms
        }

        val todayMs = System.currentTimeMillis() - 86_400_000
        val writeCounts = dao.countByCollectorSince(todayMs)

        return SelfAuditData(
            declaredPermissions = declaredPerms,
            usedPermissions = usedPerms.toList(),
            unusedPermissions = unusedPerms,
            writeCounts24h = writeCounts.associate { it.collectorId to it.cnt },
            versionName = pkgInfo.versionName ?: "unknown",
            versionCode = pkgInfo.longVersionCode,
            installerSource = pm.getInstallerPackageName(context.packageName) ?: "unknown"
        )
    }
}

data class SelfAuditData(
    val declaredPermissions: List<String>,
    val usedPermissions: List<String>,
    val unusedPermissions: List<String>,
    val writeCounts24h: Map<String, Long>,
    val versionName: String,
    val versionCode: Long,
    val installerSource: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collectorsByCategory = remember { viewModel.getCollectorsByCategory() }
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle(initialValue = false)
    var showPanicPinDialog by remember { mutableStateOf(false) }
    var showPollSlider by remember { mutableStateOf<String?>(null) }
    var showSelfAudit by remember { mutableStateOf(false) }
    var selfAuditData by remember { mutableStateOf<SelfAuditData?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        uri?.let { viewModel.exportDb(it) }
    }

    val trackerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.generateTrackerPayload(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importDb(it) }
    }

    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Top bar with back button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Service toggle
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Collection Service", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isServiceEnabled) "Running" else "Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = {
                            if (it) viewModel.startService() else viewModel.stopService()
                        },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        // Permissions button
        item {
            OutlinedButton(
                onClick = onNavigateToPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Permissions")
            }
        }

        // Collectors by category
        collectorsByCategory.forEach { (category, collectors) ->
            item {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            items(collectors, key = { it.id }) { collector ->
                val enabled by viewModel.isEnabled(collector.id)
                    .collectAsStateWithLifecycle(initialValue = collector.defaultEnabled)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (collector.defaultPollInterval != null) {
                                    showPollSlider = collector.id
                                }
                            }
                        )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = collector.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = collector.rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                if (collector.accessTier == AccessTier.RESTRICTED) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = androidx.compose.ui.Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            "Play Store restricted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                val health = CollectorHealthTracker.allRecords()[collector.id]
                                if (health != null && health.consecutiveFailures > 0) {
                                    Text(
                                        "Failing: ${health.lastError ?: "unknown"} (${health.consecutiveFailures}x)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                // Insight dependency hint
                                val insightCount = com.potpal.mirrortrack.ui.insights.InsightDependencyGraph.cardCountForCollector(collector.id)
                                if (insightCount > 0) {
                                    Text(
                                        "Powers $insightCount insight card${if (insightCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { newEnabled ->
                                    if (newEnabled && collector.accessTier == AccessTier.RUNTIME &&
                                        collector.requiredPermissions.isNotEmpty()) {
                                        onNavigateToPermissions()
                                    }
                                    viewModel.toggleCollector(collector.id, newEnabled)
                                },
                                colors = SwitchDefaults.colors(
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        if (showPollSlider == collector.id && collector.defaultPollInterval != null) {
                            val pollMinutes by viewModel.getPollIntervalMinutes(collector.id)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val currentMinutes = pollMinutes
                                ?: collector.defaultPollInterval!!.inWholeMinutes.toInt()
                            val presets = listOf(15, 60, 360, 1440)
                            val presetIndex = presets.indexOfFirst { it >= currentMinutes }
                                .coerceAtLeast(0)
                            var sliderValue by remember { mutableFloatStateOf(presetIndex.toFloat()) }

                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    "Poll interval: ${formatInterval(presets[sliderValue.toInt()])}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        viewModel.setPollInterval(
                                            collector.id,
                                            presets[sliderValue.toInt()]
                                        )
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export section
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        item {
            OutlinedButton(
                onClick = { exportLauncher.launch("mirrortrack_export.tar.gz") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export encrypted database")
            }
        }

        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/gzip", "application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import backup")
            }
        }

        item {
            OutlinedButton(
                onClick = { trackerLauncher.launch("tracker_payload.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simulate tracker payload")
            }
        }

        // Security section
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Security", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        item {
            OutlinedButton(
                onClick = { showPanicPinDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Set panic PIN")
            }
        }

        // Self-audit section
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        selfAuditData = viewModel.getSelfAuditData()
                        showSelfAudit = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Self-audit")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // Panic PIN dialog
    if (showPanicPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPanicPinDialog = false },
            title = { Text("Set Panic PIN") },
            text = {
                Column {
                    Text(
                        "If you enter this PIN instead of your passphrase, the database will be silently wiped.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Panic PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pin == confirmPin && pin.isNotEmpty()) {
                            viewModel.setPanicPin(pin)
                            showPanicPinDialog = false
                        }
                    }
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showPanicPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import result dialog
    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text(if (importResult is ImportManager.ImportResult.Success) "Import Complete" else "Import Failed") },
            text = {
                Text(
                    when (val r = importResult) {
                        is ImportManager.ImportResult.Success -> "Database restored. The app will close — re-open and enter the original passphrase."
                        is ImportManager.ImportResult.Error -> r.message
                        else -> ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImportResult()
                    if (importResult is ImportManager.ImportResult.Success) {
                        // Kill the process so DB gets re-opened with new file
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }) { Text("OK") }
            }
        )
    }

    // Self-audit dialog
    if (showSelfAudit && selfAuditData != null) {
        val data = selfAuditData!!
        AlertDialog(
            onDismissRequest = { showSelfAudit = false },
            title = { Text("Self-Audit") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Version: ${data.versionName} (${data.versionCode})", style = MaterialTheme.typography.bodySmall)
                    Text("Installer: ${data.installerSource}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))

                    Text("Declared permissions: ${data.declaredPermissions.size}", style = MaterialTheme.typography.labelMedium)
                    Text("Used by enabled collectors: ${data.usedPermissions.size}", style = MaterialTheme.typography.labelMedium)

                    if (data.unusedPermissions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Unused permissions:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        data.unusedPermissions.forEach { perm ->
                            Text(
                                "  ${perm.substringAfterLast('.')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (data.writeCounts24h.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Writes (24h):", style = MaterialTheme.typography.labelMedium)
                        data.writeCounts24h.forEach { (id, count) ->
                            Text("  $id: $count", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelfAudit = false }) { Text("Close") }
            }
        )
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h"
    else -> "${minutes / 1440}d"
}
