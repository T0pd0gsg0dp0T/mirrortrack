package com.potpal.mirrortrack.ui.insights

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.collectors.personal.VoiceModelInstaller
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.entities.DataPointEntity
import com.potpal.mirrortrack.settings.CollectorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.sqrt

// ── Insight metadata ─────────────────────────────────────────────────

/**
 * Confidence tier — drives visual treatment of each card.
 * LOW shows "Based on limited data", MODERATE shows a dimmed badge,
 * HIGH shows nothing (default), STALE shows a staleness warning.
 */
enum class ConfidenceTier { LOW, MODERATE, HIGH }

/**
 * Per-card metadata tracking data provenance, quality, and freshness.
 */
data class InsightMeta(
    val confidence: ConfidenceTier = ConfidenceTier.HIGH,
    val dataSource: String = "primary",       // "primary", "fallback:app_lifecycle", etc.
    val dataPointCount: Int = 0,              // how many data points backed this card
    val newestDataMs: Long = 0L,              // timestamp of freshest input
    val isStale: Boolean = false,             // true if newest data > 2x poll interval
    val attempted: List<String> = emptyList() // sources tried: ["screen_state", "app_lifecycle"]
) {
    val age: Long get() = if (newestDataMs > 0) System.currentTimeMillis() - newestDataMs else Long.MAX_VALUE
    val ageLabel: String get() = when {
        age < 3_600_000 -> "< 1h old"
        age < 86_400_000 -> "${age / 3_600_000}h old"
        else -> "${age / 86_400_000}d old"
    }
}

/**
 * Wraps any insight computation result with its metadata.
 */
data class InsightResult<T>(
    val data: T,
    val meta: InsightMeta
)

// ── Collector → Insight dependency graph ─────────────────────────────

/**
 * Maps each insight card to its primary and fallback collector IDs.
 * Used by Settings to show "Enables N insight cards" hints, and by
 * the diagnostic overlay to show data provenance.
 */
object InsightDependencyGraph {
    data class CardDeps(
        val cardName: String,
        val primary: List<String>,
        val fallbacks: List<String>
    )

    val cards: List<CardDeps> = listOf(
        CardDeps("Today", listOf("screen_state", "steps", "battery"), listOf("app_lifecycle", "usage_stats", "motion_sensor")),
        CardDeps("Sleep", listOf("screen_state", "environment_sensor", "ambient_sound"), listOf("app_lifecycle", "voice_transcription")),
        CardDeps("App Attention", listOf("usage_stats"), listOf("logcat")),
        CardDeps("Anomalies", listOf("screen_state"), listOf("app_lifecycle")),
        CardDeps("Location Clusters", listOf("location"), emptyList()),
        CardDeps("Unlock Latency", listOf("screen_state", "notification_listener"), listOf("app_lifecycle")),
        CardDeps("Fingerprint", listOf("build_info", "hardware", "identifiers"), emptyList()),
        CardDeps("Monthly Trends", listOf("screen_state", "steps"), listOf("app_lifecycle")),
        CardDeps("Engagement", listOf("screen_state"), listOf("app_lifecycle")),
        CardDeps("Privacy Radar", listOf("appops_audit"), listOf("privacy_dashboard")),
        CardDeps("Data Flow", listOf("network_usage"), emptyList()),
        CardDeps("App Compulsion", listOf("logcat"), listOf("usage_stats")),
        CardDeps("Device Health", listOf("system_stats"), listOf("battery")),
        CardDeps("Identity Entropy", listOf("build_info", "hardware", "identifiers"), emptyList()),
        CardDeps("Home/Work", listOf("location"), emptyList()),
        CardDeps("Circadian", listOf("screen_state"), listOf("app_lifecycle", "logcat", "voice_transcription")),
        CardDeps("Routine", listOf("screen_state"), listOf("app_lifecycle", "logcat", "voice_transcription")),
        CardDeps("Social Pressure", listOf("notification_listener", "screen_state"), listOf("app_lifecycle")),
        CardDeps("App Portfolio", listOf("installed_apps"), listOf("usage_stats", "logcat")),
        CardDeps("Charging", listOf("battery"), emptyList()),
        CardDeps("WiFi Footprint", listOf("wifi"), listOf("connectivity")),
        CardDeps("Session Fragmentation", listOf("screen_state", "logcat"), listOf("app_lifecycle")),
        CardDeps("Dwell Times", listOf("location"), emptyList()),
        CardDeps("Weekday/Weekend", listOf("screen_state", "usage_stats"), listOf("app_lifecycle")),
        CardDeps("Income", listOf("build_info", "carrier", "installed_apps"), listOf("connectivity", "usage_stats", "logcat")),
        CardDeps("Commute", listOf("location"), emptyList()),
        CardDeps("Voice Context", listOf("voice_transcription"), emptyList())
    )

    /** Returns how many insight cards this collector feeds (primary + fallback). */
    fun cardCountForCollector(collectorId: String): Int =
        cards.count { collectorId in it.primary || collectorId in it.fallbacks }

    /** Returns card names that depend on this collector. */
    fun cardsForCollector(collectorId: String): List<String> =
        cards.filter { collectorId in it.primary || collectorId in it.fallbacks }.map { it.cardName }

    /** Returns all collector IDs needed by any card (primary + fallback). */
    fun allCollectorIds(): Set<String> =
        cards.flatMap { it.primary + it.fallbacks }.toSet()
}

// ── Data models ──────────────────────────────────────────────────────

data class InsightsState(
    val loading: Boolean = true,
    val today: TodayData? = null,
    val sleepDays: List<SleepDay> = emptyList(),
    val sleepIntervals72h: List<SleepInterval> = emptyList(),
    val appAttention: List<AppAttention> = emptyList(),
    val anomalies: List<Anomaly> = emptyList(),
    val locationClusters: List<LocationCluster> = emptyList(),
    val unlockLatencies: List<UnlockLatency> = emptyList(),
    val fingerprint: List<FingerprintField> = emptyList(),
    // Trends
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    // ADB-powered insights
    val engagement: EngagementScore? = null,
    val privacyRadar: List<PrivacyRadarEntry> = emptyList(),
    val dataFlow: List<DataFlowEntry> = emptyList(),
    val appCompulsion: List<AppCompulsion> = emptyList(),
    val deviceHealth: DeviceHealth? = null,
    val identityEntropy: IdentityEntropy? = null,
    // Behavioral inferences
    val homeWork: HomeWorkInference? = null,
    val circadian: CircadianProfile? = null,
    val routine: RoutinePredictability? = null,
    val socialPressure: List<SocialPressureEntry> = emptyList(),
    val appPortfolio: AppPortfolioProfile? = null,
    val charging: ChargingBehavior? = null,
    val wifiFootprint: WiFiFootprint? = null,
    val sessionFrag: SessionFragmentation? = null,
    val dwellTimes: List<DwellTimeEntry> = emptyList(),
    val weekdayWeekend: WeekdayWeekendDelta? = null,
    val income: IncomeInference? = null,
    val commute: CommutePattern? = null,
    val voiceContext: VoiceContextInsight? = null,
    // Collection summary
    val categoryCounts: List<CategoryCount> = emptyList(),
    val totalDataPoints: Long = 0L,
    // Per-card metadata
    val cardMeta: Map<String, InsightMeta> = emptyMap(),
    // Diagnostics toggle
    val showDiagnostics: Boolean = false
)

data class CategoryCount(
    val name: String,
    val displayName: String,
    val count: Long,
    val icon: String   // category enum name, resolved to icon in UI
)

data class TodayData(
    val dataPoints: Long,
    val unlocks: Int,
    val screenTimeMs: Long,
    val steps: Long,
    val batteryDeltaPct: Int,
    val activeCollectors: Int
)

data class SleepDay(
    val date: LocalDate,
    val sleepDurationHrs: Double,
    val bedtimeHour: Double?,
    val wakeHour: Double?
)

data class SleepInterval(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val confidence: Double,
    val evidence: List<String>,
    val averageLux: Double?,
    val averageSoundDbfs: Double?
)

data class AppAttention(
    val packageName: String,
    val foregroundMs7d: Long,
    val baselineDeltaMs: Long
)

data class Anomaly(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long
)

data class LocationCluster(
    val id: String,
    val lat: Double,
    val lon: Double,
    val fixCount: Int,
    val name: String?
)

data class UnlockLatency(
    val packageName: String,
    val medianLatencyMs: Long,
    val sampleCount: Int
)

data class FingerprintField(
    val label: String,
    val currentValue: String,
    val lastChangedMs: Long?
)

data class MonthlyTrend(
    val month: String,             // "2026-03"
    val dataPoints: Long,
    val avgDailyUnlocks: Double,
    val avgDailyScreenTimeMs: Long,
    val totalSteps: Long
)

data class EngagementScore(
    val dauWauRatio: Double,       // DAU/WAU stickiness (0.0–1.0)
    val avgSessionsPerDay: Double,
    val avgSessionDurationMs: Long,
    val totalSessions7d: Int,
    val activeDays7d: Int,
    val retentionDay7: Boolean,    // had data 7 days ago
    val retentionDay30: Boolean    // had data 30 days ago
)

data class PrivacyRadarEntry(
    val packageName: String,
    val cameraAccesses: Int,
    val micAccesses: Int,
    val locationAccesses: Int,
    val contactAccesses: Int,
    val lastAccessMs: Long,
    val privacyScore: Int          // 0–100, higher = more invasive
)

data class DataFlowEntry(
    val packageName: String,
    val totalBytes: Long,
    val txBytes: Long,
    val rxBytes: Long,
    val txRxRatio: Double,         // >1.0 = sending more than receiving
    val isSuspicious: Boolean      // tx/rx ratio anomalously high
)

data class AppCompulsion(
    val packageName: String,
    val launchCount: Int,
    val avgGapMinutes: Double      // avg time between launches
)

data class DeviceHealth(
    val ramUsedPct: Double,
    val processCount: Int,
    val foregroundCount: Int,
    val backgroundCount: Int,
    val thermalStatus: String,
    val uptimeHours: Double,
    val memoryTrend: Double        // delta in used% over last 24h
)

data class IdentityEntropy(
    val totalBits: Double,         // sum of per-field entropy
    val fields: List<EntropyField>
)

data class EntropyField(
    val name: String,
    val value: String,
    val entropyBits: Double        // log2(cardinality) for known field types
)

// ── Behavioral inference models ──────────────────────────────────────

data class HomeWorkInference(
    val homeCluster: LocationCluster?,
    val workCluster: LocationCluster?,
    val commuteDistanceKm: Double?,
    val avgCommuteStartHour: Double?,
    val avgCommuteEndHour: Double?
)

data class CircadianProfile(
    val hourlyUnlocks: List<Int>,       // 24 entries, index = hour of day
    val peakHour: Int,
    val troughHour: Int,
    val chronotype: String,             // "early_bird", "night_owl", "bimodal", "shift_worker"
    val activitySpreadHrs: Double       // hours between first and last activity
)

data class RoutinePredictability(
    val overallScore: Double,           // 0.0–1.0, 1.0 = perfectly predictable
    val hourlyEntropy: List<Double>,    // 24 entries, bits of entropy per hour
    val mostPredictableHour: Int,
    val leastPredictableHour: Int,
    val weekdayVsWeekendShift: Double   // cosine distance between weekday/weekend patterns
)

data class SocialPressureEntry(
    val packageName: String,
    val notificationCount: Int,
    val medianResponseMs: Long,
    val responseRate: Double,           // fraction that led to unlock within 10m
    val pressureScore: Double           // notifs * responseRate / medianResponse
)

data class AppPortfolioProfile(
    val totalApps: Int,
    val systemApps: Int,
    val userApps: Int,
    val categories: Map<String, Int>,   // inferred category → count
    val inferences: List<String>        // "likely_parent", "finance_engaged", etc.
)

data class ChargingBehavior(
    val avgChargesPerDay: Double,
    val avgDischargeDepthPct: Double,   // how low before plugging in
    val overnightCharger: Boolean,      // typically gains charge midnight-6AM
    val avgChargeDurationMs: Long,
    val typicalChargeHour: Int          // most common hour to start charging
)

data class WiFiFootprint(
    val uniqueNetworks7d: Int,
    val totalScans: Int,
    val topNetworks: List<Pair<String, Int>>,  // SSID → seen count
    val mobilityScore: Double,          // 0.0 = static, 1.0 = highly mobile
    val homeNetwork: String?            // most frequent overnight network
)

data class SessionFragmentation(
    val avgSwitchesPerSession: Double,
    val avgSessionDepthMs: Long,        // time in single app before switching
    val mostFragmentedHour: Int,
    val leastFragmentedHour: Int,
    val attentionScore: Double          // 0.0–1.0, 1.0 = deep focus
)

data class DwellTimeEntry(
    val clusterId: String,
    val clusterName: String?,
    val totalDwellMs: Long,
    val visitCount: Int,
    val avgDwellMs: Long,
    val classification: String          // "home", "work", "transit", "retail", "social"
)

data class WeekdayWeekendDelta(
    val weekdayAvgUnlocks: Double,
    val weekendAvgUnlocks: Double,
    val weekdayAvgScreenMs: Long,
    val weekendAvgScreenMs: Long,
    val weekdayTopApps: List<String>,
    val weekendTopApps: List<String>,
    val balanceScore: Double            // 0.0 = identical, 1.0 = completely different
)

data class IncomeInference(
    val deviceTier: String,             // "budget", "mid_range", "flagship", "ultra"
    val estimatedDevicePrice: Int,      // USD estimate based on model
    val carrierTier: String,            // "mvno", "prepaid", "postpaid", "premium"
    val appSignals: List<String>,       // "has_trading_app", "multiple_streaming", etc.
    val overallTier: String             // "low", "mid", "high", "affluent"
)

data class CommutePattern(
    val detected: Boolean,
    val avgDepartureHour: Double,
    val avgReturnHour: Double,
    val avgDurationMinutes: Double,
    val transportMode: String,          // "walking", "driving", "transit"
    val consistencyScore: Double        // 0.0–1.0
)

data class VoiceContextInsight(
    val samples7d: Int,
    val conversationSamples: Int,
    val avgSpeechDensityWpm: Double,
    val topContexts: List<Pair<String, Int>>,
    val topTags: List<Pair<String, Int>>,
    val latestTranscript: String?
)

// ── ViewModel ────────────────────────────────────────────────────────

@HiltViewModel
class InsightsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataPointDao,
    private val prefs: CollectorPreferences,
    private val registry: CollectorRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state

    private val zone: ZoneId = ZoneId.systemDefault()

    // Accumulates metadata from each compute function during refresh
    private val metaAccumulator = mutableMapOf<String, InsightMeta>()

    init {
        refresh()
    }

    fun toggleDiagnostics() {
        _state.update { it.copy(showDiagnostics = !it.showDiagnostics) }
    }

    /**
     * Build InsightMeta from data points used by a card.
     * @param cardKey unique card identifier for the meta map
     * @param source description of the data source used ("primary", "fallback:app_lifecycle", etc.)
     * @param count number of data points that backed the computation
     * @param newestMs timestamp of the freshest data point used
     * @param attempted list of sources tried in order
     * @param staleThresholdMs if newest data is older than this, mark stale (default 2h)
     */
    private fun buildMeta(
        cardKey: String,
        source: String,
        count: Int,
        newestMs: Long,
        attempted: List<String>,
        staleThresholdMs: Long = 7_200_000L
    ): InsightMeta {
        val confidence = when {
            source == "primary" && count >= 50 -> ConfidenceTier.HIGH
            source == "primary" && count >= 20 -> ConfidenceTier.MODERATE
            source.startsWith("fallback") && count >= 50 -> ConfidenceTier.MODERATE
            source.startsWith("fallback") && count >= 20 -> ConfidenceTier.LOW
            count < 20 -> ConfidenceTier.LOW
            else -> ConfidenceTier.MODERATE
        }
        val isStale = newestMs > 0 && (System.currentTimeMillis() - newestMs) > staleThresholdMs
        val meta = InsightMeta(confidence, source, count, newestMs, isStale, attempted)
        metaAccumulator[cardKey] = meta
        return meta
    }

    /**
     * Auto-generate metadata for cards that don't have explicit buildMeta calls.
     * Queries DAO for freshness of primary collectors. Only creates meta if
     * not already set by an explicit buildMeta call inside the compute function.
     */
    private suspend fun autoMeta(
        cardKey: String,
        hasData: Boolean,
        primaryCollectors: List<String>,
        fallbackCollectors: List<String>
    ) {
        if (metaAccumulator.containsKey(cardKey)) return // already set by compute function
        if (!hasData) {
            metaAccumulator[cardKey] = InsightMeta(
                confidence = ConfidenceTier.LOW,
                dataSource = "none",
                dataPointCount = 0,
                newestDataMs = 0L,
                isStale = true,
                attempted = primaryCollectors + fallbackCollectors
            )
            return
        }
        // Check which collectors have data
        var source = "primary"
        var newestMs = 0L
        var totalCount = 0
        for (cid in primaryCollectors) {
            val count = dao.countByCollector(cid)
            if (count > 0) {
                totalCount += count.toInt()
                val latest = dao.byCollector(cid, 1).firstOrNull()
                if (latest != null && latest.timestamp > newestMs) newestMs = latest.timestamp
            }
        }
        if (totalCount == 0) {
            source = "fallback"
            for (cid in fallbackCollectors) {
                val count = dao.countByCollector(cid)
                if (count > 0) {
                    totalCount += count.toInt()
                    source = "fallback:$cid"
                    val latest = dao.byCollector(cid, 1).firstOrNull()
                    if (latest != null && latest.timestamp > newestMs) newestMs = latest.timestamp
                }
            }
        }
        val confidence = when {
            source == "primary" && totalCount >= 50 -> ConfidenceTier.HIGH
            source == "primary" && totalCount >= 10 -> ConfidenceTier.MODERATE
            source.startsWith("fallback") -> ConfidenceTier.LOW
            else -> ConfidenceTier.LOW
        }
        val isStale = newestMs > 0 && (System.currentTimeMillis() - newestMs) > 7_200_000L
        metaAccumulator[cardKey] = InsightMeta(confidence, source, totalCount, newestMs, isStale,
            primaryCollectors + fallbackCollectors)
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(loading = true) }
            }
            metaAccumulator.clear()

            // Load collection summary
            val catCountsDef = async {
                Category.entries.map { cat ->
                    val collectors = registry.byCategory(cat)
                    val total = collectors.sumOf { dao.countByCollector(it.id) }
                    CategoryCount(cat.name, cat.displayName, total, cat.name)
                }
            }
            val totalDef = async { dao.count() }

            val todayDef = async { computeToday() }
            val sleepDef = async { computeSleep() }
            val sleepIntervalsDef = async { computeSleepIntervals72h() }
            val appsDef = async { computeAppAttention() }
            val anomalyDef = async { computeAnomalies() }
            val locDef = async { computeLocationClusters() }
            val latencyDef = async { computeUnlockLatencies() }
            val fpDef = async { computeFingerprint() }
            val trendsDef = async { computeMonthlyTrends() }
            val engageDef = async { computeEngagement() }
            val privacyDef = async { computePrivacyRadar() }
            val flowDef = async { computeDataFlow() }
            val compulsionDef = async { computeAppCompulsion() }
            val healthDef = async { computeDeviceHealth() }
            val entropyDef = async { computeIdentityEntropy() }
            val homeWorkDef = async { computeHomeWork() }
            val circadianDef = async { computeCircadian() }
            val routineDef = async { computeRoutine() }
            val socialDef = async { computeSocialPressure() }
            val portfolioDef = async { computeAppPortfolio() }
            val chargingDef = async { computeCharging() }
            val wifiDef = async { computeWifiFootprint() }
            val fragDef = async { computeSessionFragmentation() }
            val dwellDef = async { computeDwellTimes() }
            val wdweDef = async { computeWeekdayWeekend() }
            val incomeDef = async { computeIncome() }
            val commuteDef = async { computeCommute() }
            val voiceDef = async { computeVoiceContext() }

            val today = todayDef.await()
            val sleepDays = sleepDef.await()
            val sleepIntervals72h = sleepIntervalsDef.await()
            val appAttention = appsDef.await()
            val anomalies = anomalyDef.await()
            val locationClusters = locDef.await()
            val unlockLatencies = latencyDef.await()
            val fingerprint = fpDef.await()
            val monthlyTrends = trendsDef.await()
            val engagement = engageDef.await()
            val privacyRadar = privacyDef.await()
            val dataFlow = flowDef.await()
            val appCompulsion = compulsionDef.await()
            val deviceHealth = healthDef.await()
            val identityEntropy = entropyDef.await()
            val homeWork = homeWorkDef.await()
            val circadian = circadianDef.await()
            val routine = routineDef.await()
            val socialPressure = socialDef.await()
            val appPortfolio = portfolioDef.await()
            val charging = chargingDef.await()
            val wifiFootprint = wifiDef.await()
            val sessionFrag = fragDef.await()
            val dwellTimes = dwellDef.await()
            val weekdayWeekend = wdweDef.await()
            val income = incomeDef.await()
            val commute = commuteDef.await()
            val voiceContext = voiceDef.await()

            // Auto-generate metadata for any card not already tracked by buildMeta
            autoMeta("engagement", engagement != null, listOf("screen_state"), listOf("app_lifecycle"))
            autoMeta("privacy", privacyRadar.isNotEmpty(), listOf("appops_audit"), listOf("privacy_dashboard"))
            autoMeta("dataflow", dataFlow.isNotEmpty(), listOf("network_usage"), emptyList())
            autoMeta("compulsion", appCompulsion.isNotEmpty(), listOf("logcat"), listOf("usage_stats"))
            autoMeta("health", deviceHealth != null, listOf("system_stats"), listOf("battery"))
            autoMeta("entropy", identityEntropy != null, listOf("build_info", "hardware", "identifiers"), emptyList())
            autoMeta("homework", homeWork != null, listOf("location"), emptyList())
            autoMeta("circadian", circadian != null, listOf("screen_state"), listOf("app_lifecycle", "logcat", "voice_transcription"))
            autoMeta("routine", routine != null, listOf("screen_state"), listOf("app_lifecycle", "logcat", "voice_transcription"))
            autoMeta("social", socialPressure.isNotEmpty(), listOf("notification_listener", "screen_state"), listOf("app_lifecycle"))
            autoMeta("portfolio", appPortfolio != null, listOf("installed_apps"), listOf("usage_stats", "logcat"))
            autoMeta("charging", charging != null, listOf("battery"), emptyList())
            autoMeta("wifi", wifiFootprint != null, listOf("wifi"), listOf("connectivity"))
            autoMeta("fragmentation", sessionFrag != null, listOf("screen_state", "logcat"), listOf("app_lifecycle"))
            autoMeta("dwell", dwellTimes.isNotEmpty(), listOf("location"), emptyList())
            autoMeta("weekdayweekend", weekdayWeekend != null, listOf("screen_state", "usage_stats"), listOf("app_lifecycle"))
            autoMeta("income", income != null, listOf("build_info", "carrier"), listOf("connectivity", "usage_stats"))
            autoMeta("commute", commute?.detected == true, listOf("location"), emptyList())
            autoMeta("voice", voiceContext != null, listOf("voice_transcription"), emptyList())

            _state.update { it.copy(
                loading = false,
                categoryCounts = catCountsDef.await(),
                totalDataPoints = totalDef.await(),
                today = today,
                sleepDays = sleepDays,
                sleepIntervals72h = sleepIntervals72h,
                appAttention = appAttention,
                anomalies = anomalies,
                locationClusters = locationClusters,
                unlockLatencies = unlockLatencies,
                fingerprint = fingerprint,
                monthlyTrends = monthlyTrends,
                engagement = engagement,
                privacyRadar = privacyRadar,
                dataFlow = dataFlow,
                appCompulsion = appCompulsion,
                deviceHealth = deviceHealth,
                identityEntropy = identityEntropy,
                homeWork = homeWork,
                circadian = circadian,
                routine = routine,
                socialPressure = socialPressure,
                appPortfolio = appPortfolio,
                charging = charging,
                wifiFootprint = wifiFootprint,
                sessionFrag = sessionFrag,
                dwellTimes = dwellTimes,
                weekdayWeekend = weekdayWeekend,
                income = income,
                commute = commute,
                voiceContext = voiceContext,
                cardMeta = metaAccumulator.toMap()
            ) }
        }
    }

    fun dismissAnomaly(id: String) {
        viewModelScope.launch {
            prefs.dismissAnomaly(id)
            _state.update { s ->
                s.copy(anomalies = s.anomalies.filter { it.id != id })
            }
        }
    }

    fun renameCluster(clusterId: String, name: String) {
        viewModelScope.launch {
            prefs.setClusterName(clusterId, name)
            _state.update { s ->
                s.copy(locationClusters = s.locationClusters.map { c ->
                    if (c.id == clusterId) c.copy(name = name) else c
                })
            }
        }
    }

    // ── Card 1: Today ────────────────────────────────────────────────

    private suspend fun computeToday(): TodayData {
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val attempted = mutableListOf<String>()
        var source = "primary"
        var totalCount = 0

        val dataPoints = dao.countSince(todayStart)

        val screenEvents = dao.byCollectorKeySince("screen_state", "event", todayStart)
        attempted.add("screen_state")
        totalCount += screenEvents.size

        // Unlocks: primary screen_state, fallback app_lifecycle foreground events
        var unlocks = screenEvents.count { it.value == "screen_on" }
        if (unlocks == 0) {
            val lifecycleEvents = dao.byCollectorKeySince("app_lifecycle", "app_foreground", todayStart)
            attempted.add("app_lifecycle")
            totalCount += lifecycleEvents.size
            unlocks = lifecycleEvents.size
            if (unlocks > 0) source = "fallback:app_lifecycle"
        }

        // Screen time: primary screen_state on/off pairs
        var screenTimeMs = 0L
        val sorted = screenEvents.sortedBy { it.timestamp }
        var lastOnTime: Long? = null
        for (e in sorted) {
            when (e.value) {
                "screen_on" -> lastOnTime = e.timestamp
                "screen_off" -> {
                    if (lastOnTime != null) {
                        screenTimeMs += e.timestamp - lastOnTime
                        lastOnTime = null
                    }
                }
            }
        }
        if (lastOnTime != null) {
            screenTimeMs += System.currentTimeMillis() - lastOnTime
        }
        // Fallback: app_lifecycle duration_ms entries
        if (screenTimeMs == 0L) {
            val durations = dao.byCollectorKeySince("app_lifecycle", "duration_ms", todayStart)
            screenTimeMs = durations.mapNotNull { it.value.toLongOrNull() }.sum()
        }
        // Fallback 2: usage_stats total foreground
        if (screenTimeMs == 0L) {
            val usageData = dao.byCollectorSince("usage_stats", todayStart)
                .filter { it.key.startsWith("package_usage:") }
            screenTimeMs = usageData.mapNotNull { parseForegroundMs(it.value) }.maxOrNull() ?: 0L
        }

        // Steps: primary steps collector, fallback motion_sensor step_counter
        val stepEvents = dao.byCollectorKeySince("steps", "step_counter_delta", todayStart)
        var steps = stepEvents.mapNotNull { it.value.toLongOrNull() }.sum()
        if (steps == 0L) {
            val motionSteps = dao.byCollectorKeySince("motion_sensor", "step_counter", todayStart)
            if (motionSteps.size >= 2) {
                val first = motionSteps.minByOrNull { it.timestamp }?.value?.toLongOrNull() ?: 0L
                val last = motionSteps.maxByOrNull { it.timestamp }?.value?.toLongOrNull() ?: 0L
                steps = (last - first).coerceAtLeast(0)
            }
        }

        val batteryToday = dao.byCollectorKeySince("battery", "level", todayStart)
            .mapNotNull { it.value.toIntOrNull() }
        val batteryDelta = if (batteryToday.size >= 2) {
            batteryToday.last() - batteryToday.first()
        } else 0

        val collectors = dao.countByCollectorSince(todayStart)

        val newestMs = screenEvents.maxOfOrNull { it.timestamp } ?: 0L
        buildMeta("today", source, totalCount, newestMs, attempted, staleThresholdMs = 3_600_000L)

        return TodayData(
            dataPoints = dataPoints,
            unlocks = unlocks,
            screenTimeMs = screenTimeMs,
            steps = steps,
            batteryDeltaPct = batteryDelta,
            activeCollectors = collectors.size
        )
    }

    // ── Card 2: Sleep heatmap ────────────────────────────────────────

    private suspend fun computeSleep(): List<SleepDay> {
        val ninetyDaysAgo = LocalDate.now().minusDays(90)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val attempted = mutableListOf("screen_state")
        var source = "primary"

        var events = dao.byCollectorKeySince("screen_state", "event", ninetyDaysAgo)
            .sortedBy { it.timestamp }

        // Fallback: synthesize screen-like events from app_lifecycle
        if (events.size < 10) {
            attempted.add("app_lifecycle")
            val lifecycle = dao.byCollectorSince("app_lifecycle", ninetyDaysAgo)
                .sortedBy { it.timestamp }
            if (lifecycle.size >= 10) {
                source = "fallback:app_lifecycle"
                events = lifecycle.mapNotNull { dp ->
                    when (dp.key) {
                        "app_foreground" -> dp.copy(value = "screen_on")
                        "app_background" -> dp.copy(value = "screen_off")
                        else -> null
                    }
                }.sortedBy { it.timestamp }
            }
        }

        if (events.size < 10) {
            buildMeta("sleep", source, events.size, events.maxOfOrNull { it.timestamp } ?: 0L, attempted)
            return emptyList()
        }

        // Build screen-off gaps
        data class Gap(val offTime: Long, val onTime: Long) {
            val durationMs get() = onTime - offTime
        }

        val gaps = mutableListOf<Gap>()
        var lastOffTime: Long? = null
        for (e in events) {
            when (e.value) {
                "screen_off" -> lastOffTime = e.timestamp
                "screen_on" -> {
                    val off = lastOffTime
                    if (off != null) {
                        val dur = e.timestamp - off
                        if (dur > 2 * 3_600_000) { // > 2 hours = potential sleep
                            gaps.add(Gap(off, e.timestamp))
                        }
                    }
                    lastOffTime = null
                }
            }
        }

        // For each date, find the longest overnight gap that overlaps with it
        val today = LocalDate.now()
        val result = mutableListOf<SleepDay>()

        for (dayOffset in 0L until 90L) {
            val date = today.minusDays(89 - dayOffset)
            // Sleep "night" = 8 PM day before to 12 PM this day
            val nightStart = date.minusDays(1).atTime(20, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val nightEnd = date.atTime(12, 0)
                .atZone(zone).toInstant().toEpochMilli()

            data class NightSleepGap(
                val bedtimeMs: Long,
                val wakeMs: Long,
                val durationMs: Long
            )

            val nightGaps = gaps.mapNotNull { g ->
                val clippedStart = maxOf(g.offTime, nightStart)
                val clippedEnd = minOf(g.onTime, nightEnd)
                val clippedDuration = clippedEnd - clippedStart
                if (clippedDuration > 2 * 3_600_000) {
                    NightSleepGap(clippedStart, clippedEnd, clippedDuration)
                } else {
                    null
                }
            }

            val longestGap = nightGaps.maxByOrNull { it.durationMs }

            if (longestGap != null) {
                val bedtime = hourOfDay(longestGap.bedtimeMs)
                val wake = hourOfDay(longestGap.wakeMs)
                result.add(SleepDay(
                    date = date,
                    sleepDurationHrs = longestGap.durationMs / 3_600_000.0,
                    bedtimeHour = bedtime,
                    wakeHour = wake
                ))
            } else {
                result.add(SleepDay(date = date, sleepDurationHrs = 0.0, bedtimeHour = null, wakeHour = null))
            }
        }

        buildMeta("sleep", source, events.size, events.maxOfOrNull { it.timestamp } ?: 0L, attempted,
            staleThresholdMs = 86_400_000L)
        return result
    }

    private suspend fun computeSleepIntervals72h(): List<SleepInterval> {
        val now = System.currentTimeMillis()
        val windowStart = now - 72 * 3_600_000L
        val lookbackStart = windowStart - 12 * 3_600_000L

        var events = dao.byCollectorKeySince("screen_state", "event", lookbackStart)
            .sortedBy { it.timestamp }

        if (events.size < 4) {
            val lifecycle = dao.byCollectorSince("app_lifecycle", lookbackStart)
                .sortedBy { it.timestamp }
            if (lifecycle.size >= 4) {
                events = lifecycle.mapNotNull { dp ->
                    when (dp.key) {
                        "app_foreground" -> dp.copy(value = "screen_on")
                        "app_background" -> dp.copy(value = "screen_off")
                        else -> null
                    }
                }.sortedBy { it.timestamp }
            }
        }

        if (events.size < 2) return emptyList()

        val lightSamples = dao.byCollectorKeySince(
            "environment_sensor",
            "ambient_light_lux",
            lookbackStart
        ).sortedBy { it.timestamp }
        val soundSamples = dao.byCollectorKeySince(
            "ambient_sound",
            "ambient_sound_dbfs",
            lookbackStart
        ).sortedBy { it.timestamp }
        val conversationSamples = dao.byCollectorKeySince(
            "voice_transcription",
            "conversation_present",
            lookbackStart
        ).sortedBy { it.timestamp }
        val speechDensitySamples = dao.byCollectorKeySince(
            "voice_transcription",
            "speech_density_wpm",
            lookbackStart
        ).sortedBy { it.timestamp }

        val intervals = mutableListOf<SleepInterval>()
        var lastOffTime: Long? = null
        for (e in events) {
            when (e.value) {
                "screen_off" -> lastOffTime = e.timestamp
                "screen_on" -> {
                    val off = lastOffTime
                    if (off != null) {
                        val clippedStart = maxOf(off, windowStart)
                        val clippedEnd = minOf(e.timestamp, now)
                        val clippedDuration = clippedEnd - clippedStart
                        if (clippedDuration > 2 * 3_600_000L) {
                            val evidence = scoreSleepEvidence(
                                startMs = clippedStart,
                                endMs = clippedEnd,
                                durationMs = clippedDuration,
                                lightSamples = lightSamples,
                                soundSamples = soundSamples,
                                conversationSamples = conversationSamples,
                                speechDensitySamples = speechDensitySamples
                            )
                            if (evidence.confidence >= 0.4) {
                                intervals.add(
                                    SleepInterval(
                                        startMs = clippedStart,
                                        endMs = clippedEnd,
                                        durationMs = clippedDuration,
                                        confidence = evidence.confidence,
                                        evidence = evidence.evidence,
                                        averageLux = evidence.averageLux,
                                        averageSoundDbfs = evidence.averageSoundDbfs
                                    )
                                )
                            }
                        }
                    }
                    lastOffTime = null
                }
            }
        }

        return intervals.sortedBy { it.startMs }
    }

    private fun scoreSleepEvidence(
        startMs: Long,
        endMs: Long,
        durationMs: Long,
        lightSamples: List<DataPointEntity>,
        soundSamples: List<DataPointEntity>,
        conversationSamples: List<DataPointEntity>,
        speechDensitySamples: List<DataPointEntity>
    ): SleepEvidenceScore {
        val evidence = mutableListOf("phone inactive")
        var confidence = 0.45

        val luxValues = lightSamples
            .filter { it.timestamp in startMs..endMs }
            .mapNotNull { it.value.toDoubleOrNull() }
        val averageLux = luxValues.average().takeIf { luxValues.isNotEmpty() && !it.isNaN() }
        if (luxValues.isNotEmpty()) {
            val darkRatio = luxValues.count { it <= SLEEP_DARK_LUX } / luxValues.size.toDouble()
            when {
                darkRatio >= 0.6 -> {
                    confidence += 0.25
                    evidence += "dark room (${averageLux?.let { "%.0f".format(it) } ?: "?"} lux)"
                }
                averageLux != null && averageLux >= 50.0 -> {
                    confidence -= 0.15
                    evidence += "light present (${"%.0f".format(averageLux)} lux)"
                }
                else -> evidence += "dim light"
            }
        } else {
            evidence += "no light data"
        }

        val soundValues = soundSamples
            .filter { it.timestamp in startMs..endMs }
            .mapNotNull { it.value.toDoubleOrNull() }
        val averageSoundDbfs = soundValues.average().takeIf { soundValues.isNotEmpty() && !it.isNaN() }
        if (soundValues.isNotEmpty()) {
            val quietRatio = soundValues.count { it <= SLEEP_QUIET_DBFS } / soundValues.size.toDouble()
            when {
                quietRatio >= 0.6 -> {
                    confidence += 0.20
                    evidence += "quiet room (${averageSoundDbfs?.let { "%.0f".format(it) } ?: "?"} dBFS)"
                }
                averageSoundDbfs != null && averageSoundDbfs >= -35.0 -> {
                    confidence -= 0.15
                    evidence += "sound present (${"%.0f".format(averageSoundDbfs)} dBFS)"
                }
                else -> evidence += "moderate sound"
            }
        } else {
            val conversationValues = conversationSamples
                .filter { it.timestamp in startMs..endMs }
                .map { it.value.toBooleanStrictOrNull() ?: false }
            val densityValues = speechDensitySamples
                .filter { it.timestamp in startMs..endMs }
                .mapNotNull { it.value.toDoubleOrNull() }
            val quietVoiceWindows = conversationValues.count { !it } + densityValues.count { it < 5.0 }
            val totalVoiceWindows = conversationValues.size + densityValues.size
            if (totalVoiceWindows > 0 && quietVoiceWindows / totalVoiceWindows.toDouble() >= 0.6) {
                confidence += 0.12
                evidence += "little speech"
            } else if (totalVoiceWindows == 0) {
                evidence += "no sound data"
            }
        }

        when {
            durationMs >= 6 * 3_600_000L -> confidence += 0.10
            durationMs >= 4 * 3_600_000L -> confidence += 0.05
        }

        return SleepEvidenceScore(
            confidence = confidence.coerceIn(0.1, 0.95),
            evidence = evidence,
            averageLux = averageLux,
            averageSoundDbfs = averageSoundDbfs
        )
    }

    private data class SleepEvidenceScore(
        val confidence: Double,
        val evidence: List<String>,
        val averageLux: Double?,
        val averageSoundDbfs: Double?
    )

    private companion object {
        const val SLEEP_DARK_LUX = 10.0
        const val SLEEP_QUIET_DBFS = -45.0
    }

    // ── Card 3: App attention ────────────────────────────────────────

    private suspend fun computeAppAttention(): List<AppAttention> {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86_400_000L
        val fourteenDaysAgo = now - 14 * 86_400_000L
        val attempted = mutableListOf("usage_stats")
        var source = "primary"

        // Primary: usage_stats
        val recent = dao.byCollectorSince("usage_stats", sevenDaysAgo)
            .filter { it.key.startsWith("package_usage:") }

        val currentMap = recent.groupBy { it.key.removePrefix("package_usage:") }
            .mapValues { (_, entries) ->
                entries.mapNotNull { parseForegroundMs(it.value) }.maxOrNull() ?: 0L
            }

        // Fallback: logcat focus entries as proxy (count × avg_poll_interval as rough ms)
        val effectiveMap = if (currentMap.isEmpty()) {
            attempted.add("logcat")
            val logcatData = dao.byCollectorSince("logcat", sevenDaysAgo)
                .filter { it.key.startsWith("focus:") }
            if (logcatData.isNotEmpty()) {
                source = "fallback:logcat"
                logcatData.groupBy { it.key.removePrefix("focus:") }
                    .mapValues { (_, entries) ->
                        // Sum launch counts, estimate ~2 min foreground per launch
                        entries.mapNotNull { it.value.toLongOrNull() }.sum() * 120_000L
                    }
            } else currentMap
        } else currentMap

        val baseline = dao.byCollectorSince("usage_stats", fourteenDaysAgo)
            .filter { it.key.startsWith("package_usage:") && it.timestamp < sevenDaysAgo }
            .groupBy { it.key.removePrefix("package_usage:") }
            .mapValues { (_, entries) ->
                entries.mapNotNull { parseForegroundMs(it.value) }.maxOrNull() ?: 0L
            }

        val newestMs = recent.maxOfOrNull { it.timestamp } ?: 0L
        buildMeta("apps", source, recent.size + effectiveMap.size, newestMs, attempted)

        return effectiveMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (pkg, fgMs) ->
                val baseMs = baseline[pkg] ?: 0L
                AppAttention(
                    packageName = pkg,
                    foregroundMs7d = fgMs,
                    baselineDeltaMs = fgMs - baseMs
                )
            }
    }

    private fun parseForegroundMs(json: String): Long? {
        val match = Regex(""""total_foreground_ms":(\d+)""").find(json)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    // ── Card 4: Anomaly feed ─────────────────────────────────────────

    private suspend fun computeAnomalies(): List<Anomaly> {
        val dismissed = prefs.getDismissedAnomalyIds()
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val sevenDaysAgo = now - 7 * 86_400_000L
        val anomalies = mutableListOf<Anomaly>()

        // Configurable thresholds
        val unlockSigmaThreshold = prefs.getAnomalyThresholdSync("unlock_sigma", 2.0f)
        val lateNightThreshold = prefs.getAnomalyThresholdSync("late_night_unlocks", 5.0f).toInt()
        val batteryDrainRateThreshold = prefs.getAnomalyThresholdSync("battery_drain_rate", 5.0f)
        val dataFlowTxRxThreshold = prefs.getAnomalyThresholdSync("data_flow_tx_rx_ratio", 3.0f)

        // 1. Unusual unlock count (primary: screen_state, fallback: app_lifecycle)
        var screenEventsWeek = dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo)
        if (screenEventsWeek.size < 5) {
            val lifecycle = dao.byCollectorSince("app_lifecycle", sevenDaysAgo)
            val synthesized = lifecycle.mapNotNull { dp ->
                when (dp.key) {
                    "app_foreground" -> dp.copy(value = "screen_on")
                    "app_background" -> dp.copy(value = "screen_off")
                    else -> null
                }
            }
            if (synthesized.size > screenEventsWeek.size) screenEventsWeek = synthesized
        }
        val unlocksByDay = screenEventsWeek
            .filter { it.value == "screen_on" }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { it.value.size }

        if (unlocksByDay.size >= 3) {
            val today = LocalDate.now()
            val todayUnlocks = unlocksByDay[today] ?: 0
            val priorDays = unlocksByDay.filter { it.key != today }.values.toList()
            if (priorDays.isNotEmpty()) {
                val mean = priorDays.average()
                val std = stdDev(priorDays.map { it.toDouble() })
                if (std > 0 && todayUnlocks > mean + unlockSigmaThreshold * std) {
                    anomalies.add(Anomaly(
                        id = "unlock_spike_$today",
                        title = "Unusual unlock activity",
                        description = "$todayUnlocks unlocks today vs ${mean.toInt()} avg — ${((todayUnlocks - mean) / std).toInt()}σ above normal",
                        timestamp = now
                    ))
                }
            }
        }

        // 2. Late night usage (activity after usual bedtime)
        val todayScreenEvents = screenEventsWeek.filter {
            it.value == "screen_on" && it.timestamp >= todayStart
        }
        val lateNightUnlocks = todayScreenEvents.count { e ->
            val hour = hourOfDay(e.timestamp)
            hour >= 0 && hour < 5
        }
        if (lateNightUnlocks >= lateNightThreshold) {
            anomalies.add(Anomaly(
                id = "late_night_${LocalDate.now()}",
                title = "Late night usage spike",
                description = "$lateNightUnlocks unlocks between midnight and 5 AM",
                timestamp = now
            ))
        }

        // 3. Battery drain anomaly
        val batteryWeek = dao.byCollectorKeySince("battery", "level", sevenDaysAgo)
            .mapNotNull { dp -> dp.value.toIntOrNull()?.let { dp.timestamp to it } }
        if (batteryWeek.size >= 10) {
            val batteryToday = batteryWeek.filter { it.first >= todayStart }
            if (batteryToday.size >= 2) {
                val todayDrain = batteryToday.last().second - batteryToday.first().second
                val todayHours = ((batteryToday.last().first - batteryToday.first().first) / 3_600_000.0).coerceAtLeast(0.5)
                val todayRate = todayDrain / todayHours

                val priorBattery = batteryWeek.filter { it.first < todayStart }
                if (priorBattery.size >= 4) {
                    val priorDrain = priorBattery.last().second - priorBattery.first().second
                    val priorHours = ((priorBattery.last().first - priorBattery.first().first) / 3_600_000.0).coerceAtLeast(1.0)
                    val priorRate = priorDrain / priorHours

                    if (priorRate != 0.0 && todayRate < priorRate * 1.5 && todayRate < -batteryDrainRateThreshold) {
                        anomalies.add(Anomaly(
                            id = "battery_drain_${LocalDate.now()}",
                            title = "Unusual battery drain",
                            description = "Draining at ${"%.1f".format(-todayRate)}%/hr vs ${"%.1f".format(-priorRate)}%/hr baseline",
                            timestamp = now
                        ))
                    }
                }
            }
        }

        // 4. New location cluster
        val locAll = dao.byCollectorKeySince("location", "lat", sevenDaysAgo)
        val locToday = locAll.filter { it.timestamp >= todayStart }
        if (locToday.isNotEmpty() && locAll.size > locToday.size) {
            val todayCells = locToday.map { "%.3f".format(it.value.toDoubleOrNull() ?: 0.0) }.toSet()
            val priorCells = locAll.filter { it.timestamp < todayStart }
                .map { "%.3f".format(it.value.toDoubleOrNull() ?: 0.0) }.toSet()
            val newCells = todayCells - priorCells
            if (newCells.isNotEmpty()) {
                anomalies.add(Anomaly(
                    id = "new_location_${LocalDate.now()}",
                    title = "New location detected",
                    description = "${newCells.size} location grid cell(s) visited for the first time this week",
                    timestamp = now
                ))
            }
        }

        val allEvents = screenEventsWeek
        buildMeta("anomalies", if (screenEventsWeek.any { it.collectorId == "app_lifecycle" }) "fallback:app_lifecycle" else "primary",
            allEvents.size, allEvents.maxOfOrNull { it.timestamp } ?: 0L,
            listOf("screen_state", "app_lifecycle"))

        return anomalies
            .filter { it.id !in dismissed }
            .sortedByDescending { it.timestamp }
            .take(5)
    }

    // ── Card 5: Location clusters ────────────────────────────────────

    private suspend fun computeLocationClusters(): List<LocationCluster> {
        val locPoints = dao.byCollector("location", 5000)
        val latPoints = locPoints.filter { it.key == "lat" }
        val lonPoints = locPoints.filter { it.key == "lon" }

        // Pair lat/lon by timestamp proximity (same collection batch)
        val pairs = mutableListOf<Pair<Double, Double>>()
        for (lat in latPoints) {
            val latVal = lat.value.toDoubleOrNull() ?: continue
            val lon = lonPoints.minByOrNull { kotlin.math.abs(it.timestamp - lat.timestamp) }
            val lonVal = lon?.value?.toDoubleOrNull() ?: continue
            if (kotlin.math.abs(lon.timestamp - lat.timestamp) < 5000) {
                pairs.add(latVal to lonVal)
            }
        }

        if (pairs.isEmpty()) {
            buildMeta("location", "primary", 0, 0L, listOf("location"))
            return emptyList()
        }

        // Grid clustering at ~100m resolution
        data class ClusterData(val grid: String, val lat: Double, val lon: Double, val count: Int)

        val clusters = pairs.groupBy { (lat, lon) ->
            "%.3f,%.3f".format(lat, lon)
        }.map { (grid, points) ->
            val parts = grid.split(",")
            ClusterData(grid, parts[0].toDouble(), parts[1].toDouble(), points.size)
        }.sortedByDescending { it.count }

        val clusterNames = prefs.getClusterNames()

        buildMeta("location", "primary", locPoints.size, locPoints.maxOfOrNull { it.timestamp } ?: 0L, listOf("location"))

        return clusters.take(20).map { c ->
            LocationCluster(
                id = c.grid,
                lat = c.lat,
                lon = c.lon,
                fixCount = c.count,
                name = clusterNames[c.grid]
            )
        }
    }

    // ── Card 6: Unlock-after-notification ────────────────────────────

    private suspend fun computeUnlockLatencies(): List<UnlockLatency> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L

        val notifications = dao.byCollectorKeySince("notification_listener", "notif_posted", sevenDaysAgo)
            .sortedBy { it.timestamp }

        // Primary: screen_state unlocks. Fallback: app_lifecycle foreground events
        var unlocks = dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo)
            .filter { it.value == "screen_on" }
            .sortedBy { it.timestamp }
            .map { it.timestamp }
        if (unlocks.isEmpty()) {
            unlocks = dao.byCollectorKeySince("app_lifecycle", "app_foreground", sevenDaysAgo)
                .sortedBy { it.timestamp }
                .map { it.timestamp }
        }

        val unlockSource = if (unlocks.isNotEmpty() && dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo).any { it.value == "screen_on" })
            "primary" else "fallback:app_lifecycle"

        if (notifications.isEmpty() || unlocks.isEmpty()) {
            buildMeta("unlock", unlockSource, 0, 0L, listOf("screen_state", "notification_listener", "app_lifecycle"))
            return emptyList()
        }

        // For each notification, find the next unlock within 10 minutes
        val maxLatencyMs = 10 * 60_000L
        val latencies = mutableMapOf<String, MutableList<Long>>()

        var unlockIdx = 0
        for (notif in notifications) {
            // Advance unlock pointer past notification time
            while (unlockIdx < unlocks.size && unlocks[unlockIdx] < notif.timestamp) {
                unlockIdx++
            }
            if (unlockIdx < unlocks.size) {
                val gap = unlocks[unlockIdx] - notif.timestamp
                if (gap in 0..maxLatencyMs) {
                    latencies.getOrPut(notif.value) { mutableListOf() }.add(gap)
                }
            }
        }

        val newestNotif = notifications.maxOfOrNull { it.timestamp } ?: 0L
        buildMeta("unlock", unlockSource, notifications.size + unlocks.size, newestNotif,
            listOf("screen_state", "notification_listener", "app_lifecycle"))

        return latencies.entries
            .map { (pkg, gaps) ->
                val sorted = gaps.sorted()
                val median = sorted[sorted.size / 2]
                UnlockLatency(pkg, median, sorted.size)
            }
            .sortedByDescending { it.medianLatencyMs }
            .take(10)
    }

    // ── Card 7: Fingerprint stability ────────────────────────────────

    private suspend fun computeFingerprint(): List<FingerprintField> {
        val collectors = listOf("build_info", "hardware", "identifiers")
        val fields = mutableListOf<FingerprintField>()

        for (collectorId in collectors) {
            val allPoints = dao.byCollector(collectorId, 10_000)
            val grouped = allPoints.groupBy { it.key }

            for ((key, readings) in grouped) {
                if (readings.isEmpty()) continue
                val sorted = readings.sortedBy { it.timestamp }
                val currentValue = sorted.last().value

                // Find when the value last changed
                var lastChangeTs: Long? = null
                for (i in sorted.size - 1 downTo 1) {
                    if (sorted[i].value != sorted[i - 1].value) {
                        lastChangeTs = sorted[i].timestamp
                        break
                    }
                }

                val label = key.replace("_", " ").replaceFirstChar { it.uppercase() }
                fields.add(FingerprintField(
                    label = label,
                    currentValue = currentValue,
                    lastChangedMs = lastChangeTs
                ))
            }
        }

        buildMeta("fingerprint", "primary", fields.size,
            fields.mapNotNull { it.lastChangedMs }.maxOrNull() ?: 0L,
            listOf("build_info", "hardware", "identifiers"),
            staleThresholdMs = 86_400_000L)

        return fields.sortedWith(
            compareByDescending<FingerprintField> { it.lastChangedMs != null }
                .thenByDescending { it.lastChangedMs ?: 0L }
        )
    }

    // ── Card 8: Monthly Trends ─────────────────────────────────────

    private suspend fun computeMonthlyTrends(): List<MonthlyTrend> {
        val sixMonthsAgo = System.currentTimeMillis() - 180 * 86_400_000L

        var screenEvents = dao.byCollectorKeySince("screen_state", "event", sixMonthsAgo)
        // Fallback: synthesize from app_lifecycle
        if (screenEvents.size < 20) {
            val lifecycle = dao.byCollectorSince("app_lifecycle", sixMonthsAgo)
            val synthesized = lifecycle.mapNotNull { dp ->
                when (dp.key) {
                    "app_foreground" -> dp.copy(value = "screen_on")
                    "app_background" -> dp.copy(value = "screen_off")
                    else -> null
                }
            }
            if (synthesized.size >= 20) screenEvents = synthesized
        }
        if (screenEvents.size < 20) {
            buildMeta("trends", "primary", screenEvents.size, 0L, listOf("screen_state", "app_lifecycle"))
            return emptyList()
        }

        val stepEvents = dao.byCollectorKeySince("steps", "step_counter_delta", sixMonthsAgo)

        // Group events by month
        val unlocksByMonth = screenEvents
            .filter { it.value == "screen_on" }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().withDayOfMonth(1) }

        val months = unlocksByMonth.keys.sorted()
        if (months.size < 2) return emptyList()

        return months.map { monthStart ->
            val monthEnd = monthStart.plusMonths(1)
            val daysInMonth = java.time.Period.between(monthStart, monthEnd).days.coerceAtLeast(1)

            val monthUnlocks = unlocksByMonth[monthStart]?.size ?: 0
            val avgDailyUnlocks = monthUnlocks.toDouble() / daysInMonth

            // Screen time for this month
            val monthEvents = screenEvents
                .filter {
                    val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && d.isBefore(monthEnd)
                }
                .sortedBy { it.timestamp }

            var screenTimeMs = 0L
            var lastOn: Long? = null
            for (e in monthEvents) {
                when (e.value) {
                    "screen_on" -> lastOn = e.timestamp
                    "screen_off" -> {
                        val on = lastOn
                        if (on != null) {
                            screenTimeMs += e.timestamp - on
                            lastOn = null
                        }
                    }
                }
            }
            val avgDailyScreenMs = screenTimeMs / daysInMonth

            // Steps this month
            val monthSteps = stepEvents
                .filter {
                    val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && d.isBefore(monthEnd)
                }
                .mapNotNull { it.value.toLongOrNull() }.sum()

            // Data points this month
            val monthStartMs = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val monthEndMs = monthEnd.atStartOfDay(zone).toInstant().toEpochMilli()
            val dataPoints = dao.countSince(monthStartMs) - dao.countSince(monthEndMs)

            MonthlyTrend(
                month = monthStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                dataPoints = dataPoints.coerceAtLeast(0),
                avgDailyUnlocks = avgDailyUnlocks,
                avgDailyScreenTimeMs = avgDailyScreenMs,
                totalSteps = monthSteps
            )
        }.also {
            val src = if (screenEvents.firstOrNull()?.collectorId == "screen_state") "primary" else "fallback:app_lifecycle"
            buildMeta("trends", src, screenEvents.size, screenEvents.maxOfOrNull { it.timestamp } ?: 0L,
                listOf("screen_state", "app_lifecycle", "steps"), staleThresholdMs = 86_400_000L)
        }
    }

    // ── Card 9: Engagement Score ─────────────────────────────────────

    private suspend fun computeEngagement(): EngagementScore? {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86_400_000L
        val thirtyDaysAgo = now - 30 * 86_400_000L

        var screenEvents = dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo)
            .sortedBy { it.timestamp }

        // Fallback: app_lifecycle foreground/background as session boundaries
        if (screenEvents.size < 2) {
            val lifecycle = dao.byCollectorSince("app_lifecycle", sevenDaysAgo)
            val synthesized = lifecycle.mapNotNull { dp ->
                when (dp.key) {
                    "app_foreground" -> dp.copy(value = "screen_on")
                    "app_background" -> dp.copy(value = "screen_off")
                    else -> null
                }
            }.sortedBy { it.timestamp }
            if (synthesized.size >= 2) screenEvents = synthesized
        }

        if (screenEvents.size < 2) {
            val durations = dao.byCollectorKeySince("app_lifecycle", "duration_ms", sevenDaysAgo)
                .mapNotNull { dp -> dp.value.toLongOrNull()?.takeIf { it in 1_000..3_600_000L }?.let { dp.timestamp to it } }
            if (durations.isEmpty()) return null
            val activeDays = durations.map { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }.toSet().size
            val totalMs = durations.sumOf { it.second }
            return EngagementScore(
                dauWauRatio = activeDays / 7.0,
                avgSessionsPerDay = durations.size / 7.0,
                avgSessionDurationMs = totalMs / durations.size,
                totalSessions7d = durations.size,
                activeDays7d = activeDays,
                retentionDay7 = false,
                retentionDay30 = false
            )
        }

        // Build sessions: screen_on to screen_off pairs
        var sessionCount = 0
        var totalSessionMs = 0L
        var lastOn: Long? = null
        val daysWithSession = mutableSetOf<LocalDate>()

        for (e in screenEvents) {
            when (e.value) {
                "screen_on" -> lastOn = e.timestamp
                "screen_off" -> {
                    val on = lastOn
                    if (on != null) {
                        val dur = e.timestamp - on
                        if (dur in 1_000..3_600_000L) { // 1s–1hr = valid session
                            sessionCount++
                            totalSessionMs += dur
                            daysWithSession.add(
                                Instant.ofEpochMilli(on).atZone(zone).toLocalDate()
                            )
                        }
                    }
                    lastOn = null
                }
            }
        }

        if (sessionCount == 0) {
            val foregrounds = screenEvents.filter { it.value == "screen_on" }
            if (foregrounds.isEmpty()) return null
            val activeDays = foregrounds.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toSet().size
            return EngagementScore(
                dauWauRatio = activeDays / 7.0,
                avgSessionsPerDay = foregrounds.size / 7.0,
                avgSessionDurationMs = 0L,
                totalSessions7d = foregrounds.size,
                activeDays7d = activeDays,
                retentionDay7 = false,
                retentionDay30 = false
            )
        }

        val activeDays = daysWithSession.size
        val dauWau = activeDays / 7.0

        // Retention: did we have any data 7 and 30 days ago?
        val day7Start = now - 7 * 86_400_000L
        val day7End = day7Start + 86_400_000L
        val ret7 = dao.byCollectorKeySince("screen_state", "event", day7Start)
            .any { it.timestamp < day7End }

        val day30Start = now - 30 * 86_400_000L
        val day30End = day30Start + 86_400_000L
        val ret30events = dao.byCollectorKeySince("screen_state", "event", day30Start)
        val ret30 = ret30events.any { it.timestamp < day30End }

        return EngagementScore(
            dauWauRatio = dauWau,
            avgSessionsPerDay = sessionCount / 7.0,
            avgSessionDurationMs = totalSessionMs / sessionCount,
            totalSessions7d = sessionCount,
            activeDays7d = activeDays,
            retentionDay7 = ret7,
            retentionDay30 = ret30
        )
    }

    // ── Card 9: Privacy Radar ────────────────────────────────────────

    private suspend fun computePrivacyRadar(): List<PrivacyRadarEntry> {
        val ninetyDaysAgo = System.currentTimeMillis() - 90 * 86_400_000L
        var opsData = dao.byCollectorSince("appops_audit", ninetyDaysAgo)
            .filter { it.key.startsWith("appop:") || it.key.startsWith("appops:") }

        // Fallback: privacy_dashboard collector (Android 12+ reflection-based)
        if (opsData.isEmpty()) {
            val dashData = dao.byCollectorSince("privacy_dashboard", ninetyDaysAgo)
                .filter { it.key.startsWith("usage:") }
            if (dashData.isNotEmpty()) {
                // Convert privacy_dashboard format to appops-like format
                // Dashboard key: "usage:<pkg>:<permission_group>", value has last_access_ms
                opsData = dashData.map { dp ->
                    val parts = dp.key.removePrefix("usage:").split(":", limit = 2)
                    val pkg = parts.getOrElse(0) { "" }
                    val group = parts.getOrElse(1) { "" }
                    dp.copy(key = "appop:$pkg:$group")
                }
            }
        }

        if (opsData.isEmpty()) return emptyList()

        // Group by package name (key format: "appops:<pkg>:<op>")
        data class OpRecord(val pkg: String, val op: String, val lastAccess: Long)

        val records = opsData.mapNotNull { dp ->
            val parts = dp.key
                .removePrefix("appops:")
                .removePrefix("appop:")
                .split(":", limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val lastAccess = Regex(""""last_access_ms":(\d+)""").find(dp.value)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            OpRecord(parts[0], parts[1], lastAccess)
        }

        val byPkg = records.groupBy { it.pkg }

        return byPkg.map { (pkg, ops) ->
            val camera = ops.count { it.op.contains("camera", ignoreCase = true) && it.lastAccess > 0 }
            val mic = ops.count { it.op.contains("record_audio", ignoreCase = true) && it.lastAccess > 0 }
            val location = ops.count { (it.op.contains("fine_location", ignoreCase = true) || it.op.contains("coarse_location", ignoreCase = true)) && it.lastAccess > 0 }
            val contacts = ops.count { it.op.contains("contacts", ignoreCase = true) && it.lastAccess > 0 }
            val lastAccess = ops.maxOf { it.lastAccess }

            // Privacy score: weighted sum of access types
            val score = ((camera * 25) + (mic * 25) + (location * 20) + (contacts * 15))
                .coerceIn(0, 100)

            PrivacyRadarEntry(pkg, camera, mic, location, contacts, lastAccess, score)
        }
            .filter { it.privacyScore > 0 }
            .sortedByDescending { it.privacyScore }
            .take(15)
    }

    // ── Card 10: Data Flow Monitor ───────────────────────────────────

    private suspend fun computeDataFlow(): List<DataFlowEntry> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val netData = dao.byCollectorSince("network_usage", sevenDaysAgo)
            .filter { it.key.startsWith("net_usage:") }

        if (netData.isEmpty()) return emptyList()

        // Take the most recent poll per package
        val latestPerPkg = netData
            .groupBy { it.key.removePrefix("net_usage:") }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp } }

        return latestPerPkg.mapNotNull { (pkg, dp) ->
            dp ?: return@mapNotNull null
            val json = dp.value
            val wifiRx = Regex(""""wifi_rx":(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val wifiTx = Regex(""""wifi_tx":(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mobileRx = Regex(""""mobile_rx":(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mobileTx = Regex(""""mobile_tx":(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val total = Regex(""""total_bytes":(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            val tx = wifiTx + mobileTx
            val rx = wifiRx + mobileRx
            val ratio = if (rx > 0) tx.toDouble() / rx else 0.0
            // Suspicious: sending >3x what it receives AND >1MB tx
            val suspicious = ratio > 3.0 && tx > 1_000_000L

            DataFlowEntry(pkg, total, tx, rx, ratio, suspicious)
        }
            .sortedByDescending { it.totalBytes }
            .take(15)
    }

    // ── Card 11: App Compulsion Index ────────────────────────────────

    private suspend fun computeAppCompulsion(): List<AppCompulsion> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val logcatData = dao.byCollectorSince("logcat", sevenDaysAgo)

        // Focus entries: key="focus:<pkg>", value = launch count for that poll
        val focusEntries = logcatData.filter { it.key.startsWith("focus:") }

        // Primary: logcat focus entries
        if (focusEntries.isNotEmpty()) {
            val launchCounts = mutableMapOf<String, Int>()
            val firstSeen = mutableMapOf<String, Long>()
            val lastSeen = mutableMapOf<String, Long>()

            for (entry in focusEntries) {
                val pkg = entry.key.removePrefix("focus:")
                val count = entry.value.toLongOrNull()?.toInt() ?: 1
                launchCounts[pkg] = (launchCounts[pkg] ?: 0) + count
                if (firstSeen[pkg] == null || entry.timestamp < firstSeen[pkg]!!) {
                    firstSeen[pkg] = entry.timestamp
                }
                if (lastSeen[pkg] == null || entry.timestamp > lastSeen[pkg]!!) {
                    lastSeen[pkg] = entry.timestamp
                }
            }

            return launchCounts.entries
                .filter { it.value >= 3 }
                .map { (pkg, count) ->
                    val first = firstSeen[pkg] ?: 0L
                    val last = lastSeen[pkg] ?: 0L
                    val spanMinutes = ((last - first) / 60_000.0).coerceAtLeast(1.0)
                    val avgGap = if (count > 1) spanMinutes / (count - 1) else spanMinutes
                    AppCompulsion(pkg, count, avgGap)
                }
                .sortedByDescending { it.launchCount }
                .take(10)
        }

        // Fallback: usage_stats launch counts (from JSON payload)
        val usageData = dao.byCollectorSince("usage_stats", sevenDaysAgo)
            .filter { it.key.startsWith("package_usage:") }
        if (usageData.isEmpty()) return emptyList()

        val launchCountsFromUsage = usageData.groupBy { it.key.removePrefix("package_usage:") }
            .mapNotNull { (pkg, entries) ->
                val latestJson = entries.maxByOrNull { it.timestamp }?.value ?: return@mapNotNull null
                val launches = Regex(""""launch_count":(\d+)""").find(latestJson)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                if (launches < 3) return@mapNotNull null
                // Estimate avg gap from 7-day span
                val avgGap = (7 * 24 * 60.0) / launches.coerceAtLeast(1)
                AppCompulsion(pkg, launches, avgGap)
            }
            .sortedByDescending { it.launchCount }
            .take(10)

        return launchCountsFromUsage
    }

    // ── Card 12: Device Health ───────────────────────────────────────

    private suspend fun computeDeviceHealth(): DeviceHealth? {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86_400_000L

        val sysStats = dao.byCollectorSince("system_stats", oneDayAgo)

        if (sysStats.isNotEmpty()) {
            // Primary: full system_stats data
            fun latestValue(key: String): String? =
                sysStats.filter { it.key == key }.maxByOrNull { it.timestamp }?.value

            val ramPct = latestValue("ram_used_pct")?.toDoubleOrNull()
            if (ramPct != null) {
                val procCount = latestValue("running_process_count")?.toIntOrNull() ?: 0
                val fgCount = latestValue("foreground_processes")?.toIntOrNull() ?: 0
                val bgCount = latestValue("background_processes")?.toIntOrNull() ?: 0
                val thermal = latestValue("thermal_status") ?: "unknown"
                val uptimeMs = latestValue("uptime_ms")?.toLongOrNull() ?: 0L

                val ramReadings = sysStats.filter { it.key == "ram_used_pct" }
                    .mapNotNull { dp -> dp.value.toDoubleOrNull()?.let { dp.timestamp to it } }
                    .sortedBy { it.first }
                val memTrend = if (ramReadings.size >= 2) {
                    ramReadings.last().second - ramReadings.first().second
                } else 0.0

                return DeviceHealth(
                    ramUsedPct = ramPct,
                    processCount = procCount,
                    foregroundCount = fgCount,
                    backgroundCount = bgCount,
                    thermalStatus = thermal,
                    uptimeHours = uptimeMs / 3_600_000.0,
                    memoryTrend = memTrend
                )
            }
        }

        // Fallback: live platform snapshot. This keeps Device Health useful even
        // before the System Stats collector has written its first row.
        liveDeviceHealthSnapshot(context)?.let { return it }

        // Final fallback: partial health from battery data
        val batteryData = dao.byCollectorSince("battery", oneDayAgo)
        if (batteryData.isEmpty()) return null

        fun latestBattery(key: String): String? =
            batteryData.filter { it.key == key }.maxByOrNull { it.timestamp }?.value

        latestBattery("level")?.toDoubleOrNull() ?: return null
        val tempC = latestBattery("temperature_tenths_c")?.toDoubleOrNull()?.div(10.0)
        val thermal = when {
            tempC == null -> "unknown"
            tempC > 45 -> "severe"
            tempC > 40 -> "moderate"
            tempC > 35 -> "light"
            else -> "none"
        }

        // Estimate uptime from earliest battery reading today
        val earliestToday = batteryData.minByOrNull { it.timestamp }?.timestamp ?: now
        val estimatedUptimeMs = now - earliestToday

        return DeviceHealth(
            ramUsedPct = 0.0, // unknown without system_stats
            processCount = 0,
            foregroundCount = 0,
            backgroundCount = 0,
            thermalStatus = thermal,
            uptimeHours = estimatedUptimeMs / 3_600_000.0,
            memoryTrend = 0.0
        )
    }

    private fun liveDeviceHealthSnapshot(context: Context): DeviceHealth? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager
            ?: return null

        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val ramPct = if (memInfo.totalMem > 0L) {
            ((memInfo.totalMem - memInfo.availMem) * 100.0 / memInfo.totalMem)
        } else {
            0.0
        }

        val processes = activityManager.runningAppProcesses.orEmpty()
        val foreground = processes.count {
            it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val background = processes.count {
            it.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        }

        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            when (powerManager?.currentThermalStatus) {
                android.os.PowerManager.THERMAL_STATUS_NONE -> "none"
                android.os.PowerManager.THERMAL_STATUS_LIGHT -> "light"
                android.os.PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                android.os.PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            }
        } else {
            "unknown"
        }

        return DeviceHealth(
            ramUsedPct = ramPct,
            processCount = processes.size,
            foregroundCount = foreground,
            backgroundCount = background,
            thermalStatus = thermal,
            uptimeHours = android.os.SystemClock.elapsedRealtime() / 3_600_000.0,
            memoryTrend = 0.0
        )
    }

    // ── Card 13: Identity Entropy ────────────────────────────────────

    private suspend fun computeIdentityEntropy(): IdentityEntropy? {
        // Known cardinalities for common fingerprint fields
        // Sources: industry fingerprinting research (EFF Panopticlick, AmIUnique)
        val knownCardinalities = mapOf(
            "manufacturer" to 50.0,
            "model" to 15_000.0,
            "brand" to 80.0,
            "device" to 12_000.0,
            "product" to 10_000.0,
            "board" to 5_000.0,
            "hardware" to 4_000.0,
            "sdk_version" to 20.0,
            "release" to 30.0,
            "security_patch" to 100.0,
            "abi" to 6.0,
            "supported_abis" to 15.0,
            "display_density_dpi" to 25.0,
            "display_width_px" to 200.0,
            "display_height_px" to 200.0,
            "total_ram_bytes" to 100.0,
            "cpu_count" to 8.0,
            "gl_renderer" to 500.0,
            "gl_vendor" to 20.0,
            "sensor_list_hash" to 5_000.0,
            "timezone" to 40.0,
            "locale" to 200.0,
            "kernel_version" to 50_000.0
        )

        val collectors = listOf("build_info", "hardware", "identifiers")
        val fields = mutableListOf<EntropyField>()

        for (collectorId in collectors) {
            val latest = dao.byCollector(collectorId, 5000)
                .groupBy { it.key }
                .mapValues { (_, v) -> v.maxByOrNull { it.timestamp } }

            for ((key, dp) in latest) {
                dp ?: continue
                val cardinality = knownCardinalities[key]
                val bits = if (cardinality != null) {
                    kotlin.math.ln(cardinality) / kotlin.math.ln(2.0)
                } else {
                    // Estimate: if value is a number, use log2(value+1); otherwise use string length * 2
                    val numVal = dp.value.toLongOrNull()
                    if (numVal != null && numVal > 0) {
                        kotlin.math.ln(numVal.toDouble()) / kotlin.math.ln(2.0)
                    } else {
                        dp.value.length.toDouble().coerceIn(1.0, 20.0)
                    }
                }

                fields.add(EntropyField(
                    name = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                    value = dp.value,
                    entropyBits = bits
                ))
            }
        }

        if (fields.isEmpty()) return null

        val sorted = fields.sortedByDescending { it.entropyBits }
        val total = sorted.sumOf { it.entropyBits }

        return IdentityEntropy(totalBits = total, fields = sorted.take(15))
    }

    // ── Inference 1: Home/Work ────────────────────────────────────────

    private suspend fun computeHomeWork(): HomeWorkInference? {
        val locPoints = dao.byCollector("location", 10_000)
        val latPoints = locPoints.filter { it.key == "lat" }
        val lonPoints = locPoints.filter { it.key == "lon" }

        if (latPoints.size < 20) return null

        // Pair lat/lon by timestamp
        data class Fix(val lat: Double, val lon: Double, val ts: Long)
        val fixes = mutableListOf<Fix>()
        for (lat in latPoints) {
            val latVal = lat.value.toDoubleOrNull() ?: continue
            val lon = lonPoints.minByOrNull { kotlin.math.abs(it.timestamp - lat.timestamp) } ?: continue
            val lonVal = lon.value.toDoubleOrNull() ?: continue
            if (kotlin.math.abs(lon.timestamp - lat.timestamp) < 5000) {
                fixes.add(Fix(latVal, lonVal, lat.timestamp))
            }
        }

        if (fixes.size < 10) return null

        // Grid cluster
        data class GridCluster(val grid: String, val lat: Double, val lon: Double, val fixes: MutableList<Fix>)
        val clusters = mutableMapOf<String, GridCluster>()
        for (f in fixes) {
            val grid = "%.3f,%.3f".format(f.lat, f.lon)
            clusters.getOrPut(grid) { GridCluster(grid, f.lat, f.lon, mutableListOf()) }.fixes.add(f)
        }

        // Home = most visited cluster during 10PM-6AM
        val nightClusters = clusters.values.map { c ->
            val nightFixes = c.fixes.count { f ->
                val hour = hourOfDay(f.ts)
                hour >= 22 || hour < 6
            }
            c to nightFixes
        }.sortedByDescending { it.second }

        val homeCluster = nightClusters.firstOrNull { it.second >= 3 }?.first

        // Work = most visited cluster during 9AM-5PM weekdays, excluding home
        val workClusters = clusters.values.map { c ->
            val workFixes = c.fixes.count { f ->
                val ldt = Instant.ofEpochMilli(f.ts).atZone(zone)
                val hour = ldt.hour
                val dow = ldt.dayOfWeek.value // 1=Mon, 7=Sun
                hour in 9..16 && dow in 1..5
            }
            c to workFixes
        }.sortedByDescending { it.second }

        val workCluster = workClusters.firstOrNull { it.second >= 3 && it.first.grid != homeCluster?.grid }?.first

        val clusterNames = prefs.getClusterNames()

        val homeLoc = homeCluster?.let {
            LocationCluster(it.grid, it.lat, it.lon, it.fixes.size, clusterNames[it.grid] ?: "Home")
        }
        val workLoc = workCluster?.let {
            LocationCluster(it.grid, it.lat, it.lon, it.fixes.size, clusterNames[it.grid] ?: "Work")
        }

        // Commute distance (haversine)
        val distKm = if (homeLoc != null && workLoc != null) {
            haversineKm(homeLoc.lat, homeLoc.lon, workLoc.lat, workLoc.lon)
        } else null

        return HomeWorkInference(homeLoc, workLoc, distKm, null, null)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    // ── Inference 2: Circadian Chronotype ────────────────────────────

    private suspend fun computeCircadian(): CircadianProfile? {
        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 86_400_000L
        var events = dao.byCollectorKeySince("screen_state", "event", thirtyDaysAgo)
            .filter { it.value == "screen_on" }

        // Fallback: app_lifecycle foreground events as proxy for screen unlocks
        if (events.size < 8) {
            val lifecycleEvents = dao.byCollectorKeySince("app_lifecycle", "app_foreground", thirtyDaysAgo)
            if (lifecycleEvents.size >= 8) {
                events = lifecycleEvents
            }
        }
        // Fallback 2: logcat app_launches timestamps
        if (events.size < 8) {
            val logcatLaunches = dao.byCollectorSince("logcat", thirtyDaysAgo)
                .filter { it.key.startsWith("focus:") }
            if (logcatLaunches.size >= 8) {
                events = logcatLaunches
            }
        }
        // Fallback 3: voice transcription windows indicate awake/conversational activity
        if (events.size < 8) {
            val voiceWindows = dao.byCollectorKeySince("voice_transcription", "conversation_present", thirtyDaysAgo)
                .filter { it.value.equals("true", ignoreCase = true) }
            if (voiceWindows.size >= 4) {
                events = voiceWindows
            }
        }

        if (events.size < 4) return null

        val hourlyUnlocks = IntArray(24)
        for (e in events) {
            val hour = Instant.ofEpochMilli(e.timestamp).atZone(zone).hour
            hourlyUnlocks[hour]++
        }

        val peakHour = hourlyUnlocks.indices.maxByOrNull { hourlyUnlocks[it] } ?: 12
        val troughHour = hourlyUnlocks.indices.filter { hourlyUnlocks[it] > 0 }
            .minByOrNull { hourlyUnlocks[it] } ?: 4

        // Find activity span: first and last hours with meaningful activity
        val activeHours = hourlyUnlocks.indices.filter { hourlyUnlocks[it] > events.size / 100 }
        val firstActive = activeHours.minOrNull() ?: 6
        val lastActive = activeHours.maxOrNull() ?: 23
        val spread = if (lastActive >= firstActive) (lastActive - firstActive + 1).toDouble()
                     else (24 - firstActive + lastActive + 1).toDouble()

        // Chronotype classification
        val morningActivity = (6..11).sumOf { hourlyUnlocks[it] }
        val eveningActivity = (18..23).sumOf { hourlyUnlocks[it] }
        val nightActivity = (0..5).sumOf { hourlyUnlocks[it] }
        val total = hourlyUnlocks.sum().coerceAtLeast(1)

        val chronotype = when {
            nightActivity > total * 0.15 -> "shift_worker"
            morningActivity > eveningActivity * 1.5 -> "early_bird"
            eveningActivity > morningActivity * 1.5 -> "night_owl"
            peakHour in 9..11 && hourlyUnlocks.indices.count { hourlyUnlocks[it] > total / 30 } > 2 &&
                (14..16).sumOf { hourlyUnlocks[it] } < morningActivity / 3 -> "bimodal"
            else -> "balanced"
        }

        return CircadianProfile(
            hourlyUnlocks = hourlyUnlocks.toList(),
            peakHour = peakHour,
            troughHour = troughHour,
            chronotype = chronotype,
            activitySpreadHrs = spread
        )
    }

    // ── Inference 3: Routine Predictability ──────────────────────────

    private suspend fun computeRoutine(): RoutinePredictability? {
        val fourteenDaysAgo = System.currentTimeMillis() - 14 * 86_400_000L
        var events = dao.byCollectorKeySince("screen_state", "event", fourteenDaysAgo)
            .filter { it.value == "screen_on" }

        // Fallback: app_lifecycle foreground events
        if (events.size < 10) {
            val lifecycleEvents = dao.byCollectorKeySince("app_lifecycle", "app_foreground", fourteenDaysAgo)
            if (lifecycleEvents.size >= 10) {
                events = lifecycleEvents
            }
        }
        // Fallback 2: logcat focus events
        if (events.size < 10) {
            val logcatFocus = dao.byCollectorSince("logcat", fourteenDaysAgo)
                .filter { it.key.startsWith("focus:") }
            if (logcatFocus.size >= 10) {
                events = logcatFocus
            }
        }
        // Fallback 3: voice context samples are sparse but useful for routine timing
        if (events.size < 10) {
            val voiceWindows = dao.byCollectorKeySince("voice_transcription", "conversation_present", fourteenDaysAgo)
                .filter { it.value.equals("true", ignoreCase = true) }
            if (voiceWindows.size >= 5) {
                events = voiceWindows
            }
        }

        if (events.size < 5) return null

        // Build per-day hourly unlock histograms
        data class DayProfile(val day: LocalDate, val hourly: IntArray)

        val dayProfiles = events.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.map { (day, dayEvents) ->
            val hourly = IntArray(24)
            for (e in dayEvents) {
                hourly[Instant.ofEpochMilli(e.timestamp).atZone(zone).hour]++
            }
            DayProfile(day, hourly)
        }

        if (dayProfiles.size < 2) return null

        // Average hourly profile
        val avgProfile = DoubleArray(24)
        for (dp in dayProfiles) {
            for (h in 0 until 24) avgProfile[h] += dp.hourly[h].toDouble()
        }
        for (h in 0 until 24) avgProfile[h] /= dayProfiles.size

        // Per-hour entropy: how variable is each hour across days
        val hourlyEntropy = DoubleArray(24)
        for (h in 0 until 24) {
            val values = dayProfiles.map { it.hourly[h].toDouble() }
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            // Normalized entropy: coefficient of variation (lower = more predictable)
            hourlyEntropy[h] = if (mean > 0) sqrt(variance) / mean else 0.0
        }

        val mostPredictable = hourlyEntropy.indices.filter { avgProfile[it] > 0.5 }
            .minByOrNull { hourlyEntropy[it] } ?: 0
        val leastPredictable = hourlyEntropy.indices.filter { avgProfile[it] > 0.5 }
            .maxByOrNull { hourlyEntropy[it] } ?: 12

        // Overall predictability: inverse of mean CV across active hours
        val activeHourEntropies = hourlyEntropy.indices
            .filter { avgProfile[it] > 0.5 }
            .map { hourlyEntropy[it] }
        val overallScore = if (activeHourEntropies.isNotEmpty()) {
            (1.0 / (1.0 + activeHourEntropies.average())).coerceIn(0.0, 1.0)
        } else 0.5

        // Weekday vs weekend shift
        val weekdayProfiles = dayProfiles.filter { it.day.dayOfWeek.value in 1..5 }
        val weekendProfiles = dayProfiles.filter { it.day.dayOfWeek.value in 6..7 }

        val wdAvg = DoubleArray(24)
        val weAvg = DoubleArray(24)
        weekdayProfiles.forEach { dp -> for (h in 0 until 24) wdAvg[h] += dp.hourly[h].toDouble() }
        weekendProfiles.forEach { dp -> for (h in 0 until 24) weAvg[h] += dp.hourly[h].toDouble() }
        if (weekdayProfiles.isNotEmpty()) for (h in 0 until 24) wdAvg[h] /= weekdayProfiles.size
        if (weekendProfiles.isNotEmpty()) for (h in 0 until 24) weAvg[h] /= weekendProfiles.size

        // Cosine distance
        val dot = (0 until 24).sumOf { wdAvg[it] * weAvg[it] }
        val magWd = sqrt((0 until 24).sumOf { wdAvg[it] * wdAvg[it] })
        val magWe = sqrt((0 until 24).sumOf { weAvg[it] * weAvg[it] })
        val cosine = if (magWd > 0 && magWe > 0) dot / (magWd * magWe) else 1.0
        val shift = 1.0 - cosine.coerceIn(0.0, 1.0)

        return RoutinePredictability(
            overallScore = overallScore,
            hourlyEntropy = hourlyEntropy.toList(),
            mostPredictableHour = mostPredictable,
            leastPredictableHour = leastPredictable,
            weekdayVsWeekendShift = shift
        )
    }

    // ── Inference 4: Social Pressure Index ───────────────────────────

    private suspend fun computeSocialPressure(): List<SocialPressureEntry> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L

        val notifications = dao.byCollectorKeySince("notification_listener", "notif_posted", sevenDaysAgo)
            .sortedBy { it.timestamp }

        var unlocks = dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo)
            .filter { it.value == "screen_on" }
            .sortedBy { it.timestamp }
            .map { it.timestamp }

        // Fallback: app_lifecycle foreground timestamps as proxy for unlocks
        if (unlocks.isEmpty()) {
            unlocks = dao.byCollectorKeySince("app_lifecycle", "app_foreground", sevenDaysAgo)
                .sortedBy { it.timestamp }
                .map { it.timestamp }
        }

        if (notifications.isEmpty() || unlocks.isEmpty()) return emptyList()

        val maxLatencyMs = 10 * 60_000L
        val perPkg = mutableMapOf<String, MutableList<Long?>>() // null = no response

        var unlockIdx = 0
        for (notif in notifications) {
            val pkg = notif.value
            // Find next unlock after this notification
            while (unlockIdx < unlocks.size && unlocks[unlockIdx] < notif.timestamp) {
                unlockIdx++
            }
            val responseMs = if (unlockIdx < unlocks.size) {
                val gap = unlocks[unlockIdx] - notif.timestamp
                if (gap in 0..maxLatencyMs) gap else null
            } else null

            perPkg.getOrPut(pkg) { mutableListOf() }.add(responseMs)
        }

        return perPkg.entries
            .map { (pkg, responses) ->
                val responded = responses.filterNotNull()
                val responseRate = responded.size.toDouble() / responses.size
                val medianMs = if (responded.isNotEmpty()) {
                    responded.sorted()[responded.size / 2]
                } else Long.MAX_VALUE

                val pressure = if (medianMs > 0 && medianMs < Long.MAX_VALUE) {
                    responses.size * responseRate / (medianMs / 60_000.0)
                } else 0.0

                SocialPressureEntry(pkg, responses.size, medianMs, responseRate, pressure)
            }
            .sortedByDescending { it.pressureScore }
            .take(10)
    }

    // ── Inference 5: App Portfolio Profile ────────────────────────────

    private suspend fun computeAppPortfolio(): AppPortfolioProfile? {
        var appData = dao.byCollector("installed_apps", 5000)

        // Fallback: derive package list from usage_stats entries
        if (appData.isEmpty()) {
            val usageData = dao.byCollector("usage_stats", 5000)
                .filter { it.key.startsWith("package_usage:") }
            if (usageData.isNotEmpty()) {
                // Synthesize minimal entries from usage_stats package names
                appData = usageData.map { dp ->
                    val pkg = dp.key.removePrefix("package_usage:")
                    DataPointEntity(
                        timestamp = dp.timestamp, collectorId = "installed_apps",
                        category = "APPS", key = pkg,
                        value = """{"package":"$pkg","is_system":false}""",
                        valueType = "JSON")
                }
            }
        }
        // Fallback 2: logcat focus entries for package names
        if (appData.isEmpty()) {
            val logcatFocus = dao.byCollector("logcat", 5000)
                .filter { it.key.startsWith("focus:") }
            if (logcatFocus.isNotEmpty()) {
                appData = logcatFocus.map { dp ->
                    val pkg = dp.key.removePrefix("focus:")
                    DataPointEntity(
                        timestamp = dp.timestamp, collectorId = "installed_apps",
                        category = "APPS", key = pkg,
                        value = """{"package":"$pkg","is_system":false}""",
                        valueType = "JSON")
                }.distinctBy { it.key }
            }
        }

        if (appData.isEmpty()) return null

        // Take the latest snapshot per package
        val latestPerPkg = appData.groupBy { it.key }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp } }

        var systemCount = 0
        var userCount = 0
        val categories = mutableMapOf<String, Int>()
        val inferences = mutableListOf<String>()

        // Package name → inferred category mapping
        val categoryPatterns = mapOf(
            "finance|banking|bank|pay|wallet|trading|invest" to "finance",
            "dating|tinder|bumble|hinge|grindr" to "dating",
            "kid|child|parent|family|baby" to "family",
            "fitness|health|workout|gym|strava|run" to "health_fitness",
            "game|play|puzzle|arcade" to "gaming",
            "shop|store|amazon|ebay|cart|buy" to "shopping",
            "news|cnn|bbc|reuters|nyt" to "news",
            "social|facebook|instagram|twitter|tiktok|snapchat|reddit|mastodon" to "social",
            "music|spotify|pandora|soundcloud|deezer" to "music",
            "video|youtube|netflix|hulu|disney|twitch|plex" to "streaming",
            "photo|camera|gallery|editor|snapseed" to "photography",
            "vpn|proxy|tor|signal|telegram|proton" to "privacy",
            "uber|lyft|taxi|transit|map|waze|navigation" to "transport",
            "food|delivery|doordash|grubhub|ubereats" to "food_delivery",
            "work|slack|teams|zoom|office|docs|notion|asana" to "productivity",
            "learn|edu|course|duolingo|study" to "education"
        )

        for ((pkg, dp) in latestPerPkg) {
            dp ?: continue
            val isSystem = dp.value.contains("\"is_system\":true")
            if (isSystem) systemCount++ else userCount++

            val pkgLower = pkg.lowercase()
            for ((pattern, cat) in categoryPatterns) {
                if (pattern.toRegex().containsMatchIn(pkgLower)) {
                    categories[cat] = (categories[cat] ?: 0) + 1
                    break
                }
            }
        }

        // Generate demographic inferences
        if ((categories["dating"] ?: 0) > 0) inferences.add("likely_single")
        if ((categories["family"] ?: 0) >= 2) inferences.add("likely_parent")
        if ((categories["finance"] ?: 0) >= 2) inferences.add("finance_engaged")
        if ((categories["gaming"] ?: 0) >= 3) inferences.add("heavy_gamer")
        if ((categories["streaming"] ?: 0) >= 3) inferences.add("multiple_subscriptions")
        if ((categories["privacy"] ?: 0) >= 2) inferences.add("privacy_conscious")
        if ((categories["health_fitness"] ?: 0) >= 2) inferences.add("fitness_enthusiast")
        if ((categories["productivity"] ?: 0) >= 3) inferences.add("knowledge_worker")
        if ((categories["food_delivery"] ?: 0) >= 2) inferences.add("delivery_dependent")
        if ((categories["education"] ?: 0) >= 1) inferences.add("active_learner")

        return AppPortfolioProfile(
            totalApps = latestPerPkg.size,
            systemApps = systemCount,
            userApps = userCount,
            categories = categories,
            inferences = inferences
        )
    }

    // ── Inference 6: Charging Behavior ───────────────────────────────

    private suspend fun computeCharging(): ChargingBehavior? {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val batteryLevels = dao.byCollectorKeySince("battery", "level", sevenDaysAgo)
            .mapNotNull { dp -> dp.value.toIntOrNull()?.let { dp.timestamp to it } }
            .sortedBy { it.first }
        val chargingStates = dao.byCollectorKeySince("battery", "is_charging", sevenDaysAgo)
            .mapNotNull { dp -> dp.value.toBooleanStrictOrNull()?.let { dp.timestamp to it } }
            .sortedBy { it.first }

        if (batteryLevels.isEmpty() && chargingStates.isEmpty()) return null

        // Detect charge cycles: find sequences where level increases
        data class ChargeCycle(val startTs: Long, val startLevel: Int, val endTs: Long, val endLevel: Int)
        val cycles = mutableListOf<ChargeCycle>()
        var chargeStart: Pair<Long, Int>? = null

        if (chargingStates.isNotEmpty()) {
            fun nearestLevel(ts: Long): Int =
                batteryLevels.minByOrNull { kotlin.math.abs(it.first - ts) }?.second
                    ?: batteryLevels.lastOrNull()?.second
                    ?: 0

            for (i in chargingStates.indices) {
                val (ts, isCharging) = chargingStates[i]
                val prevCharging = chargingStates.getOrNull(i - 1)?.second ?: false
                if (isCharging && !prevCharging) {
                    chargeStart = ts to nearestLevel(ts)
                } else if (!isCharging && prevCharging && chargeStart != null) {
                    cycles.add(ChargeCycle(
                        chargeStart!!.first,
                        chargeStart!!.second,
                        ts,
                        nearestLevel(ts)
                    ))
                    chargeStart = null
                }
            }
        }

        if (cycles.isEmpty() && batteryLevels.size >= 2) {
            for (i in 1 until batteryLevels.size) {
                val prev = batteryLevels[i - 1]
                val curr = batteryLevels[i]
                if (curr.second > prev.second + 2) {
                    // Charging started or continuing
                    if (chargeStart == null) chargeStart = prev
                } else if (curr.second <= prev.second && chargeStart != null) {
                    // Charging stopped
                    val endIdx = i - 1
                    cycles.add(ChargeCycle(
                        chargeStart!!.first, chargeStart!!.second,
                        batteryLevels[endIdx].first, batteryLevels[endIdx].second
                    ))
                    chargeStart = null
                }
            }
        }

        if (cycles.isEmpty()) {
            val latestLevel = batteryLevels.lastOrNull()?.second ?: return null
            val latestCharging = chargingStates.lastOrNull()?.second == true
            val latestHour = Instant.ofEpochMilli(
                chargingStates.lastOrNull()?.first ?: batteryLevels.last().first
            ).atZone(zone).hour
            return ChargingBehavior(
                avgChargesPerDay = if (latestCharging) 1.0 else 0.0,
                avgDischargeDepthPct = latestLevel.toDouble(),
                overnightCharger = latestCharging && (latestHour >= 20 || latestHour < 6),
                avgChargeDurationMs = 0L,
                typicalChargeHour = latestHour
            )
        }

        val daysSpan = ((batteryLevels.last().first - batteryLevels.first().first) / 86_400_000.0).coerceAtLeast(1.0)
        val avgChargesPerDay = cycles.size / daysSpan
        val avgDischargeDepth = cycles.map { it.startLevel.toDouble() }.average()
        val avgChargeDuration = cycles.map { it.endTs - it.startTs }.average().toLong()

        // Overnight charging: how many cycles include midnight-6AM
        val overnightCycles = cycles.count { c ->
            val startHour = Instant.ofEpochMilli(c.startTs).atZone(zone).hour
            startHour in 20..23 || startHour in 0..2
        }
        val overnightCharger = overnightCycles > cycles.size / 2

        // Most common charge start hour
        val chargeHours = cycles.map { Instant.ofEpochMilli(it.startTs).atZone(zone).hour }
        val typicalHour = chargeHours.groupBy { it }.maxByOrNull { it.value.size }?.key ?: 22

        return ChargingBehavior(
            avgChargesPerDay = avgChargesPerDay,
            avgDischargeDepthPct = avgDischargeDepth,
            overnightCharger = overnightCharger,
            avgChargeDurationMs = avgChargeDuration,
            typicalChargeHour = typicalHour
        )
    }

    // ── Inference 7: WiFi Footprint ──────────────────────────────────

    private suspend fun computeWifiFootprint(): WiFiFootprint? {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val wifiData = dao.byCollectorSince("wifi", sevenDaysAgo)

        var ssidEntries = if (wifiData.isNotEmpty()) {
            wifiData.filter { it.key == "current_ssid" && it.value.isNotBlank() && it.value != "<unknown ssid>" }
        } else emptyList()

        // Fallback: connectivity collector may have SSID or transport info
        if (ssidEntries.isEmpty()) {
            val connData = dao.byCollectorSince("connectivity", sevenDaysAgo)
            ssidEntries = connData.filter { it.key == "ssid" && it.value.isNotBlank() && it.value != "<unknown ssid>" }
            if (ssidEntries.isEmpty()) {
                ssidEntries = connData
                    .filter { it.key == "active_transport" && it.value.equals("wifi", ignoreCase = true) }
                    .map {
                        it.copy(
                            collectorId = "connectivity",
                            key = "ssid",
                            value = "Wi-Fi connected"
                        )
                    }
            }
        }

        if (ssidEntries.isEmpty()) return null

        val ssidCounts = ssidEntries.groupBy { it.value }.mapValues { it.value.size }
        val uniqueNetworks = ssidCounts.size
        val topNetworks = ssidCounts.entries.sortedByDescending { it.value }.take(10)
            .map { it.key to it.value }

        // Mobility score: entropy of SSID distribution
        val total = ssidEntries.size.toDouble()
        val entropy = ssidCounts.values.sumOf { count ->
            val p = count / total
            if (p > 0) -p * kotlin.math.ln(p) / kotlin.math.ln(2.0) else 0.0
        }
        val maxEntropy = if (uniqueNetworks > 1) kotlin.math.ln(uniqueNetworks.toDouble()) / kotlin.math.ln(2.0) else 1.0
        val mobilityScore = (entropy / maxEntropy).coerceIn(0.0, 1.0)

        // Home network: most frequent SSID during 10PM-6AM
        val nightSSIDs = ssidEntries.filter {
            val hour = Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
            hour >= 22 || hour < 6
        }.groupBy { it.value }.maxByOrNull { it.value.size }

        val scanResults = wifiData.filter { it.key == "scan_results" }

        return WiFiFootprint(
            uniqueNetworks7d = uniqueNetworks,
            totalScans = scanResults.size,
            topNetworks = topNetworks,
            mobilityScore = mobilityScore,
            homeNetwork = nightSSIDs?.key
        )
    }

    // ── Inference 8: Session Fragmentation ───────────────────────────

    private suspend fun computeSessionFragmentation(): SessionFragmentation? {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L

        // Get usage stats for app foreground times
        val usageData = dao.byCollectorSince("usage_stats", sevenDaysAgo)
            .filter { it.key.startsWith("package_usage:") }

        var screenEvents = dao.byCollectorKeySince("screen_state", "event", sevenDaysAgo)
            .sortedBy { it.timestamp }

        // Fallback: synthesize screen_on/screen_off from app_lifecycle foreground/background
        if (screenEvents.size < 4) {
            val fgEvents = dao.byCollectorKeySince("app_lifecycle", "app_foreground", sevenDaysAgo)
            val bgEvents = dao.byCollectorKeySince("app_lifecycle", "app_background", sevenDaysAgo)
            if (fgEvents.size + bgEvents.size >= 4) {
                val synthList = mutableListOf<DataPointEntity>()
                for (e in fgEvents) {
                    synthList.add(DataPointEntity(
                        timestamp = e.timestamp, collectorId = "screen_state",
                        category = "DEVICE", key = "event", value = "screen_on",
                        valueType = "STRING"))
                }
                for (e in bgEvents) {
                    synthList.add(DataPointEntity(
                        timestamp = e.timestamp, collectorId = "screen_state",
                        category = "DEVICE", key = "event", value = "screen_off",
                        valueType = "STRING"))
                }
                screenEvents = synthList.sortedBy { it.timestamp }
            }
        }

        if (screenEvents.size < 4) return null

        // Build sessions (screen on to screen off)
        data class Session(val startMs: Long, val endMs: Long)
        val sessions = mutableListOf<Session>()
        var lastOn: Long? = null
        for (e in screenEvents) {
            when (e.value) {
                "screen_on" -> lastOn = e.timestamp
                "screen_off" -> {
                    val on = lastOn
                    if (on != null) {
                        val dur = e.timestamp - on
                        if (dur in 3_000..3_600_000L) { // 3s to 1hr
                            sessions.add(Session(on, e.timestamp))
                        }
                    }
                    lastOn = null
                }
            }
        }

        if (sessions.isEmpty()) return null

        // Count app switches per session using logcat focus entries
        val logcatData = dao.byCollectorSince("logcat", sevenDaysAgo)
        val appLaunchEvents = logcatData.filter { it.key == "app_launches" }
            .mapNotNull { dp -> dp.value.toLongOrNull()?.let { dp.timestamp to it } }
            .sortedBy { it.first }

        // Estimate switches per session
        var totalSwitches = 0L
        for (session in sessions) {
            val switchesInSession = appLaunchEvents.count {
                it.first in session.startMs..session.endMs
            }
            totalSwitches += switchesInSession
        }

        val avgSwitches = totalSwitches.toDouble() / sessions.size
        val avgSessionMs = sessions.map { it.endMs - it.startMs }.average().toLong()
        val avgDepthMs = if (avgSwitches > 0) (avgSessionMs / avgSwitches).toLong() else avgSessionMs

        // Per-hour fragmentation
        val hourlyFragmentation = IntArray(24)
        val hourlySessions = IntArray(24)
        for (session in sessions) {
            val hour = Instant.ofEpochMilli(session.startMs).atZone(zone).hour
            hourlySessions[hour]++
            hourlyFragmentation[hour] += appLaunchEvents.count {
                it.first in session.startMs..session.endMs
            }.toInt()
        }

        val hourlyAvgFrag = DoubleArray(24) { h ->
            if (hourlySessions[h] > 0) hourlyFragmentation[h].toDouble() / hourlySessions[h] else 0.0
        }

        val activeHours = hourlyAvgFrag.indices.filter { hourlySessions[it] >= 2 }
        val mostFrag = activeHours.maxByOrNull { hourlyAvgFrag[it] } ?: 12
        val leastFrag = activeHours.minByOrNull { hourlyAvgFrag[it] } ?: 6

        // Attention score: inverse of switches per minute, normalized
        val switchesPerMin = if (avgSessionMs > 0) avgSwitches / (avgSessionMs / 60_000.0) else 0.0
        val attentionScore = (1.0 / (1.0 + switchesPerMin * 2)).coerceIn(0.0, 1.0)

        return SessionFragmentation(
            avgSwitchesPerSession = avgSwitches,
            avgSessionDepthMs = avgDepthMs,
            mostFragmentedHour = mostFrag,
            leastFragmentedHour = leastFrag,
            attentionScore = attentionScore
        )
    }

    // ── Inference 9: Dwell Time ──────────────────────────────────────

    private suspend fun computeDwellTimes(): List<DwellTimeEntry> {
        val locPoints = dao.byCollector("location", 10_000)
        val latPoints = locPoints.filter { it.key == "lat" }
        val lonPoints = locPoints.filter { it.key == "lon" }

        if (latPoints.size < 10) return emptyList()

        data class Fix(val lat: Double, val lon: Double, val ts: Long, val grid: String)
        val fixes = mutableListOf<Fix>()
        for (lat in latPoints) {
            val latVal = lat.value.toDoubleOrNull() ?: continue
            val lon = lonPoints.minByOrNull { kotlin.math.abs(it.timestamp - lat.timestamp) } ?: continue
            val lonVal = lon.value.toDoubleOrNull() ?: continue
            if (kotlin.math.abs(lon.timestamp - lat.timestamp) < 5000) {
                val grid = "%.3f,%.3f".format(latVal, lonVal)
                fixes.add(Fix(latVal, lonVal, lat.timestamp, grid))
            }
        }

        if (fixes.size < 5) return emptyList()

        // Sort by time and compute dwell at each cluster
        val sorted = fixes.sortedBy { it.ts }
        data class Visit(val grid: String, val startMs: Long, val endMs: Long)
        val visits = mutableListOf<Visit>()
        var visitStart = sorted.first().ts
        var currentGrid = sorted.first().grid

        for (i in 1 until sorted.size) {
            if (sorted[i].grid != currentGrid) {
                visits.add(Visit(currentGrid, visitStart, sorted[i - 1].ts))
                currentGrid = sorted[i].grid
                visitStart = sorted[i].ts
            }
        }
        visits.add(Visit(currentGrid, visitStart, sorted.last().ts))

        // Aggregate by grid
        val clusterNames = prefs.getClusterNames()
        val byGrid = visits.groupBy { it.grid }

        return byGrid.map { (grid, gridVisits) ->
            val totalDwell = gridVisits.sumOf { it.endMs - it.startMs }
            val avgDwell = totalDwell / gridVisits.size

            // Classify based on dwell patterns
            val nightVisits = gridVisits.count { v ->
                val hour = Instant.ofEpochMilli(v.startMs).atZone(zone).hour
                hour >= 22 || hour < 6
            }
            val workVisits = gridVisits.count { v ->
                val ldt = Instant.ofEpochMilli(v.startMs).atZone(zone)
                ldt.hour in 9..17 && ldt.dayOfWeek.value in 1..5
            }

            val classification = when {
                nightVisits > gridVisits.size / 2 -> "home"
                workVisits > gridVisits.size / 2 && avgDwell > 3_600_000 -> "work"
                avgDwell < 30 * 60_000 -> "transit"
                avgDwell in 30 * 60_000..2 * 3_600_000L -> "retail"
                else -> "social"
            }

            DwellTimeEntry(grid, clusterNames[grid], totalDwell, gridVisits.size, avgDwell, classification)
        }
            .sortedByDescending { it.totalDwellMs }
            .take(15)
    }

    // ── Inference 10: Weekday/Weekend Delta ──────────────────────────

    private suspend fun computeWeekdayWeekend(): WeekdayWeekendDelta? {
        val fourteenDaysAgo = System.currentTimeMillis() - 14 * 86_400_000L
        var screenEvents = dao.byCollectorKeySince("screen_state", "event", fourteenDaysAgo)

        // Fallback: synthesize from app_lifecycle foreground/background
        if (screenEvents.size < 4) {
            val fgEvents = dao.byCollectorKeySince("app_lifecycle", "app_foreground", fourteenDaysAgo)
            val bgEvents = dao.byCollectorKeySince("app_lifecycle", "app_background", fourteenDaysAgo)
            if (fgEvents.size + bgEvents.size >= 4) {
                val synthList = mutableListOf<DataPointEntity>()
                for (e in fgEvents) {
                    synthList.add(DataPointEntity(
                        timestamp = e.timestamp, collectorId = "screen_state",
                        category = "DEVICE", key = "event", value = "screen_on",
                        valueType = "STRING"))
                }
                for (e in bgEvents) {
                    synthList.add(DataPointEntity(
                        timestamp = e.timestamp, collectorId = "screen_state",
                        category = "DEVICE", key = "event", value = "screen_off",
                        valueType = "STRING"))
                }
                screenEvents = synthList.sortedBy { it.timestamp }
            }
        }

        if (screenEvents.size < 4) return null

        val unlockEvents = screenEvents.filter { it.value == "screen_on" }

        val weekdayUnlocks = unlockEvents.filter {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek.value in 1..5
        }
        val weekendUnlocks = unlockEvents.filter {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek.value in 6..7
        }

        val weekdays = (1..5).map { dow ->
            unlockEvents.count { e ->
                Instant.ofEpochMilli(e.timestamp).atZone(zone).dayOfWeek.value == dow
            }
        }.let { it.sum().toDouble() / it.count { c -> c > 0 }.coerceAtLeast(1) }

        val weekendDays = (6..7).map { dow ->
            unlockEvents.count { e ->
                Instant.ofEpochMilli(e.timestamp).atZone(zone).dayOfWeek.value == dow
            }
        }.let { it.sum().toDouble() / it.count { c -> c > 0 }.coerceAtLeast(1) }

        // Screen time by weekday/weekend
        val sorted = screenEvents.sortedBy { it.timestamp }
        var wdScreenMs = 0L
        var weScreenMs = 0L
        var lastOn: Long? = null
        var wdDays = 0
        var weDays = 0

        val seenDays = mutableSetOf<String>()
        for (e in sorted) {
            val ldt = Instant.ofEpochMilli(e.timestamp).atZone(zone)
            val dayKey = ldt.toLocalDate().toString()
            val isWeekend = ldt.dayOfWeek.value in 6..7

            if (dayKey !in seenDays) {
                seenDays.add(dayKey)
                if (isWeekend) weDays++ else wdDays++
            }

            when (e.value) {
                "screen_on" -> lastOn = e.timestamp
                "screen_off" -> {
                    val on = lastOn
                    if (on != null) {
                        val dur = e.timestamp - on
                        if (dur in 1_000..3_600_000L) {
                            if (isWeekend) weScreenMs += dur else wdScreenMs += dur
                        }
                    }
                    lastOn = null
                }
            }
        }

        val wdAvgScreenMs = if (wdDays > 0) wdScreenMs / wdDays else 0L
        val weAvgScreenMs = if (weDays > 0) weScreenMs / weDays else 0L

        // Top apps weekday vs weekend from usage stats
        val usageData = dao.byCollectorSince("usage_stats", fourteenDaysAgo)
            .filter { it.key.startsWith("package_usage:") }

        fun topAppsForDow(dowRange: IntRange): List<String> {
            return usageData.filter {
                Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek.value in dowRange
            }.groupBy { it.key.removePrefix("package_usage:") }
                .mapValues { (_, entries) ->
                    entries.mapNotNull { parseForegroundMs(it.value) }.maxOrNull() ?: 0L
                }
                .entries.sortedByDescending { it.value }.take(3).map { it.key }
        }

        val wdTopApps = topAppsForDow(1..5)
        val weTopApps = topAppsForDow(6..7)

        // Balance score: how different are weekday/weekend patterns
        val maxUnlocks = maxOf(weekdays, weekendDays).coerceAtLeast(1.0)
        val unlockDiff = kotlin.math.abs(weekdays - weekendDays) / maxUnlocks
        val maxScreen = maxOf(wdAvgScreenMs, weAvgScreenMs).toDouble().coerceAtLeast(1.0)
        val screenDiff = kotlin.math.abs(wdAvgScreenMs - weAvgScreenMs) / maxScreen
        val appOverlap = wdTopApps.intersect(weTopApps.toSet()).size.toDouble() / 3.0
        val balanceScore = ((unlockDiff + screenDiff + (1.0 - appOverlap)) / 3.0).coerceIn(0.0, 1.0)

        return WeekdayWeekendDelta(
            weekdayAvgUnlocks = weekdays,
            weekendAvgUnlocks = weekendDays,
            weekdayAvgScreenMs = wdAvgScreenMs,
            weekendAvgScreenMs = weAvgScreenMs,
            weekdayTopApps = wdTopApps,
            weekendTopApps = weTopApps,
            balanceScore = balanceScore
        )
    }

    // ── Inference 11: Income/Demographic ─────────────────────────────

    private suspend fun computeIncome(): IncomeInference? {
        val buildData = dao.byCollector("build_info", 100)
        if (buildData.isEmpty()) return null

        fun latestVal(key: String) = buildData.filter { it.key == key }
            .maxByOrNull { it.timestamp }?.value ?: ""

        val manufacturer = latestVal("manufacturer").lowercase()
        val model = latestVal("model").lowercase()
        val brand = latestVal("brand").lowercase()

        // Device price tier estimation
        val flagshipBrands = setOf("apple", "samsung", "google", "oneplus", "sony")
        val budgetBrands = setOf("xiaomi", "redmi", "poco", "realme", "oppo", "vivo", "tecno", "infinix", "itel")

        val flagshipKeywords = listOf("pro", "ultra", "max", "plus", "fold", "flip", "note 2", "s2")
        val budgetKeywords = listOf("lite", "go", "neo", "a0", "a1", "c1", "c0", "y1", "redmi")

        val isFlagshipBrand = flagshipBrands.any { brand.contains(it) || manufacturer.contains(it) }
        val hasFlagshipKeyword = flagshipKeywords.any { model.contains(it) }
        val hasBudgetKeyword = budgetKeywords.any { model.contains(it) }

        val (deviceTier, estimatedPrice) = when {
            brand.contains("apple") -> "ultra" to 1200
            isFlagshipBrand && hasFlagshipKeyword -> "flagship" to 900
            isFlagshipBrand && !hasBudgetKeyword -> "mid_range" to 500
            hasBudgetKeyword || budgetBrands.any { brand.contains(it) } -> "budget" to 200
            else -> "mid_range" to 400
        }

        // Carrier tier: primary carrier collector, fallback to connectivity for operator name
        var carrierData = dao.byCollector("carrier", 10)
        var carrierName = carrierData.filter { it.key == "carrier_name" }
            .maxByOrNull { it.timestamp }?.value?.lowercase() ?: ""
        if (carrierName.isBlank()) {
            val connData = dao.byCollector("connectivity", 50)
            carrierName = connData.filter { it.key == "operator_name" || it.key == "network_operator" }
                .maxByOrNull { it.timestamp }?.value?.lowercase() ?: ""
        }

        val premiumCarriers = setOf("verizon", "at&t", "t-mobile", "vodafone", "ee", "o2")
        val mvnoKeywords = listOf("mint", "visible", "cricket", "boost", "metro", "straight", "tracfone")

        val carrierTier = when {
            premiumCarriers.any { carrierName.contains(it) } -> "postpaid"
            mvnoKeywords.any { carrierName.contains(it) } -> "mvno"
            carrierName.isBlank() -> "unknown"
            else -> "prepaid"
        }

        // App signals
        val appSignals = mutableListOf<String>()
        val appPortfolio = computeAppPortfolio()
        if (appPortfolio != null) {
            if ((appPortfolio.categories["finance"] ?: 0) >= 2) appSignals.add("has_trading_apps")
            if ((appPortfolio.categories["streaming"] ?: 0) >= 3) appSignals.add("multiple_streaming")
            if ((appPortfolio.categories["shopping"] ?: 0) >= 3) appSignals.add("frequent_shopper")
            if ((appPortfolio.categories["food_delivery"] ?: 0) >= 2) appSignals.add("uses_delivery")
        }

        // Overall tier
        val tierScore = when (deviceTier) {
            "ultra" -> 4
            "flagship" -> 3
            "mid_range" -> 2
            "budget" -> 1
            else -> 2
        } + when (carrierTier) {
            "postpaid" -> 2
            "prepaid" -> 1
            "mvno" -> 0
            else -> 1
        } + if (appSignals.contains("has_trading_apps")) 1 else 0 +
                if (appSignals.contains("multiple_streaming")) 1 else 0

        val overallTier = when {
            tierScore >= 6 -> "affluent"
            tierScore >= 4 -> "high"
            tierScore >= 2 -> "mid"
            else -> "low"
        }

        return IncomeInference(deviceTier, estimatedPrice, carrierTier, appSignals, overallTier)
    }

    // ── Inference 12: Commute Detection ──────────────────────────────

    private suspend fun computeCommute(): CommutePattern? {
        val homeWork = _state.value.homeWork ?: computeHomeWork()
        if (homeWork?.homeCluster == null || homeWork.workCluster == null) {
            return CommutePattern(false, 0.0, 0.0, 0.0, "unknown", 0.0)
        }

        val fourteenDaysAgo = System.currentTimeMillis() - 14 * 86_400_000L
        val locPoints = dao.byCollectorSince("location", fourteenDaysAgo)
        val latPoints = locPoints.filter { it.key == "lat" }
        val lonPoints = locPoints.filter { it.key == "lon" }

        data class Fix(val lat: Double, val lon: Double, val ts: Long)
        val fixes = mutableListOf<Fix>()
        for (lat in latPoints) {
            val latVal = lat.value.toDoubleOrNull() ?: continue
            val lon = lonPoints.minByOrNull { kotlin.math.abs(it.timestamp - lat.timestamp) } ?: continue
            val lonVal = lon.value.toDoubleOrNull() ?: continue
            if (kotlin.math.abs(lon.timestamp - lat.timestamp) < 5000) {
                fixes.add(Fix(latVal, lonVal, lat.timestamp))
            }
        }

        val sorted = fixes.sortedBy { it.ts }
        if (sorted.size < 10) return CommutePattern(false, 0.0, 0.0, 0.0, "unknown", 0.0)

        val homeGrid = homeWork.homeCluster!!.id
        val workGrid = homeWork.workCluster!!.id

        // Find transitions: home→work and work→home
        data class Transition(val fromGrid: String, val toGrid: String, val departTs: Long, val arriveTs: Long)
        val transitions = mutableListOf<Transition>()

        var prevGrid: String? = null
        var prevTs: Long = 0
        for (fix in sorted) {
            val grid = "%.3f,%.3f".format(fix.lat, fix.lon)
            if (prevGrid != null && grid != prevGrid) {
                if ((prevGrid == homeGrid && grid == workGrid) || (prevGrid == workGrid && grid == homeGrid)) {
                    transitions.add(Transition(prevGrid, grid, prevTs, fix.ts))
                }
            }
            prevGrid = grid
            prevTs = fix.ts
        }

        if (transitions.size < 2) return CommutePattern(false, 0.0, 0.0, 0.0, "unknown", 0.0)

        val departures = transitions.filter { it.fromGrid == homeGrid }
        val returns = transitions.filter { it.fromGrid == workGrid }

        val avgDepartHour = if (departures.isNotEmpty()) {
            departures.map { hourOfDay(it.departTs) }.average()
        } else 8.0

        val avgReturnHour = if (returns.isNotEmpty()) {
            returns.map { hourOfDay(it.departTs) }.average()
        } else 17.0

        val allDurations = transitions.map { (it.arriveTs - it.departTs) / 60_000.0 }
        val avgDuration = allDurations.average()

        // Transport mode from speed
        val distKm = homeWork.commuteDistanceKm ?: 0.0
        val avgSpeedKmh = if (avgDuration > 0) distKm / (avgDuration / 60.0) else 0.0
        val transportMode = when {
            avgSpeedKmh < 6 -> "walking"
            avgSpeedKmh < 25 -> "transit"
            else -> "driving"
        }

        // Consistency: std dev of departure hours
        val departHours = departures.map { hourOfDay(it.departTs) }
        val consistency = if (departHours.size >= 3) {
            val std = stdDev(departHours)
            (1.0 / (1.0 + std)).coerceIn(0.0, 1.0)
        } else 0.5

        return CommutePattern(
            detected = true,
            avgDepartureHour = avgDepartHour,
            avgReturnHour = avgReturnHour,
            avgDurationMinutes = avgDuration,
            transportMode = transportMode,
            consistencyScore = consistency
        )
    }

    private suspend fun computeVoiceContext(): VoiceContextInsight? {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val points = dao.byCollectorSince("voice_transcription", sevenDaysAgo, limit = 20_000)
        val windows = points.filter { it.key == "window_duration_ms" }
        if (windows.isEmpty()) {
            val modelInstalled = VoiceModelInstaller.isInstalled(context)
            val modelStatus = points
                .filter { it.key == "model_status" }
                .maxByOrNull { it.timestamp }
            if (modelStatus != null || modelInstalled) {
                val expectedPath = points
                    .filter { it.key == "model_expected_path" }
                    .maxByOrNull { it.timestamp }
                    ?.value
                val message = if (modelInstalled) {
                    "Voice model installed. Waiting for the first transcription window."
                } else if (modelStatus?.value == "missing" && expectedPath != null) {
                    "Voice model missing. Expected: $expectedPath"
                } else {
                    "Voice collector status: ${modelStatus?.value ?: "waiting"}"
                }
                return VoiceContextInsight(
                    samples7d = 0,
                    conversationSamples = 0,
                    avgSpeechDensityWpm = 0.0,
                    topContexts = listOf((if (modelInstalled) "waiting_for_samples" else "setup_needed") to 1),
                    topTags = emptyList(),
                    latestTranscript = message
                )
            }
            return null
        }

        val conversationCount = points
            .filter { it.key == "conversation_present" && it.value.equals("true", ignoreCase = true) }
            .size

        val densityValues = points
            .filter { it.key == "speech_density_wpm" }
            .mapNotNull { it.value.toDoubleOrNull() }
        val avgDensity = if (densityValues.isNotEmpty()) densityValues.average() else 0.0

        val contexts = points
            .filter { it.key == "inferred_context" }
            .groupingBy { it.value }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val tags = points
            .filter { it.key == "context_tags" }
            .flatMap { parseTags(it.value) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(8)

        val latestTranscript = points
            .filter { it.key == "transcript_text" && it.value.isNotBlank() }
            .maxByOrNull { it.timestamp }
            ?.value
            ?.take(160)

        return VoiceContextInsight(
            samples7d = windows.size,
            conversationSamples = conversationCount,
            avgSpeechDensityWpm = avgDensity,
            topContexts = contexts,
            topTags = tags,
            latestTranscript = latestTranscript
        )
    }

    private fun parseTags(value: String): List<String> =
        try {
            Json.parseToJsonElement(value).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }

    // ── Utilities ────────────────────────────────────────────────────

    private fun hourOfDay(timestampMs: Long): Double {
        val ldt = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalTime()
        return ldt.hour + ldt.minute / 60.0
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.map { (it - mean) * (it - mean) }.average())
    }
}
