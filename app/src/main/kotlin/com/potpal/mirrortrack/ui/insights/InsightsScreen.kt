package com.potpal.mirrortrack.ui.insights

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bluetooth
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potpal.mirrortrack.R
import com.potpal.mirrortrack.ui.help.HelpScreen
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.pow

// ── Terminal palette ─────────────────────────────────────────────────

private val TerminalGreen = Color(0xFF3FB950)
private val TerminalAmber = Color(0xFFD29922)
private val TerminalRed = Color(0xFFF85149)
private val TerminalBlue = Color(0xFF58A6FF)
private val TerminalPurple = Color(0xFFD2A8FF)
private val DimGray = Color(0xFF484F58)
private val CellEmpty = Color(0xFF21262D)

private data class CardExpansionCommand(
    val collapsed: Boolean,
    val version: Int
)

private val LocalCardExpansionCommand = staticCompositionLocalOf {
    CardExpansionCommand(collapsed = true, version = 0)
}

private enum class InsightCardGroup(val label: String, val accent: Color) {
    SUMMARY("Summary", TerminalGreen),
    PATTERN("Pattern", TerminalBlue),
    ATTENTION("Attention", TerminalRed),
    CONTEXT("Context", TerminalPurple),
    TECHNICAL("Technical", TerminalAmber)
}

private enum class CardSortField(val label: String) {
    MANUAL("Manual"),
    GROUP("Group"),
    FRESHNESS("Freshness"),
    CONFIDENCE("Confidence"),
    TITLE("Title")
}

// ── Root screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCategoryDetail: (String) -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showSortControls by rememberSaveable { mutableStateOf(false) }
    var collapseAllCards by rememberSaveable { mutableStateOf(true) }
    var expansionCommandVersion by rememberSaveable { mutableStateOf(0) }
    var sortField by rememberSaveable { mutableStateOf(CardSortField.MANUAL) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var draggingCardKey by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableStateOf(0f) }
    val meta = state.cardMeta
    val diag = state.showDiagnostics
    val activeCards = buildList {
        state.engagement?.let { eng ->
            add(ActiveInsightCard("engagement", "Engagement Score", meta["engagement"], InsightCardGroup.SUMMARY) {
                EngagementCard(eng, meta["engagement"], diag)
            })
        }
        state.today?.let { today ->
            add(ActiveInsightCard("today", "Today", meta["today"], InsightCardGroup.SUMMARY) {
                TodayCard(today, meta["today"], diag)
            })
        }
        if (state.sleepDays.isNotEmpty() || state.sleepIntervals72h.isNotEmpty()) {
            add(ActiveInsightCard("sleep", "Sleep", meta["sleep"], InsightCardGroup.SUMMARY) {
                SleepTimelineCard(
                    days = state.sleepDays,
                    intervals = state.sleepIntervals72h,
                    meta = meta["sleep"],
                    showDiagnostics = diag
                )
            })
        }
        state.homeWork?.let { hw ->
            add(ActiveInsightCard("homework", "Home & Work", meta["homework"], InsightCardGroup.PATTERN) {
                HomeWorkCard(hw, meta["homework"], diag)
            })
        }
        state.commute?.takeIf { it.detected }?.let { com ->
            add(ActiveInsightCard("commute", "Commute Pattern", meta["commute"], InsightCardGroup.PATTERN) {
                CommuteCard(com, meta["commute"], diag)
            })
        }
        if (state.locationClusters.isNotEmpty()) {
            add(ActiveInsightCard("location", "Location Clusters", meta["location"], InsightCardGroup.PATTERN) {
                LocationMapCard(
                    clusters = state.locationClusters,
                    onRename = { id, name -> viewModel.renameCluster(id, name) },
                    meta = meta["location"],
                    showDiagnostics = diag
                )
            })
        }
        if (state.dwellTimes.isNotEmpty()) {
            add(ActiveInsightCard("dwell", "Dwell Times", meta["dwell"], InsightCardGroup.PATTERN) {
                DwellTimeCard(state.dwellTimes, meta["dwell"], diag)
            })
        }
        state.circadian?.let { circ ->
            add(ActiveInsightCard("circadian", "Circadian Rhythm", meta["circadian"], InsightCardGroup.PATTERN) {
                CircadianCard(circ, meta["circadian"], diag)
            })
        }
        state.routine?.let { rout ->
            add(ActiveInsightCard("routine", "Routine Predictability", meta["routine"], InsightCardGroup.PATTERN) {
                RoutineCard(rout, meta["routine"], diag)
            })
        }
        state.weekdayWeekend?.let { wdwe ->
            add(ActiveInsightCard("weekdayweekend", "Weekday vs Weekend", meta["weekdayweekend"], InsightCardGroup.PATTERN) {
                WeekdayWeekendCard(wdwe, meta["weekdayweekend"], diag)
            })
        }
        if (state.monthlyTrends.size >= 2) {
            add(ActiveInsightCard("trends", "Monthly Trends", meta["trends"], InsightCardGroup.PATTERN) {
                MonthlyTrendsCard(state.monthlyTrends, meta["trends"], diag)
            })
        }
        if (state.socialPressure.isNotEmpty()) {
            add(ActiveInsightCard("social", "Social Pressure", meta["social"], InsightCardGroup.ATTENTION) {
                SocialPressureCard(state.socialPressure, meta["social"], diag)
            })
        }
        if (state.unlockLatencies.isNotEmpty()) {
            add(ActiveInsightCard("unlock", "Unlock After Notification", meta["unlock"], InsightCardGroup.ATTENTION) {
                UnlockLatencyCard(state.unlockLatencies, meta["unlock"], diag)
            })
        }
        if (state.privacyRadar.isNotEmpty()) {
            add(ActiveInsightCard("privacy", "Privacy Radar", meta["privacy"], InsightCardGroup.ATTENTION) {
                PrivacyRadarCard(state.privacyRadar, meta["privacy"], diag)
            })
        }
        if (state.appCompulsion.isNotEmpty()) {
            add(ActiveInsightCard("compulsion", "App Compulsion Index", meta["compulsion"], InsightCardGroup.ATTENTION) {
                AppCompulsionCard(state.appCompulsion, meta["compulsion"], diag)
            })
        }
        state.sessionFrag?.let { frag ->
            add(ActiveInsightCard("fragmentation", "Session Fragmentation", meta["fragmentation"], InsightCardGroup.ATTENTION) {
                FragmentationCard(frag, meta["fragmentation"], diag)
            })
        }
        state.voiceContext?.let { voice ->
            add(ActiveInsightCard("voice", "Voice Context", meta["voice"], InsightCardGroup.CONTEXT) {
                VoiceContextCard(voice, meta["voice"], diag)
            })
        }
        state.deviceHealth?.let { health ->
            add(ActiveInsightCard("health", "Device Health", meta["health"], InsightCardGroup.CONTEXT) {
                DeviceHealthCard(health, meta["health"], diag)
            })
        }
        state.charging?.let { chg ->
            add(ActiveInsightCard("charging", "Charging Behavior", meta["charging"], InsightCardGroup.CONTEXT) {
                ChargingCard(chg, meta["charging"], diag)
            })
        }
        state.income?.let { inc ->
            add(ActiveInsightCard("income", "Income Inference", meta["income"], InsightCardGroup.CONTEXT) {
                IncomeCard(inc, meta["income"], diag)
            })
        }
        state.wifiFootprint?.let { wifi ->
            add(ActiveInsightCard("wifi", "WiFi Footprint", meta["wifi"], InsightCardGroup.CONTEXT) {
                WiFiCard(wifi, meta["wifi"], diag)
            })
        }
        state.appPortfolio?.let { port ->
            add(ActiveInsightCard("portfolio", "App Portfolio", meta["portfolio"], InsightCardGroup.CONTEXT) {
                AppPortfolioCard(port, meta["portfolio"], diag)
            })
        }
        if (state.appAttention.isNotEmpty()) {
            add(ActiveInsightCard("apps", "App Attention (7d)", meta["apps"], InsightCardGroup.TECHNICAL) {
                AppAttentionCard(state.appAttention, meta["apps"], diag)
            })
        }
        if (state.dataFlow.isNotEmpty()) {
            add(ActiveInsightCard("dataflow", "Data Flow", meta["dataflow"], InsightCardGroup.TECHNICAL) {
                DataFlowCard(state.dataFlow, meta["dataflow"], diag)
            })
        }
        if (state.fingerprint.isNotEmpty()) {
            add(ActiveInsightCard("fingerprint", "Fingerprint Stability", meta["fingerprint"], InsightCardGroup.TECHNICAL) {
                FingerprintStabilityCard(state.fingerprint, meta["fingerprint"], diag)
            })
        }
        state.identityEntropy?.let { entropy ->
            add(ActiveInsightCard("entropy", "Identity Entropy", meta["entropy"], InsightCardGroup.TECHNICAL) {
                IdentityEntropyCard(entropy, meta["entropy"], diag)
            })
        }
        state.travelProfile?.let { tp ->
            add(ActiveInsightCard("travel", "Travel Profile", meta["travel"], InsightCardGroup.PATTERN) {
                TravelProfileCard(tp, meta["travel"], diag)
            })
        }
        state.socialGraph?.let { sg ->
            add(ActiveInsightCard("socialgraph", "Social Graph", meta["social_graph"], InsightCardGroup.CONTEXT) {
                SocialGraphCard(sg, meta["social_graph"], diag)
            })
        }
        state.activityProfile?.let { ap ->
            add(ActiveInsightCard("activityprofile", "Activity Profile", meta["activity_profile"], InsightCardGroup.CONTEXT) {
                ActivityProfileCard(ap, meta["activity_profile"], diag)
            })
        }
        state.heartRate?.let { hr ->
            add(ActiveInsightCard("heartrate", "Heart Rate", meta["heart_rate"], InsightCardGroup.CONTEXT) {
                HeartRateCard(hr, meta["heart_rate"], diag)
            })
        }
        state.bluetoothEcosystem?.let { be ->
            add(ActiveInsightCard("bluetooth", "Bluetooth Ecosystem", meta["bluetooth_eco"], InsightCardGroup.CONTEXT) {
                BluetoothEcosystemCard(be, meta["bluetooth_eco"], diag)
            })
        }
        state.calendarDensity?.let { cd ->
            add(ActiveInsightCard("calendar", "Calendar Density", meta["calendar_density"], InsightCardGroup.PATTERN) {
                CalendarDensityCard(cd, meta["calendar_density"], diag)
            })
        }
        state.photoActivity?.let { pa ->
            add(ActiveInsightCard("photo", "Photo Activity", meta["photo_activity"], InsightCardGroup.PATTERN) {
                PhotoActivityCard(pa, meta["photo_activity"], diag)
            })
        }
        state.notificationStress?.let { ns ->
            add(ActiveInsightCard("notifstress", "Notification Heatmap", meta["notification_stress"], InsightCardGroup.ATTENTION) {
                NotificationStressCard(ns, meta["notification_stress"], diag)
            })
        }
        state.integrityTrust?.let { it_ ->
            add(ActiveInsightCard("integrity", "Integrity Trust", meta["integrity_trust"], InsightCardGroup.TECHNICAL) {
                IntegrityTrustCard(it_, meta["integrity_trust"], diag)
            })
        }
        state.spendingPulse?.let { sp ->
            add(ActiveInsightCard("spending", "Spending Pulse", meta["spending_pulse"], InsightCardGroup.ATTENTION) {
                SpendingPulseCard(sp, meta["spending_pulse"], diag)
            })
        }
        state.communicationDepth?.let { cdepth ->
            add(ActiveInsightCard("commdepth", "Communication Depth", meta["communication_depth"], InsightCardGroup.PATTERN) {
                CommunicationDepthCard(cdepth, meta["communication_depth"], diag)
            })
        }
    }
    val defaultCardOrder = activeCards.map { it.key }
    var localCardOrder by remember(defaultCardOrder, state.cardOrder) {
        mutableStateOf(resolveCardOrder(defaultCardOrder, state.cardOrder))
    }
    val orderedActiveCards = sortActiveCards(
        cards = activeCards,
        manualOrder = localCardOrder,
        sortField = sortField,
        descending = sortDescending
    )
    val canDragCards = sortField == CardSortField.MANUAL

    fun persistCardOrder(order: List<String>) {
        if (order == defaultCardOrder) {
            viewModel.clearInsightCardOrder()
        } else {
            viewModel.saveInsightCardOrder(order)
        }
    }

    fun finishDragging() {
        if (draggingCardKey != null) {
            draggingCardKey = null
            draggingOffsetY = 0f
            persistCardOrder(localCardOrder)
        }
    }

    if (showHelp) {
        HelpScreen(
            onBack = { showHelp = false }
        )
        return
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            viewModel.refresh(showLoading = false)
        }
    }

    CompositionLocalProvider(
        LocalCardExpansionCommand provides CardExpansionCommand(
            collapsed = collapseAllCards,
            version = expansionCommandVersion
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_foreground),
                                contentDescription = "MirrorTrack",
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("MirrorTrack", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                "Methodology",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── COLLECTION SUMMARY ──────────────────────────────
                    item(key = "collection_header") {
                        CollectionHeader(
                            totalDataPoints = state.totalDataPoints,
                            databaseSizeBytes = state.databaseSizeBytes,
                            categories = state.categoryCounts,
                            onCategoryClick = onNavigateToCategoryDetail
                        )
                    }

                    // ── TOOLBAR ROW ─────────────────────────────────────
                    item(key = "toolbar_row") {
                        InsightToolbar(
                            onRefresh = { viewModel.refresh() },
                            collapseAllCards = collapseAllCards,
                            onToggleExpand = {
                                collapseAllCards = !collapseAllCards
                                expansionCommandVersion += 1
                            },
                            showSortControls = showSortControls,
                            onToggleSort = { showSortControls = !showSortControls },
                            sortActive = sortField != CardSortField.MANUAL,
                            showDiagnostics = state.showDiagnostics,
                            onToggleDiagnostics = { viewModel.toggleDiagnostics() }
                        )
                    }

                    if (showSortControls || sortField != CardSortField.MANUAL) {
                        item(key = "sort_controls") {
                            SortControls(
                                activeField = sortField,
                                descending = sortDescending,
                                manualEnabled = canDragCards,
                                onSelect = { field ->
                                    if (field == CardSortField.MANUAL) {
                                        sortField = CardSortField.MANUAL
                                        sortDescending = false
                                    } else if (sortField == field) {
                                        sortDescending = !sortDescending
                                    } else {
                                        sortField = field
                                        sortDescending = when (field) {
                                            CardSortField.FRESHNESS, CardSortField.CONFIDENCE -> true
                                            else -> false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    items(orderedActiveCards, key = { it.key }) { card ->
                        val isDragging = draggingCardKey == card.key
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .pointerInput(card.key, localCardOrder) {
                                    if (!canDragCards) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingCardKey = card.key
                                            draggingOffsetY = 0f
                                        },
                                        onDragCancel = { finishDragging() },
                                        onDragEnd = { finishDragging() },
                                        onDrag = { change, dragAmount ->
                                            if (draggingCardKey != card.key) return@detectDragGesturesAfterLongPress
                                            change.consume()
                                            draggingOffsetY += dragAmount.y

                                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                                            val currentItemInfo = visibleItems.firstOrNull { it.key == card.key }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val draggedCenter = currentItemInfo.offset + draggingOffsetY + currentItemInfo.size / 2f
                                            val targetItemInfo = visibleItems.firstOrNull { itemInfo ->
                                                val itemKey = itemInfo.key as? String ?: return@firstOrNull false
                                                itemKey != card.key &&
                                                    draggedCenter >= itemInfo.offset.toFloat() &&
                                                    draggedCenter <= (itemInfo.offset + itemInfo.size).toFloat()
                                            } ?: return@detectDragGesturesAfterLongPress

                                            val targetKey = targetItemInfo.key as? String ?: return@detectDragGesturesAfterLongPress
                                            val targetIndex = localCardOrder.indexOf(targetKey)
                                            val currentIndex = localCardOrder.indexOf(card.key)
                                            if (targetIndex == -1 || currentIndex == -1 || targetIndex == currentIndex) {
                                                return@detectDragGesturesAfterLongPress
                                            }

                                            draggingOffsetY -= (targetItemInfo.offset - currentItemInfo.offset).toFloat()
                                            localCardOrder = moveCardToIndex(localCardOrder, card.key, targetIndex)
                                        }
                                    )
                                }
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = if (isDragging) draggingOffsetY.toInt() else 0
                                    )
                                }
                        ) {
                            card.content()
                        }
                    }

                    if (state.anomalies.isNotEmpty()) {
                        item(key = "anomaly_header") {
                            SectionLabel("ANOMALY FEED", Icons.Default.Warning, TerminalAmber)
                        }
                        items(state.anomalies, key = { it.id }) { anomaly ->
                            AnomalyCard(anomaly, onDismiss = { viewModel.dismissAnomaly(anomaly.id) })
                        }
                    }

                    val unavailableInsights = unavailableInsightsFor(state)
                    if (unavailableInsights.isNotEmpty()) {
                        item(key = "unavailable_header") {
                            SectionLabel("UNAVAILABLE INSIGHTS", Icons.Default.Info, DimGray)
                        }
                        items(unavailableInsights, key = { "unavailable_${it.title}" }) { insight ->
                            UnavailableInsightCard(insight)
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private data class ActiveInsightCard(
    val key: String,
    val title: String,
    val meta: InsightMeta?,
    val group: InsightCardGroup,
    val content: @Composable () -> Unit
)

private data class UnavailableInsight(
    val title: String,
    val icon: ImageVector,
    val reason: String
)

private fun resolveCardOrder(defaultOrder: List<String>, savedOrder: List<String>): List<String> {
    if (defaultOrder.isEmpty()) return emptyList()
    if (savedOrder.isEmpty()) return defaultOrder

    val seen = mutableSetOf<String>()
    return buildList {
        for (key in savedOrder) {
            if (key in defaultOrder && seen.add(key)) add(key)
        }
        for (key in defaultOrder) {
            if (seen.add(key)) add(key)
        }
    }
}

private fun orderActiveCards(cards: List<ActiveInsightCard>, order: List<String>): List<ActiveInsightCard> {
    if (cards.isEmpty()) return emptyList()
    val resolvedOrder = resolveCardOrder(cards.map { it.key }, order)
    val cardsByKey = cards.associateBy { it.key }
    return resolvedOrder.mapNotNull { cardsByKey[it] }
}

private fun sortActiveCards(
    cards: List<ActiveInsightCard>,
    manualOrder: List<String>,
    sortField: CardSortField,
    descending: Boolean
): List<ActiveInsightCard> {
    val manualCards = orderActiveCards(cards, manualOrder)
    if (sortField == CardSortField.MANUAL) return manualCards

    val confidenceRank: (InsightMeta?) -> Int = { meta ->
        when {
            meta == null -> -1
            meta.isStale -> 0
            meta.confidence == ConfidenceTier.HIGH -> 3
            meta.confidence == ConfidenceTier.MODERATE -> 2
            meta.confidence == ConfidenceTier.LOW -> 1
            else -> -1
        }
    }

    val sorted = when (sortField) {
        CardSortField.MANUAL -> manualCards
        CardSortField.GROUP -> manualCards.sortedWith(
            compareBy<ActiveInsightCard>({ it.group.ordinal }, { it.title.lowercase() })
        )
        CardSortField.FRESHNESS -> manualCards.sortedWith(
            compareBy<ActiveInsightCard>({ it.meta?.newestDataMs ?: Long.MIN_VALUE }, { it.title.lowercase() })
        )
        CardSortField.CONFIDENCE -> manualCards.sortedWith(
            compareBy<ActiveInsightCard>({ confidenceRank(it.meta) }, { it.title.lowercase() })
        )
        CardSortField.TITLE -> manualCards.sortedBy { it.title.lowercase() }
    }

    return if (descending) sorted.reversed() else sorted
}

private fun moveCardToIndex(order: List<String>, key: String, destinationIndex: Int): List<String> {
    val currentIndex = order.indexOf(key)
    if (currentIndex == -1) return order
    val mutable = order.toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(destinationIndex.coerceIn(0, mutable.size), item)
    return mutable
}

private fun unavailableInsightsFor(state: InsightsState): List<UnavailableInsight> = buildList {
    if (state.engagement == null) add(UnavailableInsight("Engagement Score", Icons.Default.Speed, "Waiting for enough opens, sessions, and active days to estimate habit strength."))
    if (state.today == null) add(UnavailableInsight("Today", Icons.Default.Timeline, "Waiting for today's basic activity totals."))
    if (state.sleepDays.isEmpty() && state.sleepIntervals72h.isEmpty()) add(UnavailableInsight("Sleep Timeline", Icons.Default.Bed, "Waiting for long inactive periods, ideally with dark or quiet-room clues."))
    if (state.anomalies.isEmpty()) add(UnavailableInsight("Anomaly Feed", Icons.Default.Warning, "Nothing unusual stands out yet; this appears after behavior breaks your normal pattern."))
    if (state.homeWork == null) add(UnavailableInsight("Home & Work", Icons.Default.Home, "Waiting for repeated places at night and during weekday daytime."))
    if (state.commute?.detected != true) add(UnavailableInsight("Commute Pattern", Icons.Default.DirectionsCar, "Waiting for repeated leave-and-return timing between common places."))
    if (state.locationClusters.isEmpty()) add(UnavailableInsight("Location Clusters", Icons.Default.LocationOn, "Waiting for enough location points to group recurring places."))
    if (state.dwellTimes.isEmpty()) add(UnavailableInsight("Dwell Times", Icons.Default.Place, "Waiting to see how long the device stays at recurring places."))
    if (state.circadian == null) add(UnavailableInsight("Circadian Rhythm", Icons.Default.WbSunny, "Waiting for enough activity across morning, day, evening, and night."))
    if (state.routine == null) add(UnavailableInsight("Routine Predictability", Icons.Default.Schedule, "Waiting for repeated daily timing patterns."))
    if (state.weekdayWeekend == null) add(UnavailableInsight("Weekday vs Weekend", Icons.Default.CalendarMonth, "Waiting for both workweek and weekend behavior."))
    if (state.monthlyTrends.size < 2) add(UnavailableInsight("Monthly Trends", Icons.Default.Timeline, "Waiting for at least two calendar months to compare."))
    if (state.socialPressure.isEmpty()) add(UnavailableInsight("Social Pressure", Icons.Default.Notifications, "Waiting to see whether notifications reliably pull you back to the phone."))
    if (state.unlockLatencies.isEmpty()) add(UnavailableInsight("Unlock After Notification", Icons.Default.Notifications, "Waiting for notifications followed by unlocks close enough to connect them."))
    if (state.privacyRadar.isEmpty()) add(UnavailableInsight("Privacy Radar", Icons.Default.Shield, "Waiting for evidence that apps used sensitive surfaces like camera, mic, location, or contacts."))
    if (state.appCompulsion.isEmpty()) add(UnavailableInsight("App Compulsion Index", Icons.Default.Repeat, "Waiting for repeated app opens that suggest checking loops."))
    if (state.sessionFrag == null) add(UnavailableInsight("Session Fragmentation", Icons.Default.DataUsage, "Waiting for enough app switching to estimate attention fragmentation."))
    if (state.voiceContext == null) add(UnavailableInsight("Voice Context", Icons.Default.Mic, "Waiting for local speech-window summaries or setup status."))
    if (state.deviceHealth == null) add(UnavailableInsight("Device Health", Icons.Default.Memory, "Waiting for system or battery readings that explain device condition."))
    if (state.charging == null) add(UnavailableInsight("Charging Behavior", Icons.Default.BatteryChargingFull, "Waiting for charge and discharge cycles."))
    if (state.income == null) add(UnavailableInsight("Income Inference", Icons.Default.AttachMoney, "Waiting for enough device, carrier, and app clues to show how crude income profiling works."))
    if (state.wifiFootprint == null) add(UnavailableInsight("WiFi Footprint", Icons.Default.Wifi, "Waiting for Wi-Fi history that can act like a location trail."))
    if (state.appPortfolio == null) add(UnavailableInsight("App Portfolio", Icons.Default.Apps, "Waiting for installed-app inventory to reveal interest and lifestyle categories."))
    if (state.appAttention.isEmpty()) add(UnavailableInsight("App Attention (7d)", Icons.Default.Smartphone, "Waiting for app foreground time: which apps actually held your attention."))
    if (state.dataFlow.isEmpty()) add(UnavailableInsight("Data Flow", Icons.Default.SwapVert, "Waiting for per-app network traffic totals."))
    if (state.fingerprint.isEmpty()) add(UnavailableInsight("Fingerprint Stability", Icons.Default.Fingerprint, "Waiting for device identity fields to compare over time."))
    if (state.identityEntropy == null) add(UnavailableInsight("Identity Entropy", Icons.Default.Fingerprint, "Waiting for enough device traits to show how uniqueness is estimated."))
    if (state.travelProfile == null) add(UnavailableInsight("Travel Profile", Icons.Default.Public, "Waiting for roaming, public-IP, location, or photo-location clues that indicate travel."))
    if (state.socialGraph == null) add(UnavailableInsight("Social Graph", Icons.Default.Forum, "Waiting for contacts, notifications, calendar, call, or SMS metadata to estimate relationship breadth."))
    if (state.activityProfile == null) add(UnavailableInsight("Activity Profile", Icons.Default.DirectionsCar, "Waiting for activity-recognition samples that classify still, walking, running, or vehicle movement."))
    if (state.heartRate == null) add(UnavailableInsight("Heart Rate", Icons.Default.Favorite, "Waiting for BODY_SENSORS heart-rate samples from a supported sensor or wearable."))
    if (state.bluetoothEcosystem == null) add(UnavailableInsight("Bluetooth Ecosystem", Icons.Default.Bluetooth, "Waiting for paired-device or nearby BLE scan summaries."))
    if (state.calendarDensity == null) add(UnavailableInsight("Calendar Density", Icons.Default.CalendarMonth, "Waiting for calendar events to estimate schedule density and meeting fragmentation."))
    if (state.photoActivity == null) add(UnavailableInsight("Photo Activity", Icons.Default.PhotoCamera, "Waiting for local photo EXIF metadata; no image pixels are inspected."))
    if (state.notificationStress == null) add(UnavailableInsight("Notification Heatmap", Icons.Default.Notifications, "Waiting for notification-listener events across enough hours to build an interruption heatmap."))
    if (state.integrityTrust == null) add(UnavailableInsight("Integrity Trust", Icons.Default.Shield, "Waiting for device integrity readings such as root, ADB, debugger, and emulator indicators."))
    if (state.spendingPulse == null) add(UnavailableInsight("Spending Pulse", Icons.Default.AttachMoney, "Waiting for bank-alert, OTP, transaction, or finance-notification metadata."))
    if (state.communicationDepth == null) add(UnavailableInsight("Communication Depth", Icons.Default.Forum, "Waiting for call and SMS aggregates to estimate communication style."))
}

@Composable
private fun UnavailableInsightCard(insight: UnavailableInsight) {
    val expansionCommand = LocalCardExpansionCommand.current
    val accent = when (insight.title) {
        "Anomaly Feed" -> TerminalAmber
        else -> insightAccentForTitle(insight.title)
    }
    var collapsed by rememberSaveable("${insight.title}:unavailable:collapsed") { mutableStateOf(true) }
    LaunchedEffect(expansionCommand.version) {
        collapsed = expansionCommand.collapsed
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(insight.icon, null, tint = accent.copy(alpha = 0.82f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (!collapsed) {
                    Text(
                        insight.reason,
                        fontSize = 11.sp,
                        color = DimGray
                    )
                }
            }
            if (!collapsed) {
                Text(
                    "WAITING",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.82f),
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    if (collapsed) "Expand ${insight.title}" else "Collapse ${insight.title}",
                    tint = DimGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Collection header ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionHeader(
    totalDataPoints: Long,
    databaseSizeBytes: Long,
    categories: List<CategoryCount>,
    onCategoryClick: (String) -> Unit
) {
    Column {
        Text(
            "COLLECTED DATA",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TerminalGreen,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatExactNumber(totalDataPoints)} data points across ${categories.count { it.count > 0 }} categories" +
                if (databaseSizeBytes > 0) " \u2022 ${formatDatabaseSize(databaseSizeBytes)}" else "",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (cat in categories) {
                CategoryChip(cat, onClick = { onCategoryClick(cat.name) })
            }
        }
    }
}

@Composable
private fun CategoryChip(cat: CategoryCount, onClick: () -> Unit) {
    val icon = when (cat.icon) {
        "DEVICE_IDENTITY" -> Icons.Default.DeviceHub
        "NETWORK" -> Icons.Default.NetworkCheck
        "LOCATION" -> Icons.Default.LocationOn
        "SENSORS" -> Icons.Default.Sensors
        "BEHAVIORAL" -> Icons.Default.Timeline
        "PERSONAL" -> Icons.Default.Person
        "APPS" -> Icons.Default.Apps
        else -> Icons.Default.DeviceHub
    }
    val hasData = cat.count > 0

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasData) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                tint = if (hasData) TerminalGreen else DimGray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    cat.displayName,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (hasData) MaterialTheme.colorScheme.onSurface else DimGray,
                    maxLines = 1
                )
                Text(
                    if (hasData) formatExactNumber(cat.count) else "---",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (hasData) TerminalGreen else DimGray
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortControls(
    activeField: CardSortField,
    descending: Boolean,
    manualEnabled: Boolean,
    onSelect: (CardSortField) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SORT CARDS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TerminalBlue,
                    letterSpacing = 1.sp
                )
                Text(
                    if (activeField == CardSortField.MANUAL) "manual order"
                    else "${activeField.label.lowercase()} ${if (descending) "desc" else "asc"}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DimGray
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CardSortField.entries.forEach { field ->
                    SortChip(
                        label = buildString {
                            append(field.label)
                            if (field == activeField && field != CardSortField.MANUAL) {
                                append(' ')
                                append(if (descending) "↓" else "↑")
                            }
                        },
                        active = field == activeField,
                        onClick = { onSelect(field) }
                    )
                }
            }
            if (!manualEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the active sort again to reverse it. Long-press drag works only in Manual mode.",
                    fontSize = 10.sp,
                    color = DimGray
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap a sort to auto-order cards. Tap it again to reverse.",
                    fontSize = 10.sp,
                    color = DimGray
                )
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) TerminalBlue.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) TerminalBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Toolbar row ────────────────────────────────────────────────────

@Composable
private fun InsightToolbar(
    onRefresh: () -> Unit,
    collapseAllCards: Boolean,
    onToggleExpand: () -> Unit,
    showSortControls: Boolean,
    onToggleSort: () -> Unit,
    sortActive: Boolean,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: refresh
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Refresh, "Refresh", tint = TerminalGreen, modifier = Modifier.size(20.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Diagnostics
            IconButton(onClick = onToggleDiagnostics, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Speed,
                    "Toggle diagnostics",
                    tint = if (showDiagnostics) TerminalAmber else DimGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            // Sort (next to expand)
            IconButton(onClick = onToggleSort, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    "Sort cards",
                    tint = if (showSortControls || sortActive) TerminalBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            // Expand / Collapse all (farthest right)
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (collapseAllCards) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    if (collapseAllCards) "Expand all cards" else "Collapse all cards",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun insightGroupForTitle(title: String): InsightCardGroup = when (title) {
    "Engagement Score", "Today", "Sleep", "Sleep Timeline" -> InsightCardGroup.SUMMARY
    "Home & Work", "Commute Pattern", "Location Clusters", "Dwell Times",
    "Circadian Rhythm", "Routine Predictability", "Weekday vs Weekend", "Monthly Trends",
    "Travel Profile", "Calendar Density", "Photo Activity", "Communication Depth" ->
        InsightCardGroup.PATTERN
    "Social Pressure", "Unlock After Notification", "Privacy Radar",
    "App Compulsion Index", "Session Fragmentation",
    "Notification Heatmap", "Spending Pulse" ->
        InsightCardGroup.ATTENTION
    "Voice Context", "Device Health", "Charging Behavior", "Income Inference",
    "WiFi Footprint", "App Portfolio",
    "Social Graph", "Activity Profile", "Heart Rate", "Bluetooth Ecosystem" ->
        InsightCardGroup.CONTEXT
    "App Attention (7d)", "Data Flow", "Fingerprint Stability", "Identity Entropy",
    "Integrity Trust" ->
        InsightCardGroup.TECHNICAL
    else -> InsightCardGroup.SUMMARY
}

private fun insightAccentForTitle(title: String): Color = insightGroupForTitle(title).accent

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
private fun TodayCard(data: TodayData, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "data_points" to "${data.dataPoints}",
        "unlocks" to "${data.unlocks}",
        "screen_time_ms" to "${data.screenTimeMs}",
        "steps" to "${data.steps}",
        "battery_delta_pct" to "${data.batteryDeltaPct}",
        "active_collectors" to "${data.activeCollectors}"
    )
    InsightCardShell(title = "Today", icon = Icons.Default.Timeline, accent = insightAccentForTitle("Today"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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

// ── Card 2: Sleep timeline ───────────────────────────────────────────

@Composable
private fun SleepTimelineCard(
    days: List<SleepDay>,
    intervals: List<SleepInterval>,
    meta: InsightMeta? = null,
    showDiagnostics: Boolean = false
) {
    val totalSleepMsAll = intervals.sumOf { it.durationMs }
    val avgConfidence = intervals.takeIf { it.isNotEmpty() }?.map { it.confidence }?.average()
    val avgLux = intervals.mapNotNull { it.averageLux }.takeIf { it.isNotEmpty() }?.average()
    val avgSnd = intervals.mapNotNull { it.averageSoundDbfs }.takeIf { it.isNotEmpty() }?.average()
    val sleepSnapshot = listOf(
        "intervals_72h" to "${intervals.size}",
        "total_sleep_ms" to "$totalSleepMsAll",
        "sleep_days_recorded" to "${days.size}",
        "avg_confidence" to (avgConfidence?.let { "%.2f".format(it) } ?: "n/a"),
        "avg_ambient_lux" to (avgLux?.let { "%.1f".format(it) } ?: "n/a"),
        "avg_ambient_dbfs" to (avgSnd?.let { "%.1f".format(it) } ?: "n/a"),
        "evidence_signals" to (intervals.flatMap { it.evidence }.distinct().joinToString(",").ifEmpty { "n/a" })
    )
    InsightCardShell(title = "Sleep", icon = Icons.Default.Bed, accent = insightAccentForTitle("Sleep"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = sleepSnapshot) {
        val now = remember(intervals) { System.currentTimeMillis() }
        val windowMs = 72 * 3_600_000L
        val windowStart = now - windowMs
        val totalSleepMs = intervals.sumOf { it.durationMs }
        val recentAverage = days
            .takeLast(7)
            .filter { it.sleepDurationHrs > 0.0 }
            .map { it.sleepDurationHrs }
            .average()
            .takeIf { !it.isNaN() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Last 72 hours", fontSize = 10.sp, color = DimGray, fontFamily = FontFamily.Monospace)
                Text(
                    formatDuration(totalSleepMs),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalPurple
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${intervals.size} block${if (intervals.size == 1) "" else "s"}", fontSize = 10.sp, color = DimGray)
                recentAverage?.let {
                    Text(
                        "7d avg ${"%.1f".format(it)}h",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            val trackTop = size.height * 0.28f
            val trackHeight = size.height * 0.38f
            drawRoundRect(
                color = CellEmpty,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(10f, 10f)
            )

            for (i in 0..3) {
                val x = size.width * (i / 3f)
                drawLine(
                    color = DimGray.copy(alpha = if (i == 3) 0.65f else 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }

            intervals.forEach { interval ->
                val startFraction = ((interval.startMs - windowStart).toDouble() / windowMs).coerceIn(0.0, 1.0)
                val endFraction = ((interval.endMs - windowStart).toDouble() / windowMs).coerceIn(0.0, 1.0)
                val x1 = (startFraction * size.width).toFloat()
                val x2 = (endFraction * size.width).toFloat()
                drawRoundRect(
                    color = sleepConfidenceColor(interval.confidence),
                    topLeft = Offset(x1, trackTop),
                    size = Size((x2 - x1).coerceAtLeast(3f), trackHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(0L, 24L, 48L, 72L).forEach { hours ->
                Text(
                    formatTimelineLabel(windowStart + hours * 3_600_000L),
                    fontSize = 9.sp,
                    color = DimGray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (intervals.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))
            Text(
                "No likely sleep blocks found in the last 72 hours.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(10.dp))
            intervals.takeLast(4).forEach { interval ->
                SleepIntervalRow(interval)
            }
            if (intervals.size > 4) {
                Text(
                    "+${intervals.size - 4} earlier block${if (intervals.size - 4 == 1) "" else "s"}",
                    fontSize = 10.sp,
                    color = DimGray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SleepIntervalRow(interval: SleepInterval) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 8.dp, height = 28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(sleepConfidenceColor(interval.confidence))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${formatTimelineTime(interval.startMs)} - ${formatTimelineTime(interval.endMs)}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                interval.evidence.joinToString(" + "),
                fontSize = 9.sp,
                color = DimGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDuration(interval.durationMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TerminalPurple
            )
            Text(
                "${(interval.confidence * 100).toInt()}%",
                fontSize = 9.sp,
                color = sleepConfidenceColor(interval.confidence),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun sleepConfidenceColor(confidence: Double): Color = when {
    confidence >= 0.8 -> TerminalPurple.copy(alpha = 0.9f)
    confidence >= 0.6 -> TerminalBlue.copy(alpha = 0.85f)
    else -> TerminalAmber.copy(alpha = 0.85f)
}

private fun formatTimelineLabel(timestampMs: Long): String =
    Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE ha"))

private fun formatTimelineTime(timestampMs: Long): String =
    Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE h:mm a"))

// ── Card 3: App attention ────────────────────────────────────────────

@Composable
private fun AppAttentionCard(apps: List<AppAttention>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_tracked" to "${apps.size}")
        add("total_fg_ms_7d" to "${apps.sumOf { it.foregroundMs7d }}")
        apps.take(5).forEach { app ->
            add(simplifyPackage(app.packageName) to "fg=${app.foregroundMs7d}ms d=${app.baselineDeltaMs}")
        }
    }
    InsightCardShell(title = "App Attention (7d)", icon = Icons.Default.Smartphone, accent = insightAccentForTitle("App Attention (7d)"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
    val expansionCommand = LocalCardExpansionCommand.current
    var infoExpanded by rememberSaveable(anomaly.id) { mutableStateOf(false) }
    var dataExpanded by rememberSaveable("${anomaly.id}:data") { mutableStateOf(false) }
    var collapsed by rememberSaveable("${anomaly.id}:collapsed") { mutableStateOf(true) }
    val anomalySnapshot = listOf(
        "id" to anomaly.id,
        "title" to anomaly.title,
        "timestamp_ms" to "${anomaly.timestamp}",
        "age" to relativeTime(anomaly.timestamp),
        "description" to anomaly.description
    )
    LaunchedEffect(expansionCommand.version) {
        collapsed = expansionCommand.collapsed
    }
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
            IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    if (collapsed) "Expand anomaly card" else "Collapse anomaly card",
                    tint = DimGray,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    anomaly.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!collapsed) {
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
                    if (infoExpanded) {
                        EducationalInfoPanel(
                            text = educationalInfoForTitle("Anomaly Feed"),
                            whyMatters = whyMattersForTitle("Anomaly Feed"),
                            algorithm = algorithmForTitle("Anomaly Feed"),
                            accent = TerminalAmber,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (dataExpanded) {
                        DataSnapshotPanel(
                            rows = anomalySnapshot,
                            accent = TerminalAmber,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    dataExpanded = !dataExpanded
                    if (dataExpanded) collapsed = false
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    "Show data for anomaly",
                    tint = if (dataExpanded) TerminalBlue else DimGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = {
                    infoExpanded = !infoExpanded
                    if (infoExpanded) collapsed = false
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    "Explain anomaly card",
                    tint = if (infoExpanded) TerminalAmber else DimGray,
                    modifier = Modifier.size(16.dp)
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
    onRename: (String, String) -> Unit,
    meta: InsightMeta? = null,
    showDiagnostics: Boolean = false
) {
    var renaming by remember { mutableStateOf<LocationCluster?>(null) }

    val locationSnapshot = buildList {
        add("clusters" to "${clusters.size}")
        add("total_fixes" to "${clusters.sumOf { it.fixCount }}")
        clusters.take(5).forEach { c ->
            add((c.name ?: c.id) to "lat=${"%.4f".format(c.lat)} lon=${"%.4f".format(c.lon)} fixes=${c.fixCount}")
        }
    }
    InsightCardShell(title = "Location Clusters", icon = Icons.Default.LocationOn, accent = insightAccentForTitle("Location Clusters"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = locationSnapshot) {
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
private fun UnlockLatencyCard(latencies: List<UnlockLatency>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_with_responses" to "${latencies.size}")
        add("total_samples" to "${latencies.sumOf { it.sampleCount }}")
        latencies.take(5).forEach { e ->
            add(simplifyPackage(e.packageName) to "median=${e.medianLatencyMs}ms n=${e.sampleCount}")
        }
    }
    InsightCardShell(
        title = "Unlock After Notification",
        icon = Icons.Default.Notifications,
        accent = insightAccentForTitle("Unlock After Notification"),
        meta = meta,
        showDiagnostics = showDiagnostics,
        dataSnapshot = snapshot
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
private fun FingerprintStabilityCard(fields: List<FingerprintField>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("fields_tracked" to "${fields.size}")
        add("recently_changed" to "${fields.count { it.lastChangedMs != null }}")
        fields.take(8).forEach { f ->
            add(f.label to f.currentValue)
        }
    }
    InsightCardShell(title = "Fingerprint Stability", icon = Icons.Default.Fingerprint, accent = insightAccentForTitle("Fingerprint Stability"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun MonthlyTrendsCard(trends: List<MonthlyTrend>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("months" to "${trends.size}")
        trends.takeLast(6).forEach { t ->
            add(t.month to "unl/d=${"%.1f".format(t.avgDailyUnlocks)} steps=${t.totalSteps}")
        }
    }
    InsightCardShell(title = "Monthly Trends", icon = Icons.Default.Timeline, accent = insightAccentForTitle("Monthly Trends"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun EngagementCard(data: EngagementScore, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "dau_wau_ratio" to "%.3f".format(data.dauWauRatio),
        "avg_sessions_per_day" to "%.2f".format(data.avgSessionsPerDay),
        "avg_session_duration_ms" to "${data.avgSessionDurationMs}",
        "total_sessions_7d" to "${data.totalSessions7d}",
        "active_days_7d" to "${data.activeDays7d}",
        "retention_day_7" to "${data.retentionDay7}",
        "retention_day_30" to "${data.retentionDay30}"
    )
    InsightCardShell(title = "Engagement Score", icon = Icons.Default.Speed, accent = insightAccentForTitle("Engagement Score"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun PrivacyRadarCard(entries: List<PrivacyRadarEntry>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_with_access" to "${entries.size}")
        add("camera_total" to "${entries.sumOf { it.cameraAccesses }}")
        add("mic_total" to "${entries.sumOf { it.micAccesses }}")
        add("location_total" to "${entries.sumOf { it.locationAccesses }}")
        add("contact_total" to "${entries.sumOf { it.contactAccesses }}")
        entries.take(5).forEach { e ->
            add(simplifyPackage(e.packageName) to "score=${e.privacyScore} cam=${e.cameraAccesses} mic=${e.micAccesses} loc=${e.locationAccesses}")
        }
    }
    InsightCardShell(title = "Privacy Radar", icon = Icons.Default.Shield, accent = insightAccentForTitle("Privacy Radar"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun DataFlowCard(entries: List<DataFlowEntry>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_with_traffic" to "${entries.size}")
        add("total_bytes" to "${entries.sumOf { it.totalBytes }}")
        add("total_tx" to "${entries.sumOf { it.txBytes }}")
        add("total_rx" to "${entries.sumOf { it.rxBytes }}")
        add("suspicious_count" to "${entries.count { it.isSuspicious }}")
        entries.take(5).forEach { e ->
            add(simplifyPackage(e.packageName) to "tx=${e.txBytes} rx=${e.rxBytes} ratio=${"%.2f".format(e.txRxRatio)}")
        }
    }
    InsightCardShell(title = "Data Flow", icon = Icons.Default.SwapVert, accent = insightAccentForTitle("Data Flow"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun AppCompulsionCard(apps: List<AppCompulsion>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_tracked" to "${apps.size}")
        add("total_launches" to "${apps.sumOf { it.launchCount }}")
        apps.take(5).forEach { a ->
            add(simplifyPackage(a.packageName) to "launches=${a.launchCount} avg_gap=${"%.1f".format(a.avgGapMinutes)}m")
        }
    }
    InsightCardShell(title = "App Compulsion Index", icon = Icons.Default.Repeat, accent = insightAccentForTitle("App Compulsion Index"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun DeviceHealthCard(health: DeviceHealth, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "ram_used_pct" to "%.1f".format(health.ramUsedPct),
        "process_count" to "${health.processCount}",
        "foreground_count" to "${health.foregroundCount}",
        "background_count" to "${health.backgroundCount}",
        "thermal_status" to health.thermalStatus,
        "uptime_hours" to "%.1f".format(health.uptimeHours),
        "memory_trend_24h" to "%.2f".format(health.memoryTrend),
        "process_counts_trusted" to "${health.processCountsTrusted}"
    )
    InsightCardShell(title = "Device Health", icon = Icons.Default.Memory, accent = insightAccentForTitle("Device Health"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        // RAM gauge
        val hasRamData = health.ramUsedPct > 0.0
        val ramColor = when {
            health.ramUsedPct >= 90 -> TerminalRed
            health.ramUsedPct >= 75 -> TerminalAmber
            !hasRamData -> DimGray
            else -> TerminalGreen
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RAM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (hasRamData) "${"%.0f".format(health.ramUsedPct)}%" else "---",
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
                    .fillMaxWidth(fraction = if (hasRamData) (health.ramUsedPct / 100.0).toFloat().coerceIn(0f, 1f) else 1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (hasRamData) ramColor else DimGray.copy(alpha = 0.35f))
            )
        }

        if (!hasRamData) {
            Text(
                "RAM/process detail unavailable",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = DimGray,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else if (health.memoryTrend != 0.0) {
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
            StatCell(Icons.Default.Smartphone, if (hasRamData) "${health.processCount}" else "---", "processes")
            StatCell(Icons.Default.Storage, if (hasRamData) "${health.foregroundCount}" else "---", "foreground")
            StatCell(Icons.Default.Storage, if (hasRamData) "${health.backgroundCount}" else "---", "background")
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
private fun IdentityEntropyCard(entropy: IdentityEntropy, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("total_bits" to "%.2f".format(entropy.totalBits))
        add("fields" to "${entropy.fields.size}")
        entropy.fields.take(8).forEach { f ->
            add(f.name to "${f.value} (${"%.2f".format(f.entropyBits)} bits)")
        }
    }
    InsightCardShell(title = "Identity Entropy", icon = Icons.Default.Fingerprint, accent = insightAccentForTitle("Identity Entropy"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun HomeWorkCard(data: HomeWorkInference, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "home_cluster" to (data.homeCluster?.let { "${it.name ?: it.id} fixes=${it.fixCount}" } ?: "n/a"),
        "work_cluster" to (data.workCluster?.let { "${it.name ?: it.id} fixes=${it.fixCount}" } ?: "n/a"),
        "commute_distance_km" to (data.commuteDistanceKm?.let { "%.2f".format(it) } ?: "n/a"),
        "avg_commute_start_hr" to (data.avgCommuteStartHour?.let { "%.2f".format(it) } ?: "n/a"),
        "avg_commute_end_hr" to (data.avgCommuteEndHour?.let { "%.2f".format(it) } ?: "n/a")
    )
    InsightCardShell(title = "Home & Work", icon = Icons.Default.Home, accent = insightAccentForTitle("Home & Work"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun CircadianCard(data: CircadianProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "peak_hour" to "${data.peakHour}",
        "trough_hour" to "${data.troughHour}",
        "chronotype" to data.chronotype,
        "activity_spread_hrs" to "%.2f".format(data.activitySpreadHrs),
        "hourly_unlocks" to data.hourlyUnlocks.joinToString(",")
    )
    InsightCardShell(title = "Circadian Rhythm", icon = Icons.Default.WbSunny, accent = insightAccentForTitle("Circadian Rhythm"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun RoutineCard(data: RoutinePredictability, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "overall_score" to "%.3f".format(data.overallScore),
        "most_predictable_hour" to "${data.mostPredictableHour}",
        "least_predictable_hour" to "${data.leastPredictableHour}",
        "weekday_vs_weekend_shift" to "%.3f".format(data.weekdayVsWeekendShift),
        "hourly_entropy_bits" to data.hourlyEntropy.joinToString(",") { "%.2f".format(it) }
    )
    InsightCardShell(title = "Routine Predictability", icon = Icons.Default.Schedule, accent = insightAccentForTitle("Routine Predictability"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun SocialPressureCard(entries: List<SocialPressureEntry>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("apps_with_pressure" to "${entries.size}")
        add("total_notifications" to "${entries.sumOf { it.notificationCount }}")
        entries.take(5).forEach { e ->
            add(simplifyPackage(e.packageName) to "n=${e.notificationCount} resp=${"%.2f".format(e.responseRate)} med_ms=${e.medianResponseMs}")
        }
    }
    InsightCardShell(title = "Social Pressure", icon = Icons.Default.Notifications, accent = insightAccentForTitle("Social Pressure"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun AppPortfolioCard(data: AppPortfolioProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("total_apps" to "${data.totalApps}")
        add("system_apps" to "${data.systemApps}")
        add("user_apps" to "${data.userApps}")
        data.categories.entries.sortedByDescending { it.value }.take(8).forEach { (cat, count) ->
            add("cat_$cat" to "$count")
        }
        if (data.inferences.isNotEmpty()) {
            add("inferences" to data.inferences.joinToString(","))
        }
    }
    InsightCardShell(title = "App Portfolio", icon = Icons.Default.Apps, accent = insightAccentForTitle("App Portfolio"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun ChargingCard(data: ChargingBehavior, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "avg_charges_per_day" to "%.2f".format(data.avgChargesPerDay),
        "avg_discharge_depth_pct" to "%.1f".format(data.avgDischargeDepthPct),
        "overnight_charger" to "${data.overnightCharger}",
        "avg_charge_duration_ms" to "${data.avgChargeDurationMs}",
        "typical_charge_hour" to "${data.typicalChargeHour}"
    )
    InsightCardShell(title = "Charging Behavior", icon = Icons.Default.BatteryChargingFull, accent = insightAccentForTitle("Charging Behavior"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun WiFiCard(data: WiFiFootprint, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("unique_networks_7d" to "${data.uniqueNetworks7d}")
        add("total_scans" to "${data.totalScans}")
        add("mobility_score" to "%.3f".format(data.mobilityScore))
        add("home_network" to (data.homeNetwork ?: "n/a"))
        data.topNetworks.take(5).forEach { (ssid, n) ->
            add("ssid_$ssid" to "$n")
        }
    }
    InsightCardShell(title = "WiFi Footprint", icon = Icons.Default.Wifi, accent = insightAccentForTitle("WiFi Footprint"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun FragmentationCard(data: SessionFragmentation, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "avg_switches_per_session" to "%.2f".format(data.avgSwitchesPerSession),
        "avg_session_depth_ms" to "${data.avgSessionDepthMs}",
        "most_fragmented_hour" to "${data.mostFragmentedHour}",
        "least_fragmented_hour" to "${data.leastFragmentedHour}",
        "attention_score" to "%.3f".format(data.attentionScore)
    )
    InsightCardShell(title = "Session Fragmentation", icon = Icons.Default.DataUsage, accent = insightAccentForTitle("Session Fragmentation"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun DwellTimeCard(entries: List<DwellTimeEntry>, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("clusters_with_dwell" to "${entries.size}")
        add("total_dwell_ms" to "${entries.sumOf { it.totalDwellMs }}")
        add("total_visits" to "${entries.sumOf { it.visitCount }}")
        entries.take(5).forEach { e ->
            add((e.clusterName ?: e.clusterId) to "dwell=${e.totalDwellMs}ms visits=${e.visitCount} class=${e.classification}")
        }
    }
    InsightCardShell(title = "Dwell Times", icon = Icons.Default.Place, accent = insightAccentForTitle("Dwell Times"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun WeekdayWeekendCard(data: WeekdayWeekendDelta, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "weekday_avg_unlocks" to "%.2f".format(data.weekdayAvgUnlocks),
        "weekend_avg_unlocks" to "%.2f".format(data.weekendAvgUnlocks),
        "weekday_avg_screen_ms" to "${data.weekdayAvgScreenMs}",
        "weekend_avg_screen_ms" to "${data.weekendAvgScreenMs}",
        "weekday_top_apps" to data.weekdayTopApps.joinToString(",") { simplifyPackage(it) },
        "weekend_top_apps" to data.weekendTopApps.joinToString(",") { simplifyPackage(it) },
        "balance_score" to "%.3f".format(data.balanceScore)
    )
    InsightCardShell(title = "Weekday vs Weekend", icon = Icons.Default.CalendarMonth, accent = insightAccentForTitle("Weekday vs Weekend"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun IncomeCard(data: IncomeInference, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "device_tier" to data.deviceTier,
        "estimated_device_price_usd" to "${data.estimatedDevicePrice}",
        "carrier_tier" to data.carrierTier,
        "app_signals" to data.appSignals.joinToString(","),
        "overall_tier" to data.overallTier
    )
    InsightCardShell(title = "Income Inference", icon = Icons.Default.AttachMoney, accent = insightAccentForTitle("Income Inference"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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
private fun CommuteCard(data: CommutePattern, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "detected" to "${data.detected}",
        "avg_departure_hour" to "%.2f".format(data.avgDepartureHour),
        "avg_return_hour" to "%.2f".format(data.avgReturnHour),
        "avg_duration_minutes" to "%.2f".format(data.avgDurationMinutes),
        "transport_mode" to data.transportMode,
        "consistency_score" to "%.3f".format(data.consistencyScore)
    )
    InsightCardShell(title = "Commute Pattern", icon = Icons.Default.DirectionsCar, accent = insightAccentForTitle("Commute Pattern"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
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

// ── Card 26: Travel Profile ────────────────────────────────────────

@Composable
private fun TravelProfileCard(data: TravelProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "countries_seen_30d" to data.countriesSeen30d.joinToString(","),
        "is_currently_roaming" to "${data.isCurrentlyRoaming}",
        "public_ip_changes_7d" to "${data.publicIpChanges7d}",
        "gps_clusters_far_from_home" to "${data.gpsClustersFarFromHome}",
        "median_trip_radius_km" to "%.2f".format(data.medianTripRadiusKm),
        "photo_locations_30d" to "${data.photoLocations30d}",
        "travel_score" to "%.3f".format(data.travelScore)
    )
    InsightCardShell(title = "Travel Profile", icon = Icons.Default.Public, accent = insightAccentForTitle("Travel Profile"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val scoreColor = when {
            data.travelScore >= 0.7 -> TerminalPurple
            data.travelScore >= 0.35 -> TerminalBlue
            else -> TerminalGreen
        }
        val label = when {
            data.travelScore >= 0.7 -> "frequent movement"
            data.travelScore >= 0.35 -> "some travel"
            else -> "mostly local"
        }
        ScoreHeader("travel score", label, data.travelScore, scoreColor)

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Public, "${data.countriesSeen30d.size}", "countries")
            StatCell(Icons.Default.NetworkCheck, "${data.publicIpChanges7d}", "ip shifts")
            StatCell(Icons.Default.LocationOn, "${data.gpsClustersFarFromHome}", "far places")
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        CompactValueRow("roaming", if (data.isCurrentlyRoaming) "yes" else "no", if (data.isCurrentlyRoaming) TerminalAmber else DimGray)
        CompactValueRow("median trip radius", "${"%.1f".format(data.medianTripRadiusKm)} km", TerminalBlue)
        CompactValueRow("photo locations", "${data.photoLocations30d}", TerminalPurple)
        if (data.countriesSeen30d.isNotEmpty()) {
            CompactValueRow("country trail", data.countriesSeen30d.take(6).joinToString(", "), TerminalGreen)
        }
    }
}

// ── Card 27: Social Graph ──────────────────────────────────────────

@Composable
private fun SocialGraphCard(data: SocialGraphProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "hashed_contacts" to "${data.hashedContacts}",
        "unique_notification_senders_7d" to "${data.uniqueNotificationSenders7d}",
        "unique_call_counterparts_30d" to "${data.uniqueCallCounterparts30d}",
        "unique_sms_senders_30d" to "${data.uniqueSmsSenders30d}",
        "calendar_attendees_per_event_avg" to "%.2f".format(data.calendarAttendeesPerEventAvg),
        "total_connections" to "${data.totalConnections}",
        "social_breadth" to data.socialBreadth
    )
    InsightCardShell(title = "Social Graph", icon = Icons.Default.Forum, accent = insightAccentForTitle("Social Graph"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val breadthColor = when (data.socialBreadth) {
            "broad" -> TerminalPurple
            "moderate" -> TerminalBlue
            "small" -> TerminalAmber
            else -> DimGray
        }
        ScoreHeader(
            label = "relationship breadth",
            value = data.socialBreadth.replace('_', ' '),
            score = (data.totalConnections / 500.0).coerceIn(0.0, 1.0),
            color = breadthColor
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Person, formatLargeNumber(data.hashedContacts), "contacts")
            StatCell(Icons.Default.Notifications, "${data.uniqueNotificationSenders7d}", "notif senders")
            StatCell(Icons.Default.Forum, "${data.totalConnections}", "connections")
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        CompactValueRow("call counterparts", "${data.uniqueCallCounterparts30d}", TerminalGreen)
        CompactValueRow("sms senders", "${data.uniqueSmsSenders30d}", TerminalBlue)
        CompactValueRow("calendar attendee proxy", "%.1f/event".format(data.calendarAttendeesPerEventAvg), TerminalPurple)
    }
}

// ── Card 28: Activity Profile ──────────────────────────────────────

@Composable
private fun ActivityProfileCard(data: ActivityProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "sample_count" to "${data.sampleCount}",
        "percent_still" to "%.1f".format(data.percentStill * 100),
        "percent_walking" to "%.1f".format(data.percentWalking * 100),
        "percent_running" to "%.1f".format(data.percentRunning * 100),
        "percent_vehicle" to "%.1f".format(data.percentVehicle * 100),
        "percent_bicycle" to "%.1f".format(data.percentBicycle * 100),
        "avg_confidence" to "%.2f".format(data.avgConfidence),
        "movement_index" to "%.3f".format(data.movementIndex)
    )
    InsightCardShell(title = "Activity Profile", icon = Icons.Default.DirectionsCar, accent = insightAccentForTitle("Activity Profile"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val movementColor = when {
            data.movementIndex >= 0.7 -> TerminalGreen
            data.movementIndex >= 0.35 -> TerminalBlue
            else -> TerminalAmber
        }
        ScoreHeader(
            label = "movement index",
            value = "${"%.0f".format(data.movementIndex * 100)}%",
            score = data.movementIndex,
            color = movementColor
        )

        Spacer(Modifier.height(10.dp))
        PercentageRow("still", data.percentStill, TerminalAmber)
        PercentageRow("walking", data.percentWalking, TerminalGreen)
        PercentageRow("running", data.percentRunning, TerminalRed)
        PercentageRow("vehicle", data.percentVehicle, TerminalBlue)
        PercentageRow("bicycle", data.percentBicycle, TerminalPurple)

        Spacer(Modifier.height(8.dp))
        CompactValueRow("samples", "${data.sampleCount}", DimGray)
        CompactValueRow("avg confidence", "${"%.0f".format(data.avgConfidence * 100)}%", TerminalBlue)
    }
}

// ── Card 29: Heart Rate ────────────────────────────────────────────

@Composable
private fun HeartRateCard(data: HeartRateProfile, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "sample_count" to "${data.sampleCount}",
        "resting_bpm_estimate" to "%.1f".format(data.restingBpmEstimate),
        "median_bpm" to "%.1f".format(data.medianBpm),
        "peak_bpm" to "%.1f".format(data.peakBpm),
        "percent_exertion" to "%.1f".format(data.percentExertion * 100),
        "recovery_window_minutes" to "%.2f".format(data.recoveryWindowMinutes),
        "newest_sample_ms" to "${data.newestSampleMs}"
    )
    InsightCardShell(title = "Heart Rate", icon = Icons.Default.Favorite, accent = insightAccentForTitle("Heart Rate"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        Text(
            "Not a medical reading; mirrors sensor-derived exertion patterns only.",
            fontSize = 10.sp,
            color = DimGray
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Favorite, "${"%.0f".format(data.restingBpmEstimate)}", "resting")
            StatCell(Icons.Default.Timeline, "${"%.0f".format(data.medianBpm)}", "median")
            StatCell(Icons.Default.Speed, "${"%.0f".format(data.peakBpm)}", "peak")
        }

        Spacer(Modifier.height(10.dp))
        PercentageRow("exertion share", data.percentExertion, TerminalRed)
        CompactValueRow("recovery window", "${"%.0f".format(data.recoveryWindowMinutes)} min", TerminalBlue)
        CompactValueRow("samples", "${data.sampleCount}", DimGray)
        if (data.newestSampleMs > 0) {
            CompactValueRow("latest", relativeTime(data.newestSampleMs), TerminalGreen)
        }
    }
}

// ── Card 30: Bluetooth Ecosystem ───────────────────────────────────

@Composable
private fun BluetoothEcosystemCard(data: BluetoothEcosystem, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("paired_count" to "${data.pairedCount}")
        add("avg_scan_count" to "%.2f".format(data.avgScanCount))
        add("total_unique_scanned_devices_7d" to "${data.totalUniqueScannedDevices7d}")
        add("ecosystem_label" to data.ecosystemLabel)
        data.pairedBrands.take(5).forEach { (brand, count) ->
            add("brand_$brand" to "$count")
        }
    }
    InsightCardShell(title = "Bluetooth Ecosystem", icon = Icons.Default.Bluetooth, accent = insightAccentForTitle("Bluetooth Ecosystem"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val labelColor = when {
            data.ecosystemLabel.contains("apple") -> TerminalPurple
            data.ecosystemLabel.contains("samsung") -> TerminalBlue
            data.ecosystemLabel == "mixed" -> TerminalGreen
            else -> TerminalAmber
        }
        Text(
            data.ecosystemLabel.replace('_', ' '),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = labelColor
        )
        Text("paired and nearby device fingerprint", fontSize = 10.sp, color = DimGray)

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Bluetooth, "${data.pairedCount}", "paired")
            StatCell(Icons.Default.Sensors, "${data.totalUniqueScannedDevices7d}", "nearby")
            StatCell(Icons.Default.NetworkCheck, "${"%.1f".format(data.avgScanCount)}", "avg scan")
        }

        if (data.pairedBrands.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            val maxBrand = data.pairedBrands.maxOf { it.second }.coerceAtLeast(1)
            data.pairedBrands.forEach { (brand, count) ->
                CountBarRow(brand, count, maxBrand, TerminalPurple)
            }
        }
    }
}

// ── Card 31: Calendar Density ──────────────────────────────────────

@Composable
private fun CalendarDensityCard(data: CalendarDensity, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "events_30d" to "${data.events30d}",
        "events_this_week" to "${data.eventsThisWeek}",
        "avg_events_per_workday" to "%.2f".format(data.avgEventsPerWorkday),
        "recurring_events" to "${data.recurringEvents}",
        "median_event_duration_min" to "%.1f".format(data.medianEventDurationMin),
        "back_to_back_percent" to "%.2f".format(data.backToBackPercent),
        "latest_event_title_hash" to (data.latestEventTitleHash ?: "—")
    )
    InsightCardShell(title = "Calendar Density", icon = Icons.Default.CalendarMonth, accent = insightAccentForTitle("Calendar Density"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val densityScore = (data.avgEventsPerWorkday / 8.0).coerceIn(0.0, 1.0)
        val densityColor = when {
            densityScore >= 0.75 -> TerminalRed
            densityScore >= 0.4 -> TerminalAmber
            else -> TerminalGreen
        }
        ScoreHeader(
            label = "workday load",
            value = "${"%.1f".format(data.avgEventsPerWorkday)}/day",
            score = densityScore,
            color = densityColor
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.CalendarMonth, "${data.events30d}", "30d events")
            StatCell(Icons.Default.Schedule, "${data.eventsThisWeek}", "this week")
            StatCell(Icons.Default.Repeat, "${data.recurringEvents}", "recurring")
        }

        Spacer(Modifier.height(8.dp))
        PercentageRow("back-to-back", data.backToBackPercent, TerminalRed)
        CompactValueRow("median duration", "${"%.0f".format(data.medianEventDurationMin)} min", TerminalBlue)
        data.latestEventTitleHash?.takeIf { it.isNotBlank() }?.let {
            CompactValueRow("latest title hash", it, DimGray)
        }
    }
}

// ── Card 32: Photo Activity ────────────────────────────────────────

@Composable
private fun PhotoActivityCard(data: PhotoActivity, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "photos_30d" to "${data.photos30d}",
        "photos_last_7d" to "${data.photosLast7d}",
        "distinct_locations_30d" to "${data.distinctLocations30d}",
        "photos_with_gps" to "${data.photosWithGps}",
        "photos_without_gps" to "${data.photosWithoutGps}",
        "camera_diversity" to "${data.cameraDiversity}",
        "activity_hour_mode" to "${data.activityHourMode}"
    )
    InsightCardShell(title = "Photo Activity", icon = Icons.Default.PhotoCamera, accent = insightAccentForTitle("Photo Activity"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val gpsShare = if (data.photos30d > 0) data.photosWithGps.toDouble() / data.photos30d else 0.0
        ScoreHeader(
            label = "photo gps coverage",
            value = "${"%.0f".format(gpsShare * 100)}%",
            score = gpsShare,
            color = if (gpsShare >= 0.5) TerminalPurple else TerminalAmber
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.PhotoCamera, "${data.photos30d}", "photos 30d")
            StatCell(Icons.Default.Timeline, "${data.photosLast7d}", "last 7d")
            StatCell(Icons.Default.LocationOn, "${data.distinctLocations30d}", "places")
        }

        Spacer(Modifier.height(8.dp))
        CompactValueRow("gps / no gps", "${data.photosWithGps} / ${data.photosWithoutGps}", TerminalBlue)
        CompactValueRow("camera diversity", "${data.cameraDiversity}", TerminalGreen)
        CompactValueRow("common hour", formatHour(data.activityHourMode.toDouble()), TerminalAmber)
    }
}

// ── Card 33: Notification Heatmap ──────────────────────────────────

@Composable
private fun NotificationStressCard(data: NotificationStress, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("total_7d" to "${data.total7d}")
        add("per_hour_avg" to "%.2f".format(data.perHourAvg))
        add("late_night_share" to "%.3f".format(data.lateNightShare))
        add("work_hours_share" to "%.3f".format(data.workHoursShare))
        add("max_cell_count" to "${data.maxCellCount}")
        add("stress_label" to data.stressLabel)
        data.topInterrupters.take(5).forEach { (pkg, count) ->
            add("top_$pkg" to "$count")
        }
    }
    InsightCardShell(title = "Notification Heatmap", icon = Icons.Default.Notifications, accent = insightAccentForTitle("Notification Heatmap"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val stressColor = when (data.stressLabel) {
            "saturated" -> TerminalRed
            "noisy" -> TerminalAmber
            "moderate" -> TerminalBlue
            else -> TerminalGreen
        }
        ScoreHeader(
            label = "interruptions",
            value = data.stressLabel,
            score = (data.perHourAvg / 8.0).coerceIn(0.0, 1.0),
            color = stressColor
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Notifications, "${data.total7d}", "7d total")
            StatCell(Icons.Default.Speed, "%.1f".format(data.perHourAvg), "per hour")
            StatCell(Icons.Default.Bed, "${"%.0f".format(data.lateNightShare * 100)}%", "late night")
        }

        Spacer(Modifier.height(10.dp))
        NotificationHeatmap(data.heatmap, data.maxCellCount)

        if (data.topInterrupters.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            val max = data.topInterrupters.maxOf { it.second }.coerceAtLeast(1)
            data.topInterrupters.take(5).forEach { (pkg, count) ->
                CountBarRow(simplifyPackage(pkg), count, max, stressColor)
            }
        }
    }
}

// ── Card 34: Integrity Trust ───────────────────────────────────────

@Composable
private fun IntegrityTrustCard(data: IntegrityTrust, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "rooted" to "${data.rooted}",
        "debugger_attached" to "${data.debuggerAttached}",
        "adb_enabled" to "${data.adbEnabled}",
        "developer_options" to "${data.developerOptions}",
        "test_keys" to "${data.testKeys}",
        "emulator_heuristic" to "${data.emulatorHeuristic}",
        "play_integrity" to data.playIntegrity,
        "trust_score" to "${data.trustScore}",
        "verdict_label" to data.verdictLabel
    )
    InsightCardShell(title = "Integrity Trust", icon = Icons.Default.Shield, accent = insightAccentForTitle("Integrity Trust"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val trustColor = when {
            data.trustScore >= 85 -> TerminalGreen
            data.trustScore >= 60 -> TerminalAmber
            else -> TerminalRed
        }
        ScoreHeader(
            label = "device trust",
            value = "${data.trustScore}",
            score = data.trustScore / 100.0,
            color = trustColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            data.verdictLabel.replace('_', ' '),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = trustColor
        )

        Spacer(Modifier.height(10.dp))
        IntegrityFlag("rooted", data.rooted)
        IntegrityFlag("debugger attached", data.debuggerAttached)
        IntegrityFlag("adb enabled", data.adbEnabled)
        IntegrityFlag("developer options", data.developerOptions)
        IntegrityFlag("test keys", data.testKeys)
        IntegrityFlag("emulator heuristic", data.emulatorHeuristic)
        CompactValueRow("play integrity", data.playIntegrity, DimGray)
    }
}

// ── Card 35: Spending Pulse ────────────────────────────────────────

@Composable
private fun SpendingPulseCard(data: SpendingPulse, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("transactions_30d" to "${data.transactions30d}")
        add("transactions_this_week" to "${data.transactionsThisWeek}")
        add("bank_alert_count_30d" to "${data.bankAlertCount30d}")
        add("notification_volume_30d" to "${data.notificationVolume30d}")
        add("otp_count_30d" to "${data.otpCount30d}")
        add("pay_cadence_days" to (data.payCadenceDays?.let { "%.2f".format(it) } ?: "—"))
        add("activity_label" to data.activityLabel)
        data.topPaymentApps.take(5).forEach { (pkg, count) ->
            add("pay_$pkg" to "$count")
        }
    }
    InsightCardShell(title = "Spending Pulse", icon = Icons.Default.AttachMoney, accent = insightAccentForTitle("Spending Pulse"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val activityColor = when (data.activityLabel) {
            "heavy" -> TerminalRed
            "active" -> TerminalAmber
            else -> TerminalGreen
        }
        ScoreHeader(
            label = "financial signal",
            value = data.activityLabel,
            score = ((data.transactions30d + data.bankAlertCount30d + data.notificationVolume30d) / 100.0).coerceIn(0.0, 1.0),
            color = activityColor
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.AttachMoney, "${data.transactions30d}", "tx 30d")
            StatCell(Icons.Default.Notifications, "${data.bankAlertCount30d}", "bank alerts")
            StatCell(Icons.Default.LockOpen, "${data.otpCount30d}", "otp")
        }

        Spacer(Modifier.height(8.dp))
        CompactValueRow("this week proxy", "${data.transactionsThisWeek} tx", TerminalBlue)
        CompactValueRow("finance notifications", "${data.notificationVolume30d}", TerminalPurple)
        data.payCadenceDays?.let {
            CompactValueRow("pay cadence", "${"%.1f".format(it)} days", TerminalGreen)
        }

        if (data.topPaymentApps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DimGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            val max = data.topPaymentApps.maxOf { it.second }.coerceAtLeast(1)
            data.topPaymentApps.forEach { (pkg, count) ->
                CountBarRow(simplifyPackage(pkg), count, max, activityColor)
            }
        }
    }
}

// ── Card 36: Communication Depth ───────────────────────────────────

@Composable
private fun CommunicationDepthCard(data: CommunicationDepth, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = listOf(
        "total_calls_30d" to "${data.totalCalls30d}",
        "avg_call_minutes" to "%.2f".format(data.avgCallMinutes),
        "total_sms_exchanged_30d" to "${data.totalSmsExchanged30d}",
        "unique_counterparts_30d" to "${data.uniqueCounterparts30d}",
        "close_tie_ratio" to "%.3f".format(data.closeTieRatio),
        "late_percent" to "%.3f".format(data.latePercent),
        "communication_style" to data.communicationStyle,
        "responsiveness_score" to "%.3f".format(data.responsivenessScore)
    )
    InsightCardShell(title = "Communication Depth", icon = Icons.Default.Forum, accent = insightAccentForTitle("Communication Depth"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        val styleColor = when (data.communicationStyle) {
            "voice_first" -> TerminalGreen
            "text_first" -> TerminalBlue
            "balanced" -> TerminalPurple
            else -> DimGray
        }
        ScoreHeader(
            label = "responsiveness",
            value = data.communicationStyle.replace('_', ' '),
            score = data.responsivenessScore,
            color = styleColor
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Forum, "${data.totalCalls30d}", "calls")
            StatCell(Icons.Default.Notifications, "${data.totalSmsExchanged30d}", "sms")
            StatCell(Icons.Default.Person, "${data.uniqueCounterparts30d}", "people")
        }

        Spacer(Modifier.height(8.dp))
        CompactValueRow("avg call", "${"%.1f".format(data.avgCallMinutes)} min", TerminalGreen)
        PercentageRow("top-5 concentration", data.closeTieRatio, TerminalAmber)
        PercentageRow("late-night share", data.latePercent, TerminalRed)
    }
}

@Composable
private fun ScoreHeader(label: String, value: String, score: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 10.sp, color = color, fontFamily = FontFamily.Monospace)
        }
        Text(
            "${"%.0f".format(score.coerceIn(0.0, 1.0) * 100)}%",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
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
                .fillMaxWidth(fraction = score.toFloat().coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun PercentageRow(label: String, value: Double, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.weight(0.46f).padding(horizontal = 6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = value.toFloat().coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.75f))
            )
        }
        Text(
            "${"%.0f".format(value * 100)}%",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.weight(0.2f)
        )
    }
}

@Composable
private fun CountBarRow(label: String, count: Int, max: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.weight(0.38f).padding(horizontal = 6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (count.toFloat() / max.coerceAtLeast(1)).coerceIn(0.04f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.75f))
            )
        }
        Text(
            "$count",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.weight(0.2f)
        )
    }
}

@Composable
private fun CompactValueRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = DimGray)
        Text(
            value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IntegrityFlag(label: String, active: Boolean) {
    val color = if (active) TerminalRed else TerminalGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (active) "flagged" else "clear",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun NotificationHeatmap(heatmap: List<List<Int>>, maxCellCount: Int) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            listOf("0", "6", "12", "18").forEach { label ->
                Text(
                    label,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DimGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        heatmap.take(7).forEachIndexed { dayIndex, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayLabels.getOrElse(dayIndex) { "?" },
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DimGray,
                    modifier = Modifier.width(16.dp)
                )
                row.take(24).forEach { count ->
                    val intensity = if (maxCellCount > 0) (count.toFloat() / maxCellCount).coerceIn(0f, 1f) else 0f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(7.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (count == 0) CellEmpty else TerminalRed.copy(alpha = 0.2f + intensity * 0.8f)
                            )
                    )
                }
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
    meta: InsightMeta? = null,
    showDiagnostics: Boolean = false,
    dataSnapshot: List<Pair<String, String>>? = null,
    content: @Composable () -> Unit
) {
    val expansionCommand = LocalCardExpansionCommand.current
    var infoExpanded by rememberSaveable(title) { mutableStateOf(false) }
    var dataExpanded by rememberSaveable("$title:data") { mutableStateOf(false) }
    var collapsed by rememberSaveable("$title:collapsed") { mutableStateOf(true) }
    val educationalInfo = educationalInfoForTitle(title)
    val whyMatters = whyMattersForTitle(title)
    val algorithm = algorithmForTitle(title)
    LaunchedEffect(expansionCommand.version) {
        collapsed = expansionCommand.collapsed
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (dataSnapshot != null) {
                    IconButton(
                        onClick = {
                            dataExpanded = !dataExpanded
                            if (dataExpanded) collapsed = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            "Show data for $title",
                            tint = if (dataExpanded) TerminalBlue else DimGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                IconButton(
                    onClick = {
                        infoExpanded = !infoExpanded
                        if (infoExpanded) collapsed = false
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        "Explain $title",
                        tint = if (infoExpanded) accent else DimGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Confidence + staleness badges
                if (meta != null) {
                    if (meta.isStale) {
                        Text(
                            "STALE",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TerminalAmber,
                            modifier = Modifier
                                .background(TerminalAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    when (meta.confidence) {
                        ConfidenceTier.LOW -> {
                            Text(
                                "LOW",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TerminalRed.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .background(TerminalRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        ConfidenceTier.MODERATE -> {
                            Text(
                                "MED",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TerminalAmber.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .background(TerminalAmber.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        ConfidenceTier.HIGH -> { /* no badge for high confidence */ }
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        if (collapsed) "Expand $title" else "Collapse $title",
                        tint = DimGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (!collapsed) {
                if (infoExpanded) {
                    Spacer(Modifier.height(8.dp))
                    EducationalInfoPanel(
                        text = educationalInfo,
                        whyMatters = whyMatters,
                        algorithm = algorithm,
                        accent = accent
                    )
                }
                if (dataExpanded && dataSnapshot != null) {
                    Spacer(Modifier.height(8.dp))
                    DataSnapshotPanel(rows = dataSnapshot, accent = accent)
                }
                // Data source + diagnostic info
                if (meta != null && showDiagnostics) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "src: ${meta.dataSource}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DimGray
                        )
                        Text(
                            "${meta.dataPointCount} pts",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DimGray
                        )
                        if (meta.newestDataMs > 0) {
                            Text(
                                meta.ageLabel,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (meta.isStale) TerminalAmber else DimGray
                            )
                        }
                    }
                    if (meta.attempted.size > 1) {
                        Text(
                            "tried: ${meta.attempted.joinToString(" > ")}",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DimGray.copy(alpha = 0.7f)
                        )
                    }
                }
                // Limited data warning
                if (meta != null && meta.confidence == ConfidenceTier.LOW && !showDiagnostics) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Read cautiously: this is based on limited evidence" +
                            if (meta.dataSource.startsWith("fallback")) " from a backup signal" else "",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray
                    )
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun EducationalInfoPanel(
    text: String,
    whyMatters: String,
    algorithm: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(10.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = accent.copy(alpha = 0.25f))
        Spacer(Modifier.height(6.dp))
        Text(
            whyMatters,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = TerminalBlue.copy(alpha = 0.4f))
        Spacer(Modifier.height(6.dp))
        Text(
            "ALGORITHM",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TerminalBlue.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            algorithm,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = TerminalBlue
        )
    }
}

@Composable
private fun DataSnapshotPanel(
    rows: List<Pair<String, String>>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalBlue.copy(alpha = 0.06f))
            .padding(10.dp)
    ) {
        Text(
            "DATA USED",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TerminalBlue.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = TerminalBlue.copy(alpha = 0.3f))
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text(
                "(no data captured)",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = DimGray
            )
        } else {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DimGray,
                        modifier = Modifier.weight(0.45f)
                    )
                    Text(
                        value,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.55f),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 4
                    )
                }
            }
        }
    }
}

private fun educationalInfoForTitle(title: String): String = when (title) {
    "Today" ->
        "This card mirrors your daily device footprint: every unlock, every minute of screen time, every step, every percent of battery burned. Trackers reflect on these same totals to gauge engagement, estimate mobility, and decide whether your device is worth harvesting right now."

    "Sleep" ->
        "MirrorTrack reflects your likely sleep windows by watching for long phone silence, then strengthening confidence when ambient light is dark and ambient sound is quiet. This is still an inference, not medical data. But profilers use the same reflection of inactivity to estimate bedtime, wake time, shift work, insomnia, and the hours when you are least able to resist a notification."

    "App Attention (7d)" ->
        "This card mirrors which apps actually held your gaze over the last week, ranked by foreground time and compared against your prior baseline. The reflection reveals what profilers already see: entertainment habits, work tools, shopping interest, news consumption, and shifts in routine that signal opportunity for targeted ads."

    "Anomaly Feed" ->
        "Reflects the moments your behavior broke its own pattern \u2014 unusual unlock spikes, late-night activity, rapid battery drain. These anomalies mirror the exact triggers that surveillance systems flag, because sudden changes often reflect travel, stress, illness, schedule disruption, or a new dependency."

    "Location Clusters" ->
        "Groups your repeated GPS fixes into places. When you see the clusters, you see a mirror of how profilers would reconstruct your life: home, work, gym, grocery store, school pickup. Location clustering reflects one of the strongest signals for identity, income context, and routine predictability."

    "Unlock After Notification" ->
        "Reflects how quickly each app's notifications pull you back to your phone. Short latency and high response rates mirror the metrics engagement systems use to rank notification effectiveness, measure urgency, and gauge how reflexively you respond to each app's pull."

    "Fingerprint Stability" ->
        "Mirrors which device identity fields stay fixed and which ones shift. Stable fields reflect a trackable device; changes can reflect updates, resets, or deliberate privacy steps. Fingerprinting systems combine dozens of ordinary-looking fields to build a mirror image of your device that persists across apps, networks, and time."

    "Monthly Trends" ->
        "Reflects your behavior across calendar months \u2014 unlocks, screen time, steps, data volume. These long-range trends mirror durable lifestyle shifts that daily snapshots miss: a new job, a move, a habit change, or a slow drift toward more (or less) device dependence."

    "Engagement Score" ->
        "Mirrors the engagement scoring used by Firebase, Facebook Analytics, and ad-tech SDKs.\n\n" +
        "\u2022 Sessions \u2014 total distinct phone usage sessions in the last 7 days. A session starts at unlock and ends after inactivity. High counts reflect habit strength or compulsive checking.\n\n" +
        "\u2022 Frequency \u2014 average sessions per day (e.g. \"4.2/d\" means about 4 pickups daily). Profilers use this reflection to classify you as dormant, casual, regular, or power-level.\n\n" +
        "\u2022 Avg length \u2014 mean duration of each session. Short sessions reflect quick checks (notifications, social feeds); long ones reflect deep engagement (work, video, reading).\n\n" +
        "Numbers turn red when they fall below the engagement thresholds that analytics systems consider \"at risk of churn\" \u2014 the same thresholds that trigger re-engagement push notifications, special offers, or algorithmic content boosts designed to pull you back."

    "Privacy Radar" ->
        "Reflects which apps reach into your camera, microphone, location, and contacts \u2014 and how often. A high score mirrors the kind of privacy-risk ranking used in corporate app audits and mobile threat analysis. The reflection is not an accusation; it simply shows how deeply each app touches your personal surfaces."

    "Data Flow" ->
        "Mirrors per-app network send and receive volume. Upload-heavy apps can reflect sync activity, telemetry, media sharing, or quiet data exfiltration. This reflection reveals which apps are actively moving your data even when you are not looking at them."

    "App Compulsion Index" ->
        "Reflects how often you reopen each app and how short the gaps between launches are. Frequent reopens with small gaps mirror the checking-loop pattern that consumer analytics uses to measure dependency and compulsion \u2014 the same signal that tells advertisers which apps already own your attention."

    "Device Health" ->
        "Mirrors your phone's internal state: memory pressure, process count, thermal status, uptime, and resource trend. If only battery fallback data is available, some fields show as unknown. This reflection helps explain behavior gaps and reveals whether heat, low resources, or a recent restart may be distorting the rest of your behavioral picture."

    "Identity Entropy" ->
        "Reflects how uniquely identifiable your device is by estimating entropy across model, hardware, identifiers, and configuration. Higher entropy means fewer devices mirror yours. Fingerprinters combine many individually weak fields because the combination can reflect a near-unique identity \u2014 your digital silhouette."

    "Home & Work" ->
        "Mirrors the two most revealing places in your life by analyzing repeated location clusters and time-of-day patterns. Nighttime dwell reflects home; weekday daytime dwell reflects work. This is the standard method profilers use to anchor your entire life pattern from raw GPS data."

    "Circadian Rhythm" ->
        "Reflects your 24-hour activity cycle from unlocks and activity events. Peak and quiet hours mirror your chronotype, shift patterns, and daily availability. Behavioral targeting systems use this same reflection of timing to decide when messages and ads are most likely to catch your attention."

    "Routine Predictability" ->
        "Mirrors how repeatable your daily schedule is by comparing hourly activity patterns across days. A predictable routine reflects a life that profilers can forecast without constant fresh observation. An irregular pattern reflects unpredictability \u2014 harder to target, but also harder to hide in a crowd."

    "Social Pressure" ->
        "Reflects the connection between notifications and your subsequent unlocks, app by app. A high notification count paired with fast responses mirrors the pull each app has over your attention. This reflection exposes social obligations, work responsiveness, and which channels successfully demand that you pick up your phone."

    "App Portfolio" ->
        "Mirrors the story your installed apps tell about you \u2014 even the ones you never open. Finance, parenting, fitness, dating, gaming, and productivity apps each reflect life-stage and interest signals that profilers harvest without needing to see a single minute of actual usage."

    "Charging Behavior" ->
        "Reflects your charging habits: when you plug in, how low you let the battery drop, whether you charge overnight, and how long each charge lasts. These patterns mirror your sleep routine, commute constraints, battery anxiety, and proximity to stable power sources throughout the day."

    "WiFi Footprint" ->
        "Mirrors your movement through the world via the Wi-Fi networks your device has seen. A stable overnight network reflects home; many networks reflect mobility. Wi-Fi history mirrors GPS-quality location data even without GPS enabled, because network names anchor visits to real places."

    "Session Fragmentation" ->
        "Reflects how chopped-up your attention is during each phone session. High fragmentation mirrors rapid app-switching, distraction, and reflexive checking. Low fragmentation reflects deeper focus or sustained task engagement. This metric mirrors how profilers assess your attention quality."

    "Dwell Times" ->
        "Mirrors how long your device stays at each recurring place and how often visits repeat. Dwell duration reflects the difference between anchor locations and pass-through stops. Time spent often reflects more about your life than the location alone."

    "Weekday vs Weekend" ->
        "Reflects the contrast between your workweek and weekend behavior: unlocks, screen time, and top apps. The differences mirror whether your life is driven by structured obligations or free time \u2014 and which mode carries more device dependence."

    "Income Inference" ->
        "Mirrors the crude socioeconomic profiling that ad networks perform using device tier, carrier signals, and installed app categories. This reflection is deliberately rough \u2014 the point is to show how easily weak signals are combined into an income guess that shapes the ads, offers, and treatment you receive."

    "Commute Pattern" ->
        "Reflects repeated transitions between your inferred home and work locations. Departure time, return time, trip duration, transport mode, and consistency mirror the schedule regulators and predictors that let profilers know where you will be at any given hour of a workday."

    "Voice Context" ->
        "Mirrors what speech patterns reveal about your context \u2014 without retaining raw audio. Conversation density, context labels, and keyword tags reflect whether you are in a meeting, running errands, watching media, or having a personal conversation. All processing is local; the reflection stays on your device."

    "Travel Profile" ->
        "Mirrors how far and how often you stray from your usual orbit. Carrier roaming flags, public-IP changes, location clusters far from home, and EXIF GPS spread combine into a single travel score. Profilers use the same signals to mark travel, surface travel-themed ads, and infer disposable income."

    "Social Graph" ->
        "Reflects the breadth of your interpersonal connections via hashed identifiers: contacts, distinct notification senders, call counterparts, SMS senders, and calendar attendees. No raw names or numbers leave your device. Profilers harvest the same skeleton to estimate community size and which channels you actually use."

    "Activity Profile" ->
        "Mirrors how your body moves throughout the day using on-device activity recognition. Still, walking, running, vehicle, and bicycle percentages reflect whether you are sedentary, active, commuting, or training. Profilers use this physical signal to score health risk, fitness intent, and commute mode."

    "Heart Rate" ->
        "Reflects resting rate, peak exertion, and recovery time when a body sensor is available. The reflection is not a medical reading. Wearable companies and ad networks use the same signal to estimate stress, sleep quality, exercise habits, and health-related audiences."

    "Bluetooth Ecosystem" ->
        "Mirrors the constellation of Bluetooth devices around you: paired earbuds, watches, speakers, cars, and nearby beacons. The brand mix can reveal purchasing power, mobility, work environment, and loyalty to a hardware ecosystem."

    "Calendar Density" ->
        "Reflects how packed your calendar is: weekly events, average events per workday, recurring patterns, back-to-back density, and median meeting length. Productivity SDKs and workplace tools use these numbers to score whether time is structured, free, fragmented, or overloaded."

    "Photo Activity" ->
        "Mirrors how often and where you take photos using EXIF metadata only; no pixels are inspected. Volume, distinct GPS locations, time-of-day mode, and camera diversity reflect lifestyle, travel, social activity, and hobbies."

    "Notification Heatmap" ->
        "Reflects the seven-day-by-twenty-four-hour grid of notifications posted to your device. Late-night intrusions, work-hours saturation, and top interrupters reveal which apps own the most expensive part of your day."

    "Integrity Trust" ->
        "Mirrors tamper signals: root status, debugger attachment, ADB enabled, developer options, AOSP test keys, emulator hints, and Play Integrity verdict. Banks, streaming services, ad networks, and fraud engines use the same signals to decide how much to trust the device."

    "Spending Pulse" ->
        "Reflects the financial heartbeat in messages and notifications: counts of bank alerts, OTPs, transactions, finance-app push notifications, and a crude pay-cadence estimate. No raw bodies are stored. Trackers use the same signals to infer income tier and payment-app loyalty."

    "Communication Depth" ->
        "Mirrors phone communication depth: total calls, average call length, SMS volume, unique counterparts, top-five concentration, and late-night share, all from hashed metadata. Voice-first, text-first, balanced, and quiet styles each reflect a distinct social shape."

    else ->
        "This card mirrors a specific behavioral signal and reflects what profilers would see if they had the same data. Treat every result as probabilistic: stronger data coverage raises confidence, while sparse or stale data should be read cautiously."
}

private fun whyMattersForTitle(title: String): String = when (title) {
    "Today" ->
        "Look in this mirror every day. If a tracker can see whether you were active, idle, sedentary, or generating unusual volumes of data \u2014 so can anyone who buys that data downstream."

    "Sleep" ->
        "Your sleep windows reflect routine, fatigue, late nights, and the hours when you are least defended against notification manipulation. This mirror shows why bedtime is a high-value signal."

    "App Attention (7d)" ->
        "This mirror reflects which apps own your time. That ranking is the foundation for habit scoring, ad targeting, and retention strategy \u2014 and it updates itself every week."

    "Anomaly Feed" ->
        "Breaks in pattern are what make surveillance valuable. This mirror reflects the moments that point to travel, illness, stress, or schedule disruption \u2014 exactly the signals a profiler watches for."

    "Location Clusters" ->
        "Recurring places mirror the map of your physical life. Once a profiler can reflect your regular locations, they can connect behavior to home, work, errands, and sensitive destinations."

    "Unlock After Notification" ->
        "This mirror reflects how quickly each app can pull you back. Fast post-notification unlocks reveal which apps have trained your reflexes \u2014 and when those interruptions reliably succeed."

    "Fingerprint Stability" ->
        "Stable identity fields mirror a device that is easy to follow over time. Changes reflect resets, upgrades, or deliberate privacy steps \u2014 each of which tells its own story."

    "Monthly Trends" ->
        "Long-range trends mirror durable shifts that daily snapshots miss. This reflection is more valuable than any single day because it reveals whether your attention, mobility, and habits are actually changing."

    "Engagement Score" ->
        "This is how apps decide you are \"losing interest\" and fire re-engagement campaigns. A single engagement number is trivially easy to rank and act on, which is why growth teams, ad networks, and surveillance systems all converge on the same formula. Red numbers reflect a classification of \"lapsing\" \u2014 the trigger for interventions designed to pull you back."

    "Privacy Radar" ->
        "This mirror separates ordinary apps from those that reach deep into your private life. The reflection helps you see which apps deserve scrutiny and which are staying in their lane."

    "Data Flow" ->
        "Network volume mirrors what apps are doing behind your back \u2014 background syncing, silent uploads, and telemetry that runs even when you are not looking. The reflection makes invisible data movement visible."

    "App Compulsion Index" ->
        "This mirror reflects which apps have trained you into checking loops. Repeated reopens with short gaps are the clearest signal that an app has learned how to pull your attention back on demand."

    "Device Health" ->
        "This reflection helps you understand whether heat, low resources, battery state, or a restart may be distorting the rest of your behavioral mirror. Device condition matters because it shapes what other cards can accurately show."

    "Identity Entropy" ->
        "The more unique your device looks, the easier it is to recognize and follow across time, apps, and data brokers. This mirror reflects your digital silhouette \u2014 and how visible it is in a crowd."

    "Home & Work" ->
        "Home and work are the anchor points of your life pattern. Once this mirror reflects both, commute, schedule, income, and unusual absences all become far easier for a profiler to infer."

    "Circadian Rhythm" ->
        "This mirror reflects when you sleep, work, and socialize \u2014 the timing profile that behavioral targeting systems use to decide when you are most persuadable by messages and notifications."

    "Routine Predictability" ->
        "A predictable routine reflects a life that profilers can forecast without constant fresh observation. This mirror shows how much of your future behavior is already written in your past patterns."

    "Social Pressure" ->
        "This mirror reflects which channels and apps successfully demand your attention, exposing work expectations, relationship dynamics, and the interruption pressure each one exerts."

    "App Portfolio" ->
        "Your installed apps reflect interests and life roles even without a single minute of active use. This mirror shows why app inventories are one of the most heavily harvested profile sources."

    "Charging Behavior" ->
        "Charging habits quietly mirror when your phone is idle, near a bed, in a car, at a desk, or under stress from heavy use. The reflection reveals routine in a way that feels invisible but is surprisingly precise."

    "WiFi Footprint" ->
        "Your network history mirrors your location history. Recurring SSIDs reflect places as clearly as GPS, making movement patterns visible even when location permissions are off."

    "Session Fragmentation" ->
        "This mirror reflects the difference between focused work and constant checking. High fragmentation reflects distraction load; low fragmentation reflects sustained attention \u2014 both are valuable signals about how your time is actually spent."

    "Dwell Times" ->
        "How long you stay reflects more than where you go. This mirror separates the anchor locations in your life from the brief pass-through stops, revealing which places actually matter."

    "Weekday vs Weekend" ->
        "This mirror reflects whether your life is shaped more by structured obligations or free time. The contrast between workweek and weekend behavior reveals schedule dependence even when the pattern is nontraditional."

    "Income Inference" ->
        "Even rough socioeconomic reflections shape the ads, offers, fraud scores, and treatment tiers that companies assign to you. This mirror shows how a few weak signals combine into a surprisingly confident income guess."

    "Commute Pattern" ->
        "Your commute pattern mirrors schedule rigidity, travel burden, and predictable presence at specific times. This reflection shows why profilers value regular commuters \u2014 their future location is almost already known."

    "Voice Context" ->
        "Speech patterns reflect context that other device signals miss: meetings, errands, family time, media use. This mirror processes everything locally and retains only derived signals, but it still shows how much context a microphone can reflect."

    "Travel Profile" ->
        "Travel is a high-value signal because it changes risk, ads, pricing, and timing. This mirror shows how many ordinary fields can reconstruct movement even when no single source is complete."

    "Social Graph" ->
        "Your contact surface reflects influence, isolation, work demands, and relationship channels. This mirror keeps identifiers local and hashed, but still shows why social metadata is so valuable."

    "Activity Profile" ->
        "Physical movement reflects health, commute mode, work style, and daily constraint. This mirror shows how coarse sensor data can still classify a body in motion."

    "Heart Rate" ->
        "Heart-rate signals can expose stress, exertion, recovery, and sleep disruption. This mirror treats them cautiously, but it shows why wearable data has become a profiling prize."

    "Bluetooth Ecosystem" ->
        "Bluetooth devices mirror the environments and products around you. This reflection reveals how nearby hardware becomes a proxy for lifestyle, brand affinity, and location context."

    "Calendar Density" ->
        "A crowded calendar mirrors obligations before they happen. This reflection shows why schedule data is predictive: it tells a profiler where your attention will be unavailable next."

    "Photo Activity" ->
        "Photo metadata reflects travel, social life, hobbies, and routines without looking at image content. This mirror shows how much context rides along with ordinary files."

    "Notification Heatmap" ->
        "The timing of interruptions mirrors pressure. This reflection shows which apps reach you at night, during work, and during quiet hours, which is exactly what attention systems optimize."

    "Integrity Trust" ->
        "Integrity signals decide whether companies treat your device as ordinary, developer-modified, or hostile. This mirror exposes the hidden trust score behind fraud checks and access gates."

    "Spending Pulse" ->
        "Payment and OTP metadata reveal financial activity even when raw messages stay private. This mirror shows why transaction-adjacent signals are enough to target income, debt, and shopping behavior."

    "Communication Depth" ->
        "Communication metadata reveals relationship intensity without reading content. This mirror shows how call duration, SMS volume, late timing, and repeated counterparts become a social profile."

    else ->
        "Once a pattern is stable enough to reflect in a summary, it can be compared, ranked, predicted, and acted on by whoever holds the mirror."
}

private fun algorithmForTitle(title: String): String = when (title) {
    "Today" ->
        "Aggregate today's data points (since local midnight) into category counters: count screen-on transitions for unlocks, sum foreground intervals for screen time, integrate step-count deltas, take latest battery-level reading, count unique app launches. No smoothing or normalization."

    "Sleep" ->
        "1) Bin minute-resolution screen state to detect stillness windows >= 4h. 2) For each candidate, compute mean ambient lux and sound dBFS (if available). 3) Confidence = base(stillness duration) + bonus(dark) + bonus(quiet) + bonus(voice silent). 4) Persist last 72h of intervals; render as timeline."

    "App Attention (7d)" ->
        "For each foreground-event pair (start, end), accumulate per-package duration over a 7d window. Compare to the prior 7d baseline; flag deltas > 25% as up/down arrows. Rank descending by total ms, take top N."

    "Anomaly Feed" ->
        "For each tracked metric (unlocks/h, battery drop rate, late-night screen, data egress), compute rolling 14d mean + stddev. Z-score current sample; flag |z| > 2.0 as anomaly. Tag with severity, timestamp, and originating collector."

    "Location Clusters" ->
        "DBSCAN-lite: round each GPS fix to ~50m grid. For each cell, count visits and total dwell ms. Merge adjacent populated cells. Sort by dwell descending. Discard clusters with < 3 visits or < 10 minutes total dwell."

    "Unlock After Notification" ->
        "For every notification posted, find the next unlock event from the same package within 10 minutes. Compute latency = unlock_ts - notif_ts. Aggregate per package: count, mean latency, response rate = unlocks_within_10m / notifications."

    "Fingerprint Stability" ->
        "For each device-identity field (model, brand, build, locale, timezone, install IDs), keep a per-day distinct value set. Field is STABLE if 1 unique value across the window, DRIFTING if 2-3, UNSTABLE if 4+. Annotate the most-recent change timestamp."

    "Monthly Trends" ->
        "Group every captured data point by ISO calendar month. Aggregate month-level totals: unlocks, screen time, steps, sent+recv bytes. Compute month-over-month delta and direction. Truncate to last 12 months."

    "Engagement Score" ->
        "1) Detect sessions: contiguous foreground intervals with gap < 60s. 2) Sessions7d = count over last 7d. 3) Frequency = sessions7d / 7. 4) AvgLength = sum(durations) / sessions7d. 5) Bucket numbers vs analytics churn thresholds (sessions/d < 1, length < 60s flagged red)."

    "Privacy Radar" ->
        "For each installed app, score the union of its declared dangerous permissions (camera/mic/location/contacts/storage) weighted by recent runtime grants. Combine with PACKAGE_USAGE_STATS foreground time. Sort descending; tag categories accessed in the last 7d."

    "Data Flow" ->
        "Pull TrafficStats per-uid sent + received bytes deltas across the polling window. Group by package via uid->pkg map. Sum 7d totals. Compute upload ratio = tx / (tx + rx). Sort by total bytes descending."

    "App Compulsion Index" ->
        "For each package, count foreground re-entries within 60s of the last exit. CompulsionScore = (reopens / launches) * (1 / median_gap_minutes). Higher = tighter checking loop. Rank descending; cap top N for display."

    "Device Health" ->
        "Sample available system signals: ActivityManager.MemoryInfo (avail/total), running process count, BatteryManager.PROPERTY_TEMPERATURE (or battery-fallback), SystemClock.uptimeMillis. Trend = (current - rolling_mean_24h) / rolling_mean_24h."

    "Identity Entropy" ->
        "For each fingerprint dimension (model, hw revision, locale, timezone offset, screen resolution, build_id), estimate population frequency from a static prior. Total entropy = sum(-log2(p_i)) bits. Higher bits = more unique device."

    "Home & Work" ->
        "Filter location clusters by time-of-day: nighttime (22:00-06:00 dwell > 60% of cluster total) -> home candidate. Weekday daytime (Mon-Fri 09:00-18:00 dwell > 50%) -> work candidate. Pick top-1 of each by total dwell."

    "Circadian Rhythm" ->
        "Bin every unlock + activity event by hour-of-day across last 14d. Normalize to a 24-bin distribution. Peak hour = argmax. Quiet hour = argmin. Compute amplitude = peak / mean as rhythm strength."

    "Routine Predictability" ->
        "Build a per-day 24-hour activity vector. Compute pairwise cosine similarity across recent days. Predictability = mean similarity across all pairs. Higher = more repeatable schedule. Output as 0.0-1.0 score + descriptive label."

    "Social Pressure" ->
        "Per-package: count notifications received and unlock-within-2min responses. PressureScore = response_rate * notification_count / total_notifications. Surfaces apps that drive both volume and reflexive response."

    "App Portfolio" ->
        "Walk the installed-package list. Map each package to a category (finance/parenting/fitness/dating/gaming/productivity/...) via a static keyword-and-prefix dictionary. Count per category; flag presence of high-signal categories."

    "Charging Behavior" ->
        "Window-detect plug-in to plug-out events from battery_status stream. Per session: start_level, end_level, start_hour, duration. Aggregate: median start hour, median start level, % overnight (started 21:00-02:00 and lasted > 3h)."

    "WiFi Footprint" ->
        "Track distinct SSIDs seen in the last 7d (BSSID hashed for stability). Compute connection-time per SSID. Top SSID = mostly-connected network. Mobility score = unique SSIDs / total connection events."

    "Session Fragmentation" ->
        "Within each session, count app-switch transitions and average dwell-per-app. Fragmentation = switches / minute. High = rapid switching; low = sustained focus. Output 7d mean + descriptive bucket."

    "Dwell Times" ->
        "For each location cluster, compute median visit duration and visit frequency over 30d. DwellEntry = (cluster_id, median_min, visits). Sort by median descending; surface top N anchors."

    "Weekday vs Weekend" ->
        "Partition events into weekday (Mon-Fri) and weekend (Sat-Sun) buckets. For unlocks, screen time, top apps: compute per-day average per bucket. Delta = weekend - weekday; |delta| / weekday determines magnitude."

    "Income Inference" ->
        "1) Map device build SKU -> tier (budget/mid/premium/flagship). 2) Classify carrier MCC/MNC -> region/operator class. 3) Detect finance/luxury/premium app categories from portfolio. 4) Linear-combine with hand-tuned weights -> coarse income tier."

    "Commute Pattern" ->
        "Find nearest-cluster transitions between home cluster and work cluster. Filter to weekday trips; keep those with proximity match (cluster_distance < 200m). Aggregate departure_time, return_time, duration; report median + consistency = 1 - stddev/mean."

    "Voice Context" ->
        "Vosk transcribes microphone snippets only when speech is detected. Per window: extract token count, words/min, keyword tags from a fixed dictionary, context label (meeting/media/errands/personal). Raw audio is discarded; only signals stored."

    "Travel Profile" ->
        "TravelScore = w1*roaming_now + w2*public_ip_changes_7d/5 + w3*clusters_far_from_home/10 + w4*photo_locations_30d/20. Clipped to [0, 1]. Far-from-home = haversine(cluster, home) > 50km."

    "Social Graph" ->
        "Sum of distinct hashed identifiers across collectors: contact rows, notification senders 7d, call counterparts 30d, SMS senders 30d. Bucket total -> isolated/small/moderate/broad. SHA-256 first-8-bytes used; no raw PII surfaces."

    "Activity Profile" ->
        "ActivityRecognition emits classified samples (still/walking/running/vehicle/bicycle) with confidence. Aggregate over 7d: percentages by class. MovementIndex = (walking + running*2 + bicycle*1.5) / total, clipped to [0, 1]."

    "Heart Rate" ->
        "Body-sensor heart-rate samples ranked. Resting = 10th percentile. Median = 50th. Peak = max. ExertionPercent = samples >= 120 / total. Recovery = avg minutes from peak back to resting + 10 BPM."

    "Bluetooth Ecosystem" ->
        "For each paired device, infer brand from name prefix dictionary (Apple/Samsung/Bose/Sony/...). Aggregate paired counts and recent-scan counts. EcosystemLabel = argmax brand share if dominant > 60%, else 'mixed' or 'minimal'."

    "Calendar Density" ->
        "Read 30d of calendar events. Count total, this-week subset, recurring set, median duration, back-to-back fraction (events with < 15 min gap). Workday avg = total_workday_events / workday_count."

    "Photo Activity" ->
        "MediaStore EXIF-only scan of last 30d. Count photos, photos_with_gps, distinct rounded GPS cells, distinct camera_make+model strings. Mode-hour = argmax of capture-hour histogram."

    "Notification Heatmap" ->
        "Bin every received notification by (day_of_week, hour_of_day) into a 7x24 matrix. Late-night share = sum(rows for 22-06) / total. Work-hours share = sum(rows for 9-18 weekday) / total. StressLabel from total_per_hour vs thresholds."

    "Integrity Trust" ->
        "Probe: rooted (su/test-keys), debuggerAttached (Debug.isDebuggerConnected), adbEnabled (Settings.Global.ADB_ENABLED), developerOptions (DEVELOPMENT_SETTINGS_ENABLED), emulator-heuristic (build fingerprints). TrustScore = 100 - 15*flag_count, clipped to 0."

    "Spending Pulse" ->
        "Tag SMS bodies by regex heuristic without storing content: /OTP|verif/ -> otp, /credited|debited|txn/ -> transaction, /balance|account/ -> bank_alert, /sale|offer|discount/ -> promo. Pay-cadence = median gap between large-credit signals."

    "Communication Depth" ->
        "From CallLog metadata: total calls, mean duration, missed/rejected counts. From SMS: sent+recv counts, unique hashed counterparts. CloseTieRatio = top-5-counterparts share. LatePercent = events 22:00-06:00 / total. Style label from voice-vs-text balance."

    else ->
        "Pulls from unified data_points table, filters by collector_id and time range, applies per-card aggregation/heuristic, persists results in InsightsState. No external network. All computation runs on-device against the local SQLCipher database."
}

@Composable
private fun VoiceContextCard(data: VoiceContextInsight, meta: InsightMeta? = null, showDiagnostics: Boolean = false) {
    val snapshot = buildList {
        add("samples_7d" to "${data.samples7d}")
        add("conversation_samples" to "${data.conversationSamples}")
        add("avg_speech_density_wpm" to "%.2f".format(data.avgSpeechDensityWpm))
        data.topContexts.take(5).forEach { (ctx, count) ->
            add("context_$ctx" to "$count")
        }
        data.topTags.take(5).forEach { (tag, count) ->
            add("tag_$tag" to "$count")
        }
        add("latest_transcript" to (data.latestTranscript?.take(200) ?: "—"))
    }
    InsightCardShell(title = "Voice Context", icon = Icons.Default.Mic, accent = insightAccentForTitle("Voice Context"), meta = meta, showDiagnostics = showDiagnostics, dataSnapshot = snapshot) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell(Icons.Default.Mic, "${data.conversationSamples}/${data.samples7d}", "speech windows")
            StatCell(Icons.Default.Speed, "${"%.0f".format(data.avgSpeechDensityWpm)}", "words/min")
        }
        if (data.topContexts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Contexts", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DimGray)
            Spacer(Modifier.height(4.dp))
            data.topContexts.take(4).forEach { (context, count) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(context.replace('_', ' '), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    Text("$count", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalBlue)
                }
            }
        }
        if (data.topTags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                data.topTags.joinToString("  ") { (tag, count) -> "$tag:$count" },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TerminalAmber
            )
        }
        data.latestTranscript?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DimGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
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

private fun formatExactNumber(n: Long): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).format(n)

private fun formatDatabaseSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "${"%.1f".format(mb / 1024.0)} GB"
    else "${"%.1f".format(mb)} MB"
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
