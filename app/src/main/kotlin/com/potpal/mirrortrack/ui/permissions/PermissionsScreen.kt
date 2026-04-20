package com.potpal.mirrortrack.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.settings.CollectorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class PermissionInfo(
    val permission: String,
    val rationale: String,
    val collectorIds: List<String>
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val registry: CollectorRegistry,
    private val prefs: CollectorPreferences
) : ViewModel() {

    fun getRuntimePermissions(): List<PermissionInfo> {
        val permMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
        registry.all()
            .filter { it.accessTier == AccessTier.RUNTIME && it.requiredPermissions.isNotEmpty() }
            .forEach { collector ->
                collector.requiredPermissions.forEach { perm ->
                    permMap.getOrPut(perm) { mutableListOf() }
                        .add(collector.id to collector.rationale)
                }
            }
        return permMap.map { (perm, collectors) ->
            PermissionInfo(
                permission = perm,
                rationale = collectors.joinToString("; ") { it.second },
                collectorIds = collectors.map { it.first }
            )
        }
    }

    fun isAnyCollectorEnabled(collectorIds: List<String>): Flow<Boolean> {
        if (collectorIds.isEmpty()) return flowOf(false)
        val flows = collectorIds.map { prefs.isEnabled(it) }
        return combine(flows) { values -> values.any { it } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val permissions = remember { viewModel.getRuntimePermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(permissions) { permInfo ->
                PermissionRow(permInfo, viewModel)
            }

            item {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open app settings")
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permInfo: PermissionInfo,
    viewModel: PermissionsViewModel
) {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permInfo.permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val hasEnabledCollector by viewModel.isAnyCollectorEnabled(permInfo.collectorIds)
        .collectAsStateWithLifecycle(initialValue = false)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permInfo.permission.substringAfterLast('.'),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = permInfo.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasEnabledCollector) {
                    Text(
                        text = "No collectors using this are enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (hasEnabledCollector) {
                if (isGranted) {
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(onClick = { launcher.launch(permInfo.permission) }) {
                        Text("Grant")
                    }
                }
            }
        }
    }
}
