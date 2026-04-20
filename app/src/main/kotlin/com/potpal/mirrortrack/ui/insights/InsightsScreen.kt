package com.potpal.mirrortrack.ui.insights

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import kotlin.math.pow

// ── Terminal palette ─────────────────────────────────────────────────

private val TerminalGreen = Color(0xFF3FB950)
private val TerminalAmber = Color(0xFFD29922)
private val TerminalRed = Color(0xFFF85149)
private val TerminalBlue = Color(0xFF58A6FF)
private val TerminalPurple = Color(0xFFD2A8FF)
private val DimGray = Color(0xFF484F58)
private val CellEmpty = Color(0xFF21262D)

// ── Root screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, null, tint = TerminalGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Insights", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TerminalGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TerminalGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Analyzing...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Today
                state.today?.let { today ->
                    item(key = "today") { TodayCard(today) }
                }

                // Card 2: Sleep heatmap
                if (state.sleepDays.isNotEmpty()) {
                    item(key = "sleep") {
                        SleepHeatmapCard(
                            days = state.sleepDays,
                            selectedDay = state.selectedSleepDay,
                            onDaySelected = { viewModel.selectSleepDay(it) }
                        )
                    }
                }

                // Card 3: App attention
                if (state.appAttention.isNotEmpty()) {
                    item(key = "apps") { AppAttentionCard(state.appAttention) }
                }

                // Card 4: Anomaly feed
                if (state.anomalies.isNotEmpty()) {
                    item(key = "anomaly_header") {
                        SectionLabel("ANOMALY FEED", Icons.Default.Warning, TerminalAmber)
                    }
                    items(state.anomalies, key = { it.id }) { anomaly ->
                        AnomalyCard(anomaly, onDismiss = { viewModel.dismissAnomaly(anomaly.id) })
                    }
                }

                // Card 5: Location map
                if (state.locationClusters.isNotEmpty()) {
                    item(key = "location") {
                        LocationMapCard(
                            clusters = state.locationClusters,
                            onRename = { id, name -> viewModel.renameCluster(id, name) }
                        )
                    }
                }

                // Card 6: Unlock-after-notification
                if (state.unlockLatencies.isNotEmpty()) {
                    item(key = "unlock") { UnlockLatencyCard(state.unlockLatencies) }
                }

                // Card 7: Fingerprint stability
                if (state.fingerprint.isNotEmpty()) {
                    item(key = "fingerprint") { FingerprintStabilityCard(state.fingerprint) }
                }

                // Card 8: Monthly Trends
                if (state.monthlyTrends.size >= 2) {
                    item(key = "trends") { MonthlyTrendsCard(state.monthlyTrends) }
                }

                // Card 9: Engagement Score
                state.engagement?.let { eng ->
                    item(key = "engagement") { EngagementCard(eng) }
                }

                // Card 9: Privacy Radar
                if (state.privacyRadar.isNotEmpty()) {
                    item(key = "privacy") { PrivacyRadarCard(state.privacyRadar) }
                }

                // Card 10: Data Flow Monitor
                if (state.dataFlow.isNotEmpty()) {
                    item(key = "dataflow") { DataFlowCard(state.dataFlow) }
                }

                // Card 11: App Compulsion Index
                if (state.appCompulsion.isNotEmpty()) {
                    item(key = "compulsion") { AppCompulsionCard(state.appCompulsion) }
                }

                // Card 12: Device Health
                state.deviceHealth?.let { health ->
                    item(key = "health") { DeviceHealthCard(health) }
                }

                // Card 13: Identity Entropy
                state.identityEntropy?.let { entropy ->
                    item(key = "entropy") { IdentityEntropyCard(entropy) }
                }

                // Card 14: Home / Work
                state.homeWork?.let { hw ->
                    item(key = "homework") { HomeWorkCard(hw) }
                }

                // Card 15: Circadian Profile
                state.circadian?.let { circ ->
                    item(key = "circadian") { CircadianCard(circ) }
                }

                // Card 16: Routine Predictability
                state.routine?.let { rout ->
                    item(key = "routine") { RoutineCard(rout) }
                }

                // Card 17: Social Pressure
                if (state.socialPressure.isNotEmpty()) {
                    item(key = "social") { SocialPressureCard(state.socialPressure) }
                }

                // Card 18: App Portfolio
                state.appPortfolio?.let { port ->
                    item(key = "portfolio") { AppPortfolioCard(port) }
                }

                // Card 19: Charging Behavior
                state.charging?.let { chg ->
                    item(key = "charging") { ChargingCard(chg) }
                }

                // Card 20: WiFi Footprint
                state.wifiFootprint?.let { wifi ->
                    item(key = "wifi") { WiFiCard(wifi) }
                }

                // Card 21: Session Fragmentation
                state.sessionFrag?.let { frag ->
                    item(key = "fragmentation") { FragmentationCard(frag) }
                }

                // Card 22: Dwell Times
                if (state.dwellTimes.isNotEmpty()) {
                    item(key = "dwell") { DwellTimeCard(state.dwellTimes) }
                }

                // Card 23: Weekday vs Weekend
                state.weekdayWeekend?.let { wdwe ->
                    item(key = "weekdayweekend") { WeekdayWeekendCard(wdwe) }
                }

                // Card 24: Income Inference
                state.income?.let { inc ->
                    item(key = "income") { IncomeCard(inc) }
                }

                // Card 25: Commute Pattern
                state.commute?.let { com ->
                    if (com.detected) {
                        item(key = "commute") { CommuteCard(com) }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Section label ────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 2.sp
        )
    }
}

// ── Card 1: Today ────────────────────────────────────────────────────

@Composable
private fun TodayCard(data: TodayData) {
    InsightCardShell(title = "Today", icon = Icons.Default.Timeline, accent = TerminalGreen) {
        val stats = listOf(
            Triple(Icons.Default.Storage, "${data.dataPoints}", "data points"),
            Triple(Icons.Default.LockOpen, "${data.unlocks}", "unlocks"),
            Triple(Icons.Default.Timer, formatDuration(data.screenTimeMs), "screen time"),
            Triple(Icons.AutoMirrored.Filled.DirectionsWalk, "${data.steps}", "steps"),
            Triple(Icons.Default.BatteryChargingFull, "${data.batteryDeltaPct}%", "battery \u0394"),
            Triple(Icons.Default.Smartphone, "${data.activeCollectors}", "collectors")
        )

        // 2x3 grid
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0 until 3) {
                    val (icon, value, label) = stats[row * 3 + col]
                    StatCell(icon, value, label)
                }
            }
            if (row == 0) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatCell(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Icon(icon, null, tint = TerminalGreen.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TerminalGreen
        )
        Text(label, fontSize = 10.sp, color = DimGray)
    }
}

// ── Card 2: Sleep heatmap ────────────────────────────────────────────

@Composable
private fun SleepHeatmapCard(
    days: List<SleepDay>,
    selectedDay: SleepDay?,
    onDaySelected: (SleepDay?) -> Unit
) {
    InsightCardShell(title = "Sleep", icon = Icons.Default.Bed, accent = TerminalPurple) {
        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val labelW = size.width / 14f
                        val cellW = (size.width - labelW) / 13f
                        val cellH = size.height / 7f
                        val col = ((offset.x - labelW) / cellW).toInt()
                        val row = (offset.y / cellH).toInt()
                        if (col in 0 until 13 && row in 0 until 7) {
                            val idx = col * 7 + row
                            if (idx in days.indices) {
                                val day = days[idx]
                                onDaySelected(if (selectedDay?.date == day.date) null else day)
                            }
                        }
                    }
                }
        ) {
            val cols = 13
            val rows = 7
            val labelWidth = size.width / 14f
            val cellW = (size.width - labelWidth) / cols
            val cellH = size.height / rows
            val gap = 2f

            // Day labels
            for (r in 0 until rows) {
                if (r % 2 == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        dayLabels[r],
                        labelWidth / 3f,
                        r * cellH + cellH * 0.7f,
                        android.graphics.Paint().apply {
                            color = 0xFF484F58.toInt()
                            textSize = cellH * 0.45f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // Grid cells
            for (c in 0 until cols) {
                for (r in 0 until rows) {
                    val idx = c * 7 + r
                    if (idx >= days.size) continue
                    val day = days[idx]
                    val cellColor = sleepColor(day.sleepDurationHrs)
                    val isSelected = selectedDay?.date == day.date

                    val x = labelWidth + c * cellW + gap
                    val y = r * cellH + gap
                    val w = cellW - gap * 2
                    val h = cellH - gap * 2

                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3f, 3f)
                    )

                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(x - 1, y - 1),
                            size = Size(w + 2, h + 2),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Less", fontSize = 9.sp, color = DimGray)
            Spacer(Modifier.width(4.dp))
            listOf(0.0, 3.0, 5.0, 7.0, 9.0).forEach { hrs ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(sleepColor(hrs))
                )
                Spacer(Modifier.width(2.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("More", fontSize = 9.sp, color = DimGray)
        }

        // Selected day detail
        if (selectedDay != null && selectedDay.sleepDurationHrs > 0) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        selectedDay.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${"%.1f".format(selectedDay.sleepDurationHrs)}h sleep",
                        fontSize = 11.sp,
                        color = TerminalPurple,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (selectedDay.bedtimeHour != null) {
                        Text("Bed ${formatHour(selectedDay.bedtimeHour)}", fontSize = 11.sp, color = DimGray, fontFamily = FontFamily.Monospace)
                    }
                    if (selectedDay.wakeHour != null) {
                        Text("Wake ${formatHour(selectedDay.wakeHour)}", fontSize = 11.sp, color = DimGray, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun sleepColor(hours: Double): Color = when {
    hours <= 0.0 -> CellEmpty
    hours < 4.0 -> TerminalRed.copy(alpha = 0.7f)
    hours < 6.0 -> TerminalAmber.copy(alpha = 0.7f)
    hours < 7.5 -> TerminalBlue.copy(alpha = 0.7f)
    hours < 9.0 -> TerminalGreen.copy(alpha = 0.8f)
    else -> TerminalPurple.copy(alpha = 0.7f)
}

// ── Card 3: App attention ────────────────────────────────────────────

@Composable
private fun AppAttentionCard(apps: List<AppAttention>) {
    InsightCardShell(title = "App Attention (7d)", icon = Icons.Default.Smartphone, accent = TerminalBlue) {
        val maxMs = apps.maxOf { it.foregroundMs7d }.coerceAtLeast(1)

        apps.forEachIndexed { i, app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}.",
                    fontSize = 10.sp,
                    color = DimGray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        simplifyPackage(app.packageName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (app.foregroundMs7d.toFloat() / maxMs).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TerminalBlue.copy(alpha = 0.6f))
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatDuration(app.foregroundMs7d),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalGreen
                    )
                    val delta = app.baselineDeltaMs
                    if (delta != 0L) {
                        val sign = if (delta > 0) "+" else ""
                        val color = if (delta > 0) TerminalAmber else TerminalGreen
                        Text(
                            "$sign${formatDuration(delta)}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

// ── Card 4: Anomaly card ─────────────────────────────────────────────

@Composable
private fun AnomalyCard(anomaly: Anomaly, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TerminalAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = TerminalAmber, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    anomaly.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    anomaly.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    relativeTime(anomaly.timestamp),
                    fontSize = 9.sp,
                    color = DimGray,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = DimGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Card 5: Location map ─────────────────────────────────────────────

@Composable
private fun LocationMapCard(
    clusters: List<LocationCluster>,
    onRename: (String, String) -> Unit
) {
    var renaming by remember { mutableStateOf<LocationCluster?>(null) }

    InsightCardShell(title = "Location Clusters", icon = Icons.Default.LocationOn, accent = TerminalRed) {
        if (clusters.size >= 2) {
            val minLat = clusters.minOf { it.lat }
            val maxLat = clusters.maxOf { it.lat }
            val minLon = clusters.minOf { it.lon }
            val maxLon = clusters.maxOf { it.lon }
            val latRange = (maxLat - minLat).coerceAtLeast(0.001)
            val lonRange = (maxLon - minLon).coerceAtLeast(0.001)
            val maxFix = clusters.maxOf { it.fixCount }.toFloat().coerceAtLeast(1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CellEmpty)
                    .pointerInput(clusters) {
                        detectTapGestures { offset ->
                            val pad = 24f
                            val w = size.width - pad * 2
                            val h = size.height - pad * 2
                            for (c in clusters) {
                                val cx = pad + ((c.lon - minLon) / lonRange * w).toFloat()
                                val cy = pad + ((1.0 - (c.lat - minLat) / latRange) * h).toFloat()
                                val dist = kotlin.math.sqrt(
                                    (offset.x - cx) * (offset.x - cx) +
                                        (offset.y - cy) * (offset.y - cy)
                                )
                                if (dist < 40f) {
                                    renaming = c
                                    break
                                }
                            }
                        }
                    }
            ) {
                val pad = 24f
                val w = size.width - pad * 2
                val h = size.height - pad * 2

                // Grid lines
                for (i in 0..4) {
                    val y = pad + h * i / 4
                    drawLine(DimGray.copy(alpha = 0.2f), Offset(pad, y), Offset(pad + w, y))
                    val x = pad + w * i / 4
                    drawLine(DimGray.copy(alpha = 0.2f), Offset(x, pad), Offset(x, pad + h))
                }

                // Cluster dots
                for (c in clusters) {
                    val cx = pad + ((c.lon - minLon) / lonRange * w).toFloat()
                    val cy = pad + ((1.0 - (c.lat - minLat) / latRange) * h).toFloat()
                    val radius = 6f + (c.fixCount / maxFix) * 14f

                    drawCircle(TerminalGreen.copy(alpha = 0.2f), radius + 4f, Offset(cx, cy))
                    drawCircle(TerminalGreen, radius, Offset(cx, cy))

                    if (c.name != null) {
                        drawContext.canvas.nativeCanvas.drawText(
                            c.name,
                            cx,
                            cy - radius - 4f,
                            android.graphics.Paint().apply {
                                color = 0xFFD2A8FF.toInt()
                                textSize = 28f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Cluster list
        clusters.take(5).forEach { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { renaming = c }
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    c.name ?: "%.4f, %.4f".format(c.lat, c.lon),
                    fontSize = 12.sp,
                    fontFamily = if (c.name == null) FontFamily.Monospace else FontFamily.Default,
                    color = if (c.name != null) TerminalPurple else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${c.fixCount} fixes",
                    fontSize = 10.sp,
                    color = DimGray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (clusters.size > 5) {
            Text("+ ${clusters.size - 5} more", fontSize = 10.sp, color = DimGray)
        }
    }

    // Rename dialog
    if (renaming != null) {
        val cluster = renaming!!
        var name by remember(cluster.id) { mutableStateOf(cluster.name ?: "") }

        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name this place", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "%.4f, %.4f  (%d fixes)".format(cluster.lat, cluster.lon, cluster.fixCount),
                        fontSize = 11.sp,
                        color = DimGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Home, Office") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CellEmpty,
                            unfocusedContainerColor = CellEmpty,
                            focusedIndicatorColor = TerminalGreen
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onRename(cluster.id, name.trim())
                    renaming = null
                }) { Text("Save", color = TerminalGreen) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel", color = DimGray) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// ── Card 6: Unlock-after-notification ────────────────────────────────

@Composable
private fun UnlockLatencyCard(latencies: List<UnlockLatency>) {
    InsightCardShell(
        title = "Unlock After Notification",
        icon = Icons.Default.Notifications,
        accent = TerminalAmber
    ) {
        Text(
            "Median time from notification to screen unlock (7d)",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val maxLatency = latencies.maxOf { it.medianLatencyMs }.toFloat().coerceAtLeast(1f)

        latencies.forEachIndexed { _, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    simplifyPackage(entry.packageName),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.width(60.dp).height(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (entry.medianLatencyMs / maxLatency).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TerminalAmber.copy(alpha = 0.6f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(entry.medianLatencyMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalAmber
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "(${entry.sampleCount})",
                    fontSize = 9.sp,
                    color = DimGray
                )
            }
        }
    }
}

// ── Card 7: Fingerprint stability ────────────────────────────────────

@Composable
private fun FingerprintStabilityCard(fields: List<FingerprintField>) {
    InsightCardShell(title = "Fingerprint Stability", icon = Icons.Default.Fingerprint, accent = TerminalGreen) {
        Text(
            "Device identity fields and when they last changed",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        fields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    field.label,
                    fontSize = 11.sp,
                    color = DimGray,
                    modifier = Modifier.weight(0.35f)
                )
                Column(
                    modifier = Modifier.weight(0.65f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        field.currentValue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (field.lastChangedMs != null) {
                        Text(
                            "changed ${relativeTime(field.lastChangedMs)}",
                            fontSize = 9.sp,
                            color = TerminalAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            "stable",
                            fontSize = 9.sp,
                            color = DimGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ── Card 8: Monthly Trends ──────────────────────────────────────

@Composable
private fun MonthlyTrendsCard(trends: List<MonthlyTrend>) {
    InsightCardShell(title = "Monthly Trends", icon = Icons.Default.Timeline, accent = TerminalBlue) {
        Text(
            "Month-over-month behavioral comparison",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        // Header
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Month", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.22f))
            Text("Unlocks/d", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.22f))
            Text("Screen/d", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.22f))
            Text("Steps", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.22f))
            Text("\u0394", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.12f))
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))

        trends.forEachIndexed { i, trend ->
            val prevTrend = if (i > 0) trends[i - 1] else null
            val unlockDelta = if (prevTrend != null && prevTrend.avgDailyUnlocks > 0) {
                ((trend.avgDailyUnlocks - prevTrend.avgDailyUnlocks) / prevTrend.avgDailyUnlocks * 100).toInt()
            } else null

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    trend.month.takeLast(5),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.22f)
                )
                Text(
                    "${"%.0f".format(trend.avgDailyUnlocks)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalGreen,
                    modifier = Modifier.weight(0.22f)
                )
                Text(
                    formatDuration(trend.avgDailyScreenTimeMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalBlue,
                    modifier = Modifier.weight(0.22f)
                )
                Text(
                    formatLargeNumber(trend.totalSteps),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalPurple,
                    modifier = Modifier.weight(0.22f)
                )
                if (unlockDelta != null) {
                    val sign = if (unlockDelta > 0) "+" else ""
                    val color = if (unlockDelta > 20) TerminalRed else if (unlockDelta < -20) TerminalGreen else DimGray
                    Text(
                        "$sign$unlockDelta%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.weight(0.12f)
                    )
                } else {
                    Text("", modifier = Modifier.weight(0.12f))
                }
            }
        }
    }
}

// ── Card 9: Engagement Score ─────────────────────────────────────

@Composable
private fun EngagementCard(data: EngagementScore) {
    InsightCardShell(title = "Engagement Score", icon = Icons.Default.Speed, accent = TerminalBlue) {
        Text(
            "Firebase-style engagement metrics (7d)",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        // DAU/WAU gauge
        val stickinessLabel = when {
            data.dauWauRatio >= 0.8 -> "Power user"
            data.dauWauRatio >= 0.5 -> "Regular"
            data.dauWauRatio >= 0.3 -> "Casual"
            else -> "Dormant"
        }
        val stickinessColor = when {
            data.dauWauRatio >= 0.8 -> TerminalGreen
            data.dauWauRatio >= 0.5 -> TerminalBlue
            data.dauWauRatio >= 0.3 -> TerminalAmber
            else -> TerminalRed
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "DAU/WAU Stickiness",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stickinessLabel,
                    fontSize = 10.sp,
                    color = stickinessColor,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                "${"%.0f".format(data.dauWauRatio * 100)}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = stickinessColor
            )
        }

        // Stickiness bar
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CellEmpty)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = data.dauWauRatio.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(stickinessColor)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        // Session metrics grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(Icons.Default.LockOpen, "${data.totalSessions7d}", "sessions")
            StatCell(Icons.Default.Timer, "${"%.1f".format(data.avgSessionsPerDay)}/d", "frequency")
            StatCell(Icons.Default.Smartphone, formatDuration(data.avgSessionDurationMs), "avg length")
        }

        Spacer(Modifier.height(8.dp))

        // Retention flags
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RetentionBadge("D7", data.retentionDay7)
            RetentionBadge("D30", data.retentionDay30)
            RetentionBadge("${data.activeDays7d}/7d", data.activeDays7d >= 5)
        }
    }
}

@Composable
private fun RetentionBadge(label: String, active: Boolean) {
    val color = if (active) TerminalGreen else TerminalRed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

// ── Card 9: Privacy Radar ───────────────────────────────────────

@Composable
private fun PrivacyRadarCard(entries: List<PrivacyRadarEntry>) {
    InsightCardShell(title = "Privacy Radar", icon = Icons.Default.Shield, accent = TerminalRed) {
        Text(
            "Per-app privacy invasion score from AppOps audit",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Privacy score badge
                val scoreColor = when {
                    entry.privacyScore >= 70 -> TerminalRed
                    entry.privacyScore >= 40 -> TerminalAmber
                    else -> TerminalGreen
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(scoreColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${entry.privacyScore}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = scoreColor
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        simplifyPackage(entry.packageName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.cameraAccesses > 0) AccessBadge("CAM", entry.cameraAccesses)
                        if (entry.micAccesses > 0) AccessBadge("MIC", entry.micAccesses)
                        if (entry.locationAccesses > 0) AccessBadge("LOC", entry.locationAccesses)
                        if (entry.contactAccesses > 0) AccessBadge("CON", entry.contactAccesses)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessBadge(label: String, count: Int) {
    Text(
        "$label:$count",
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        color = TerminalAmber
    )
}

// ── Card 10: Data Flow Monitor ──────────────────────────────────

@Composable
private fun DataFlowCard(entries: List<DataFlowEntry>) {
    InsightCardShell(title = "Data Flow", icon = Icons.Default.SwapVert, accent = TerminalPurple) {
        Text(
            "Per-app network usage (24h) — flagged if TX/RX > 3x",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val maxBytes = entries.maxOf { it.totalBytes }.toFloat().coerceAtLeast(1f)

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.isSuspicious) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = TerminalRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        simplifyPackage(entry.packageName),
                        fontSize = 12.sp,
                        color = if (entry.isSuspicious) TerminalRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Box(
                            Modifier
                                .weight((entry.rxBytes.toFloat() / maxBytes).coerceIn(0.01f, 1f))
                                .height(3.dp)
                                .background(TerminalBlue.copy(alpha = 0.6f))
                        )
                        Box(
                            Modifier
                                .weight((entry.txBytes.toFloat() / maxBytes).coerceIn(0.01f, 1f))
                                .height(3.dp)
                                .background(TerminalAmber.copy(alpha = 0.6f))
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatBytes(entry.totalBytes),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalGreen
                    )
                    Text(
                        "\u2191${formatBytes(entry.txBytes)} \u2193${formatBytes(entry.rxBytes)}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp, 3.dp).background(TerminalBlue.copy(alpha = 0.6f)))
                Spacer(Modifier.width(4.dp))
                Text("RX", fontSize = 9.sp, color = DimGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp, 3.dp).background(TerminalAmber.copy(alpha = 0.6f)))
                Spacer(Modifier.width(4.dp))
                Text("TX", fontSize = 9.sp, color = DimGray)
            }
        }
    }
}

// ── Card 11: App Compulsion Index ───────────────────────────────

@Composable
private fun AppCompulsionCard(apps: List<AppCompulsion>) {
    InsightCardShell(title = "App Compulsion Index", icon = Icons.Default.Repeat, accent = TerminalAmber) {
        Text(
            "Most-launched apps by frequency (7d logcat)",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val maxLaunches = apps.maxOf { it.launchCount }.toFloat().coerceAtLeast(1f)

        apps.forEachIndexed { i, app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}.",
                    fontSize = 10.sp,
                    color = DimGray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        simplifyPackage(app.packageName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (app.launchCount / maxLaunches).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TerminalAmber.copy(alpha = 0.6f))
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${app.launchCount}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalAmber
                    )
                    Text(
                        "~${"%.0f".format(app.avgGapMinutes)}m gap",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray
                    )
                }
            }
        }
    }
}

// ── Card 12: Device Health ──────────────────────────────────────

@Composable
private fun DeviceHealthCard(health: DeviceHealth) {
    InsightCardShell(title = "Device Health", icon = Icons.Default.Memory, accent = TerminalGreen) {
        // RAM gauge
        val ramColor = when {
            health.ramUsedPct >= 90 -> TerminalRed
            health.ramUsedPct >= 75 -> TerminalAmber
            else -> TerminalGreen
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RAM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${"%.0f".format(health.ramUsedPct)}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ramColor
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CellEmpty)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (health.ramUsedPct / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ramColor)
            )
        }

        if (health.memoryTrend != 0.0) {
            val sign = if (health.memoryTrend > 0) "+" else ""
            Text(
                "$sign${"%.1f".format(health.memoryTrend)}% over 24h",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = if (health.memoryTrend > 5) TerminalAmber else DimGray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(Icons.Default.Smartphone, "${health.processCount}", "processes")
            StatCell(Icons.Default.Storage, "${health.foregroundCount}", "foreground")
            StatCell(Icons.Default.Storage, "${health.backgroundCount}", "background")
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thermalColor = when (health.thermalStatus) {
                    "none" -> TerminalGreen
                    "light" -> TerminalBlue
                    "moderate" -> TerminalAmber
                    else -> TerminalRed
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(thermalColor)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Thermal: ${health.thermalStatus}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = thermalColor
                )
            }

            Text(
                "Up ${"%.1f".format(health.uptimeHours)}h",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DimGray
            )
        }
    }
}

// ── Card 13: Identity Entropy ───────────────────────────────────

@Composable
private fun IdentityEntropyCard(entropy: IdentityEntropy) {
    InsightCardShell(title = "Identity Entropy", icon = Icons.Default.Fingerprint, accent = TerminalPurple) {
        Text(
            "Fingerprint uniqueness quantified in bits of entropy",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        // Total entropy header
        val uniqueness = when {
            entropy.totalBits >= 50 -> "Highly unique"
            entropy.totalBits >= 33 -> "Distinguishable"
            entropy.totalBits >= 18 -> "Somewhat unique"
            else -> "Low entropy"
        }
        val entropyColor = when {
            entropy.totalBits >= 50 -> TerminalRed
            entropy.totalBits >= 33 -> TerminalAmber
            else -> TerminalGreen
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Total Entropy",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    uniqueness,
                    fontSize = 10.sp,
                    color = entropyColor,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                "${"%.1f".format(entropy.totalBits)} bits",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = entropyColor
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "1 in ${formatLargeNumber(2.0.pow(entropy.totalBits).toLong())} devices",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = DimGray
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        // Top contributing fields
        val maxBits = entropy.fields.maxOf { it.entropyBits }.toFloat().coerceAtLeast(1f)

        entropy.fields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    field.name,
                    fontSize = 10.sp,
                    color = DimGray,
                    modifier = Modifier.weight(0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.weight(0.4f).padding(horizontal = 4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (field.entropyBits / maxBits).toFloat().coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TerminalPurple.copy(alpha = 0.6f))
                    )
                }
                Text(
                    "${"%.1f".format(field.entropyBits)}b",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalPurple,
                    modifier = Modifier.weight(0.15f)
                )
                Text(
                    field.value,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.15f)
                )
            }
        }
    }
}

// ── Card 14: Home / Work ────────────────────────────────────────────

@Composable
private fun HomeWorkCard(data: HomeWorkInference) {
    InsightCardShell(title = "Home & Work", icon = Icons.Default.Home, accent = TerminalBlue) {
        Text(
            "Location-based home/work inference from GPS clusters",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Home
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Home, null, tint = TerminalGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                if (data.homeCluster != null) {
                    Text(
                        data.homeCluster.name ?: "%.3f, %.3f".format(data.homeCluster.lat, data.homeCluster.lon),
                        fontSize = 11.sp,
                        fontFamily = if (data.homeCluster.name == null) FontFamily.Monospace else FontFamily.Default,
                        color = TerminalGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${data.homeCluster.fixCount} fixes", fontSize = 9.sp, color = DimGray, fontFamily = FontFamily.Monospace)
                } else {
                    Text("Not detected", fontSize = 11.sp, color = DimGray)
                }
            }

            // Work
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Work, null, tint = TerminalBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                if (data.workCluster != null) {
                    Text(
                        data.workCluster.name ?: "%.3f, %.3f".format(data.workCluster.lat, data.workCluster.lon),
                        fontSize = 11.sp,
                        fontFamily = if (data.workCluster.name == null) FontFamily.Monospace else FontFamily.Default,
                        color = TerminalBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${data.workCluster.fixCount} fixes", fontSize = 9.sp, color = DimGray, fontFamily = FontFamily.Monospace)
                } else {
                    Text("Not detected", fontSize = 11.sp, color = DimGray)
                }
            }
        }

        if (data.commuteDistanceKm != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${"%.1f".format(data.commuteDistanceKm)} km", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TerminalPurple)
                    Text("commute distance", fontSize = 9.sp, color = DimGray)
                }
                if (data.avgCommuteStartHour != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatHour(data.avgCommuteStartHour), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TerminalAmber)
                        Text("depart", fontSize = 9.sp, color = DimGray)
                    }
                }
                if (data.avgCommuteEndHour != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatHour(data.avgCommuteEndHour), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TerminalAmber)
                        Text("return", fontSize = 9.sp, color = DimGray)
                    }
                }
            }
        }
    }
}

// ── Card 15: Circadian Profile ─────────────────────────────────────

@Composable
private fun CircadianCard(data: CircadianProfile) {
    InsightCardShell(title = "Circadian Rhythm", icon = Icons.Default.WbSunny, accent = TerminalAmber) {
        val chronoLabel = when (data.chronotype) {
            "early_bird" -> "Early Bird"
            "night_owl" -> "Night Owl"
            "bimodal" -> "Bimodal"
            "shift_worker" -> "Shift Worker"
            else -> "Balanced"
        }
        val chronoColor = when (data.chronotype) {
            "early_bird" -> TerminalAmber
            "night_owl" -> TerminalPurple
            "bimodal" -> TerminalBlue
            "shift_worker" -> TerminalRed
            else -> TerminalGreen
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Chronotype", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chronoLabel, fontSize = 10.sp, color = chronoColor, fontFamily = FontFamily.Monospace)
            }
            Text(
                "${"%.0f".format(data.activitySpreadHrs)}h span",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = chronoColor
            )
        }

        Spacer(Modifier.height(10.dp))

        // 24-hour histogram
        val maxUnlocks = data.hourlyUnlocks.max().coerceAtLeast(1)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val barW = size.width / 24f
            val maxH = size.height

            for (h in 0 until 24) {
                val fraction = data.hourlyUnlocks[h].toFloat() / maxUnlocks
                val barH = fraction * maxH * 0.9f
                val color = when (h) {
                    data.peakHour -> TerminalGreen
                    data.troughHour -> TerminalRed
                    else -> TerminalBlue.copy(alpha = 0.5f)
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(h * barW + 1f, maxH - barH),
                    size = Size(barW - 2f, barH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        // Hour labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { h ->
                Text(h, fontSize = 8.sp, color = DimGray, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Peak: ${formatHour(data.peakHour.toDouble())}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalGreen)
            Text("Trough: ${formatHour(data.troughHour.toDouble())}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalRed)
        }
    }
}

// ── Card 16: Routine Predictability ────────────────────────────────

@Composable
private fun RoutineCard(data: RoutinePredictability) {
    InsightCardShell(title = "Routine Predictability", icon = Icons.Default.Schedule, accent = TerminalGreen) {
        val pctScore = (data.overallScore * 100).toInt()
        val routineLabel = when {
            pctScore >= 80 -> "Clockwork"
            pctScore >= 60 -> "Structured"
            pctScore >= 40 -> "Flexible"
            pctScore >= 20 -> "Spontaneous"
            else -> "Chaotic"
        }
        val scoreColor = when {
            pctScore >= 60 -> TerminalGreen
            pctScore >= 40 -> TerminalBlue
            pctScore >= 20 -> TerminalAmber
            else -> TerminalRed
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Daily Pattern", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(routineLabel, fontSize = 10.sp, color = scoreColor, fontFamily = FontFamily.Monospace)
            }
            Text(
                "$pctScore%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = scoreColor
            )
        }

        // Score bar
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CellEmpty)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = data.overallScore.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(scoreColor)
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatHour(data.mostPredictableHour.toDouble()), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TerminalGreen)
                Text("most rigid", fontSize = 9.sp, color = DimGray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatHour(data.leastPredictableHour.toDouble()), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TerminalRed)
                Text("most chaotic", fontSize = 9.sp, color = DimGray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.2f".format(data.weekdayVsWeekendShift)}", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TerminalPurple)
                Text("wd/we shift", fontSize = 9.sp, color = DimGray)
            }
        }
    }
}

// ── Card 17: Social Pressure ───────────────────────────────────────

@Composable
private fun SocialPressureCard(entries: List<SocialPressureEntry>) {
    InsightCardShell(title = "Social Pressure", icon = Icons.Default.Notifications, accent = TerminalRed) {
        Text(
            "Apps that trigger the fastest phone pickups",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val maxPressure = entries.maxOf { it.pressureScore }.coerceAtLeast(0.01)

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        simplifyPackage(entry.packageName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (entry.pressureScore / maxPressure).toFloat().coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TerminalRed.copy(alpha = 0.6f))
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${entry.notificationCount} notifs",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalAmber
                    )
                    Text(
                        "${formatDuration(entry.medianResponseMs)} resp",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray
                    )
                    Text(
                        "${"%.0f".format(entry.responseRate * 100)}% rate",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (entry.responseRate > 0.7) TerminalRed else DimGray
                    )
                }
            }
        }
    }
}

// ── Card 18: App Portfolio ─────────────────────────────────────────

@Composable
private fun AppPortfolioCard(data: AppPortfolioProfile) {
    InsightCardShell(title = "App Portfolio", icon = Icons.Default.Apps, accent = TerminalPurple) {
        Text(
            "Installed app analysis and demographic inference",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(Icons.Default.Apps, "${data.totalApps}", "total")
            StatCell(Icons.Default.Smartphone, "${data.userApps}", "user")
            StatCell(Icons.Default.Memory, "${data.systemApps}", "system")
        }

        if (data.categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            val maxCount = data.categories.values.max().coerceAtLeast(1)
            val sorted = data.categories.entries.sortedByDescending { it.value }.take(8)

            sorted.forEach { (cat, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        cat.replace("_", " "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.35f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(modifier = Modifier.weight(0.45f).padding(horizontal = 4.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = (count.toFloat() / maxCount).coerceIn(0.05f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TerminalPurple.copy(alpha = 0.6f))
                        )
                    }
                    Text(
                        "$count",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalPurple,
                        modifier = Modifier.weight(0.2f)
                    )
                }
            }
        }

        if (data.inferences.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(6.dp))
            Text("Inferences", fontSize = 10.sp, color = DimGray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            data.inferences.forEach { inf ->
                Text(
                    "\u2022 ${inf.replace("_", " ")}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalAmber
                )
            }
        }
    }
}

// ── Card 19: Charging Behavior ─────────────────────────────────────

@Composable
private fun ChargingCard(data: ChargingBehavior) {
    InsightCardShell(title = "Charging Behavior", icon = Icons.Default.BatteryChargingFull, accent = TerminalGreen) {
        Text(
            "Battery charging patterns and habits",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${"%.1f".format(data.avgChargesPerDay)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalGreen
                )
                Text("charges/day", fontSize = 9.sp, color = DimGray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${"%.0f".format(data.avgDischargeDepthPct)}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (data.avgDischargeDepthPct < 20) TerminalRed else TerminalAmber
                )
                Text("plug-in level", fontSize = 9.sp, color = DimGray)
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nightColor = if (data.overnightCharger) TerminalGreen else DimGray
                Box(Modifier.size(8.dp).clip(CircleShape).background(nightColor))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (data.overnightCharger) "Overnight charger" else "No overnight",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = nightColor
                )
            }
            Text(
                "Typical: ${formatHour(data.typicalChargeHour.toDouble())}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DimGray
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Avg charge: ${formatDuration(data.avgChargeDurationMs)}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = DimGray
        )
    }
}

// ── Card 20: WiFi Footprint ────────────────────────────────────────

@Composable
private fun WiFiCard(data: WiFiFootprint) {
    InsightCardShell(title = "WiFi Footprint", icon = Icons.Default.Wifi, accent = TerminalBlue) {
        Text(
            "Network mobility and location inference via WiFi",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val mobilityLabel = when {
            data.mobilityScore >= 0.8 -> "Highly mobile"
            data.mobilityScore >= 0.5 -> "Moderately mobile"
            data.mobilityScore >= 0.3 -> "Mostly stationary"
            else -> "Stationary"
        }
        val mobilityColor = when {
            data.mobilityScore >= 0.8 -> TerminalPurple
            data.mobilityScore >= 0.5 -> TerminalBlue
            data.mobilityScore >= 0.3 -> TerminalAmber
            else -> TerminalGreen
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Mobility", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(mobilityLabel, fontSize = 10.sp, color = mobilityColor, fontFamily = FontFamily.Monospace)
            }
            Text(
                "${"%.0f".format(data.mobilityScore * 100)}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = mobilityColor
            )
        }

        // Mobility bar
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CellEmpty)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = data.mobilityScore.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(mobilityColor)
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(Icons.Default.Wifi, "${data.uniqueNetworks7d}", "networks")
            StatCell(Icons.Default.Storage, "${data.totalScans}", "scans")
        }

        if (data.homeNetwork != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Home: ${data.homeNetwork}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TerminalGreen
            )
        }

        if (data.topNetworks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            data.topNetworks.take(5).forEach { (ssid, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(ssid, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$count", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DimGray)
                }
            }
        }
    }
}

// ── Card 21: Session Fragmentation ─────────────────────────────────

@Composable
private fun FragmentationCard(data: SessionFragmentation) {
    InsightCardShell(title = "Session Fragmentation", icon = Icons.Default.DataUsage, accent = TerminalAmber) {
        val attentionLabel = when {
            data.attentionScore >= 0.8 -> "Deep focus"
            data.attentionScore >= 0.6 -> "Moderate focus"
            data.attentionScore >= 0.4 -> "Distracted"
            data.attentionScore >= 0.2 -> "Highly fragmented"
            else -> "ADHD mode"
        }
        val attentionColor = when {
            data.attentionScore >= 0.8 -> TerminalGreen
            data.attentionScore >= 0.6 -> TerminalBlue
            data.attentionScore >= 0.4 -> TerminalAmber
            else -> TerminalRed
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Attention", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(attentionLabel, fontSize = 10.sp, color = attentionColor, fontFamily = FontFamily.Monospace)
            }
            Text(
                "${"%.0f".format(data.attentionScore * 100)}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = attentionColor
            )
        }

        // Attention bar
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CellEmpty)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = data.attentionScore.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(attentionColor)
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.1f".format(data.avgSwitchesPerSession)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TerminalAmber)
                Text("switches/session", fontSize = 9.sp, color = DimGray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(data.avgSessionDepthMs), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TerminalBlue)
                Text("avg depth", fontSize = 9.sp, color = DimGray)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Most focused: ${formatHour(data.leastFragmentedHour.toDouble())}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalGreen)
            Text("Most scattered: ${formatHour(data.mostFragmentedHour.toDouble())}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalRed)
        }
    }
}

// ── Card 22: Dwell Times ───────────────────────────────────────────

@Composable
private fun DwellTimeCard(entries: List<DwellTimeEntry>) {
    InsightCardShell(title = "Dwell Times", icon = Icons.Default.Place, accent = TerminalPurple) {
        Text(
            "Time spent at each location cluster",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val maxDwell = entries.maxOf { it.totalDwellMs }.toFloat().coerceAtLeast(1f)

        entries.forEach { entry ->
            val classColor = when (entry.classification) {
                "home" -> TerminalGreen
                "work" -> TerminalBlue
                "transit" -> TerminalAmber
                "retail" -> TerminalPurple
                "social" -> TerminalRed
                else -> DimGray
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(classColor)
                )
                Spacer(Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            entry.clusterName ?: entry.clusterId.take(8),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            entry.classification,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = classColor
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (entry.totalDwellMs / maxDwell).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(classColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatDuration(entry.totalDwellMs),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = classColor
                    )
                    Text(
                        "${entry.visitCount}x \u00b7 ${formatDuration(entry.avgDwellMs)} avg",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray
                    )
                }
            }
        }
    }
}

// ── Card 23: Weekday vs Weekend ────────────────────────────────────

@Composable
private fun WeekdayWeekendCard(data: WeekdayWeekendDelta) {
    InsightCardShell(title = "Weekday vs Weekend", icon = Icons.Default.CalendarMonth, accent = TerminalBlue) {
        val balanceLabel = when {
            data.balanceScore >= 0.8 -> "Very different"
            data.balanceScore >= 0.5 -> "Noticeably different"
            data.balanceScore >= 0.3 -> "Slightly different"
            else -> "Similar patterns"
        }

        Text(
            "Behavioral differences between workdays and days off",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        // Comparison table header
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.weight(0.3f))
            Text("Weekday", fontSize = 10.sp, color = TerminalBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.35f))
            Text("Weekend", fontSize = 10.sp, color = TerminalPurple, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.35f))
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(4.dp))

        // Unlocks row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Unlocks/d", fontSize = 10.sp, color = DimGray, modifier = Modifier.weight(0.3f))
            Text("${"%.0f".format(data.weekdayAvgUnlocks)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue, modifier = Modifier.weight(0.35f))
            Text("${"%.0f".format(data.weekendAvgUnlocks)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalPurple, modifier = Modifier.weight(0.35f))
        }

        // Screen time row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Screen/d", fontSize = 10.sp, color = DimGray, modifier = Modifier.weight(0.3f))
            Text(formatDuration(data.weekdayAvgScreenMs), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue, modifier = Modifier.weight(0.35f))
            Text(formatDuration(data.weekendAvgScreenMs), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalPurple, modifier = Modifier.weight(0.35f))
        }

        // Top apps
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Top apps", fontSize = 10.sp, color = DimGray, modifier = Modifier.weight(0.3f))
            Column(modifier = Modifier.weight(0.35f)) {
                data.weekdayTopApps.take(3).forEach { app ->
                    Text(simplifyPackage(app), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(modifier = Modifier.weight(0.35f)) {
                data.weekendTopApps.take(3).forEach { app ->
                    Text(simplifyPackage(app), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TerminalPurple, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(balanceLabel, fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace)
            Text(
                "divergence: ${"%.0f".format(data.balanceScore * 100)}%",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TerminalAmber
            )
        }
    }
}

// ── Card 24: Income Inference ──────────────────────────────────────

@Composable
private fun IncomeCard(data: IncomeInference) {
    InsightCardShell(title = "Income Inference", icon = Icons.Default.AttachMoney, accent = TerminalAmber) {
        Text(
            "Socioeconomic signals from device + apps + carrier",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val tierColor = when (data.overallTier) {
            "affluent" -> TerminalGreen
            "high" -> TerminalBlue
            "mid" -> TerminalAmber
            else -> TerminalRed
        }
        val tierLabel = data.overallTier.replaceFirstChar { it.uppercase() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Overall Tier", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Based on cross-signal analysis", fontSize = 9.sp, color = DimGray)
            }
            Text(
                tierLabel,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tierColor
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        // Signal breakdown
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Device", fontSize = 10.sp, color = DimGray)
            Text("${data.deviceTier.replace("_", " ")} (~\$${data.estimatedDevicePrice})", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalGreen)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Carrier", fontSize = 10.sp, color = DimGray)
            Text(data.carrierTier.replace("_", " "), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue)
        }

        if (data.appSignals.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("App Signals", fontSize = 10.sp, color = DimGray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            data.appSignals.forEach { signal ->
                Text(
                    "\u2022 ${signal.replace("_", " ")}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalAmber
                )
            }
        }
    }
}

// ── Card 25: Commute Pattern ───────────────────────────────────────

@Composable
private fun CommuteCard(data: CommutePattern) {
    InsightCardShell(title = "Commute Pattern", icon = Icons.Default.DirectionsCar, accent = TerminalBlue) {
        Text(
            "Daily commute inference from location transitions",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        val modeIcon = when (data.transportMode) {
            "walking" -> Icons.AutoMirrored.Filled.DirectionsWalk
            "driving" -> Icons.Default.DirectionsCar
            else -> Icons.Default.DirectionsCar
        }
        val modeLabel = data.transportMode.replaceFirstChar { it.uppercase() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatHour(data.avgDepartureHour), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TerminalAmber)
                Text("departure", fontSize = 9.sp, color = DimGray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(modeIcon, null, tint = TerminalBlue, modifier = Modifier.size(24.dp))
                Text(modeLabel, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatHour(data.avgReturnHour), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TerminalAmber)
                Text("return", fontSize = 9.sp, color = DimGray)
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${"%.0f".format(data.avgDurationMinutes)}m avg",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalGreen
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val consistColor = if (data.consistencyScore >= 0.7) TerminalGreen else if (data.consistencyScore >= 0.4) TerminalAmber else TerminalRed
                Text(
                    "consistency: ${"%.0f".format(data.consistencyScore * 100)}%",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = consistColor
                )
            }
        }
    }
}

// ── Shared card shell ────────────────────────────────────────────────

@Composable
private fun InsightCardShell(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Utilities ────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val abs = kotlin.math.abs(ms)
    return when {
        abs < 60_000 -> "${abs / 1000}s"
        abs < 3_600_000 -> "${abs / 60_000}m"
        abs < 86_400_000 -> "%.1fh".format(abs / 3_600_000.0)
        else -> "%.1fd".format(abs / 86_400_000.0)
    }
}

private fun formatHour(hour: Double): String {
    val h = hour.toInt() % 24
    val m = ((hour % 1) * 60).toInt()
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
    return "%d:%02d %s".format(h12, m, ampm)
}

private fun simplifyPackage(pkg: String): String {
    val parts = pkg.split(".")
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else pkg
}

private fun formatBytes(bytes: Long): String {
    val abs = kotlin.math.abs(bytes)
    return when {
        abs < 1024 -> "${abs}B"
        abs < 1024 * 1024 -> "${"%.1f".format(abs / 1024.0)}KB"
        abs < 1024 * 1024 * 1024 -> "${"%.1f".format(abs / (1024.0 * 1024))}MB"
        else -> "${"%.1f".format(abs / (1024.0 * 1024 * 1024))}GB"
    }
}

private fun formatLargeNumber(n: Long): String {
    return when {
        n < 1_000 -> "$n"
        n < 1_000_000 -> "${"%.0f".format(n / 1_000.0)}K"
        n < 1_000_000_000 -> "${"%.0f".format(n / 1_000_000.0)}M"
        n < 1_000_000_000_000 -> "${"%.0f".format(n / 1_000_000_000.0)}B"
        else -> "${"%.0f".format(n / 1_000_000_000_000.0)}T"
    }
}

internal fun relativeTime(epochMs: Long): String {
    val delta = System.currentTimeMillis() - epochMs
    return when {
        delta < 60_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else -> "${delta / 86_400_000}d ago"
    }
}
