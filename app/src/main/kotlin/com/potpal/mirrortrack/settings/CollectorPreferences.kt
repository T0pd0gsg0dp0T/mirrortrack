package com.potpal.mirrortrack.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.collectorDataStore by preferencesDataStore(name = "mirrortrack_collectors")

@Singleton
class CollectorPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isEnabled(collectorId: String): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("collector.$collectorId.enabled")] ?: false
        }

    suspend fun isEnabledSync(collectorId: String): Boolean =
        context.collectorDataStore.data.first()[booleanPreferencesKey("collector.$collectorId.enabled")] ?: false

    suspend fun setEnabled(collectorId: String, enabled: Boolean) {
        context.collectorDataStore.data.first()
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("collector.$collectorId.enabled")] = enabled
        }
    }

    fun getPollIntervalMinutes(collectorId: String): Flow<Int?> =
        context.collectorDataStore.data.map { prefs ->
            prefs[intPreferencesKey("collector.$collectorId.pollIntervalMinutes")]
        }

    suspend fun getPollIntervalMinutesSync(collectorId: String): Int? =
        context.collectorDataStore.data.first()[intPreferencesKey("collector.$collectorId.pollIntervalMinutes")]

    suspend fun setPollIntervalMinutes(collectorId: String, minutes: Int) {
        context.collectorDataStore.edit { prefs ->
            prefs[intPreferencesKey("collector.$collectorId.pollIntervalMinutes")] = minutes
        }
    }

    fun getRetentionDays(collectorId: String): Flow<Long?> =
        context.collectorDataStore.data.map { prefs ->
            prefs[longPreferencesKey("collector.$collectorId.retentionDays")]
        }

    suspend fun setRetentionDays(collectorId: String, days: Long) {
        context.collectorDataStore.edit { prefs ->
            prefs[longPreferencesKey("collector.$collectorId.retentionDays")] = days
        }
    }

    fun isServiceEnabled(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("service.enabled")] ?: false
        }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("service.enabled")] = enabled
        }
    }

    fun isCollectionNotificationDetailsEnabled(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("notification.collection_details.enabled")] ?: true
        }

    suspend fun isCollectionNotificationDetailsEnabledSync(): Boolean =
        context.collectorDataStore.data.first()[booleanPreferencesKey("notification.collection_details.enabled")] ?: true

    suspend fun setCollectionNotificationDetailsEnabled(enabled: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("notification.collection_details.enabled")] = enabled
        }
    }

    fun getPanicPinHash(): Flow<String?> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("panic.pin_hash")]
        }

    suspend fun setPanicPinHash(hash: String) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("panic.pin_hash")] = hash
        }
    }

    fun getPanicPinSalt(): Flow<ByteArray?> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("panic.pin_salt")]?.let { hex ->
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }

    suspend fun setPanicPinSalt(salt: ByteArray) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("panic.pin_salt")] = salt.joinToString("") { "%02x".format(it) }
        }
    }

    fun isBiometricEnabled(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("biometric.enabled")] ?: false
        }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("biometric.enabled")] = enabled
        }
    }

    fun getWrappedKey(): Flow<ByteArray?> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("biometric.wrapped_key")]?.let { hex ->
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }

    suspend fun setWrappedKey(wrapped: ByteArray) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("biometric.wrapped_key")] = wrapped.joinToString("") { "%02x".format(it) }
        }
    }

    fun getContactsMode(): Flow<String> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("collector.contacts.mode")] ?: "hashed"
        }

    suspend fun setContactsMode(mode: String) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("collector.contacts.mode")] = mode
        }
    }

    fun isNotificationContentEnabled(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("collector.notification_listener.record_contents")] ?: false
        }

    suspend fun setNotificationContentEnabled(enabled: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("collector.notification_listener.record_contents")] = enabled
        }
    }

    fun isBackgroundLocationEnabled(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("collector.location.background")] ?: false
        }

    suspend fun setBackgroundLocationEnabled(enabled: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("collector.location.background")] = enabled
        }
    }

    // --- Insights: location cluster names ---

    suspend fun getClusterNames(): Map<String, String> {
        val prefs = context.collectorDataStore.data.first()
        return prefs.asMap()
            .filter { it.key.name.startsWith("cluster.name.") }
            .mapNotNull { entry ->
                val id = entry.key.name.removePrefix("cluster.name.")
                val name = entry.value as? String ?: return@mapNotNull null
                id to name
            }
            .toMap()
    }

    suspend fun setClusterName(clusterId: String, name: String) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("cluster.name.$clusterId")] = name
        }
    }

    // --- Insights: dismissed anomalies ---

    suspend fun getDismissedAnomalyIds(): Set<String> {
        val prefs = context.collectorDataStore.data.first()
        return prefs[stringSetPreferencesKey("insights.dismissed_anomalies")] ?: emptySet()
    }

    suspend fun dismissAnomaly(anomalyId: String) {
        context.collectorDataStore.edit { prefs ->
            val current = prefs[stringSetPreferencesKey("insights.dismissed_anomalies")] ?: emptySet()
            prefs[stringSetPreferencesKey("insights.dismissed_anomalies")] = current + anomalyId
        }
    }

    fun getInsightCardOrder(): Flow<List<String>> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("insights.card_order")]
                ?.split("|")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    suspend fun setInsightCardOrder(order: List<String>) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("insights.card_order")] = order
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("|")
        }
    }

    suspend fun clearInsightCardOrder() {
        context.collectorDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("insights.card_order"))
        }
    }

    // --- Insights: anomaly thresholds ---

    fun getAnomalyThreshold(key: String, default: Float): Flow<Float> =
        context.collectorDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("anomaly.threshold.$key")]?.toFloatOrNull() ?: default
        }

    suspend fun getAnomalyThresholdSync(key: String, default: Float): Float =
        context.collectorDataStore.data.first()[stringPreferencesKey("anomaly.threshold.$key")]?.toFloatOrNull() ?: default

    suspend fun setAnomalyThreshold(key: String, value: Float) {
        context.collectorDataStore.edit { prefs ->
            prefs[stringPreferencesKey("anomaly.threshold.$key")] = value.toString()
        }
    }

    // --- Onboarding ---

    /** True once the user has finished or explicitly dismissed onboarding. */
    fun isOnboardingEntrySeen(): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("onboarding.entry_seen")] ?: false
        }

    suspend fun isOnboardingEntrySeenSync(): Boolean =
        context.collectorDataStore.data.first()[booleanPreferencesKey("onboarding.entry_seen")] ?: false

    suspend fun setOnboardingEntrySeen(seen: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("onboarding.entry_seen")] = seen
        }
    }

    /** True if the user has made a Grant/Skip decision for this group. */
    fun isOnboardingStepDecided(groupId: String): Flow<Boolean> =
        context.collectorDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("onboarding.step.$groupId.decided")] ?: false
        }

    suspend fun setOnboardingStepDecided(groupId: String, decided: Boolean) {
        context.collectorDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("onboarding.step.$groupId.decided")] = decided
        }
    }

    suspend fun clearAll() {
        context.collectorDataStore.edit { it.clear() }
    }
}
