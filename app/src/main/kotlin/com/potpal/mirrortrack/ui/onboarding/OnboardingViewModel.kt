package com.potpal.mirrortrack.ui.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.scheduling.CollectionScheduler
import com.potpal.mirrortrack.settings.CollectorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CollectorRegistry,
    private val prefs: CollectorPreferences,
    private val scheduler: CollectionScheduler,
    private val ingestor: Ingestor
) : ViewModel() {

    val entrySeen: Flow<Boolean> = prefs.isOnboardingEntrySeen()

    suspend fun entrySeenOnce(): Boolean = prefs.isOnboardingEntrySeenSync()

    fun stepDecided(group: PermissionGroup): Flow<Boolean> =
        prefs.isOnboardingStepDecided(group.id)

    /** Combined flow: list of groups that the user has not yet decided on. */
    fun undecidedGroups(): Flow<List<PermissionGroup>> {
        val flows = PermissionGroup.entries.map { group ->
            prefs.isOnboardingStepDecided(group.id).map { decided -> group to decided }
        }
        return combine(flows) { pairs ->
            pairs.filter { (_, decided) -> !decided }.map { it.first }
        }
    }

    /** Picks the next group the user hasn't decided on. Returns null when done. */
    suspend fun firstUndecidedGroup(): PermissionGroup? {
        for (group in PermissionGroup.entries) {
            if (!prefs.isOnboardingStepDecided(group.id).first()) return group
        }
        return null
    }

    /**
     * Marks a step as decided. If granted == true, also enables every
     * collector in the group whose runtime permission is currently held
     * (or whose special-access requirement is satisfied). Collectors whose
     * permission was denied stay disabled — the user can revisit later.
     */
    fun markDecided(group: PermissionGroup, granted: Boolean) {
        viewModelScope.launch {
            prefs.setOnboardingStepDecided(group.id, true)
            if (!granted) {
                scheduler.refreshAll()
                return@launch
            }
            for (collectorId in group.collectorIds) {
                val collector = registry.byId(collectorId) ?: continue
                val available = try { collector.isAvailable(context) } catch (_: Throwable) { false }
                if (available) {
                    prefs.setEnabled(collectorId, true)
                    // Best-effort backfill so the first card has data fast.
                    try {
                        val points = collector.collect(context)
                        if (points.isNotEmpty()) {
                            ingestor.submitAll(points)
                            ingestor.flush()
                        }
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
            scheduler.refreshAll()
        }
    }

    fun markEntrySeen() {
        viewModelScope.launch {
            prefs.setOnboardingEntrySeen(true)
        }
    }

    /** Used by the "Skip all" path on the welcome screen. */
    fun skipAll() {
        viewModelScope.launch {
            for (group in PermissionGroup.entries) {
                prefs.setOnboardingStepDecided(group.id, true)
            }
            prefs.setOnboardingEntrySeen(true)
        }
    }

    /** Snapshot of current OS-level permission state, for showing badges. */
    fun isRuntimePermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isSpecialAccessGranted(kind: SpecialAccessKind): Boolean = when (kind) {
        SpecialAccessKind.NOTIFICATION_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        SpecialAccessKind.USAGE_ACCESS -> {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Insight cards whose primary collector is in a still-undecided group —
     * used by InsightsScreen to render the "locked" footer.
     */
    fun lockedCardCount(undecided: List<PermissionGroup>): Int {
        if (undecided.isEmpty()) return 0
        val undecidedCollectorIds = undecided.flatMap { it.collectorIds }.toSet()
        return com.potpal.mirrortrack.ui.insights.InsightDependencyGraph.cards
            .count { card -> card.primary.any { it in undecidedCollectorIds } }
    }

    /** Always-on (NONE-tier) collectors, used to introduce the welcome screen. */
    fun alwaysOnCollectorNames(): List<String> =
        registry.all()
            .filter { it.accessTier == AccessTier.NONE }
            .map { it.displayName }

    companion object {
        // Stable ordered list shown on screen and used by the "next undecided"
        // helper. Mirrors PermissionGroup.entries but exposed for clarity.
        val GROUPS: List<PermissionGroup> get() = PermissionGroup.entries.toList()
    }
}
