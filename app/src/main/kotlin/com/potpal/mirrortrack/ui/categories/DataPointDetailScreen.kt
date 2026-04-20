package com.potpal.mirrortrack.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.entities.DataPointEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
class DataPointDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: DataPointDao
) : ViewModel() {

    private val rowId: Long = savedStateHandle["rowId"] ?: 0L

    private val _point = MutableStateFlow<DataPointEntity?>(null)
    val point: StateFlow<DataPointEntity?> = _point

    init {
        viewModelScope.launch {
            _point.value = dao.byRowId(rowId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPointDetailScreen(
    rowId: Long,
    onBack: () -> Unit,
    viewModel: DataPointDetailViewModel = hiltViewModel()
) {
    val point by viewModel.point.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DataPoint #$rowId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val dp = point
        if (dp == null) {
            Text(
                text = "Loading...",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DetailField("Row ID", dp.rowId.toString())
                DetailField("Timestamp", formatTimestamp(dp.timestamp))
                DetailField("Collector", dp.collectorId)
                DetailField("Category", dp.category)
                DetailField("Key", dp.key)
                DetailField("Value Type", dp.valueType)

                Spacer(Modifier.height(16.dp))
                Text("Value", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatValue(dp.value, dp.valueType),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date(ts))
}

private val prettyJson = Json { prettyPrint = true }

private fun formatValue(value: String, valueType: String): String {
    if (valueType == "JSON") {
        return try {
            val element = prettyJson.parseToJsonElement(value)
            prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        } catch (_: Exception) {
            value
        }
    }
    return value
}
