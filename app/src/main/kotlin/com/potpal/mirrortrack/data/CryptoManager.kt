package com.potpal.mirrortrack.data

import android.content.Context
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cryptoDataStore by preferencesDataStore(name = "mirrortrack_crypto")

/**
 * Key derivation and lifecycle for the SQLCipher master key.
 *
 * Design:
 *  1. On first launch, generate a 16-byte random salt and store it in
 *     DataStore. Salt is not secret — it's fine on disk.
 *  2. User enters a passphrase. We run Argon2id over (passphrase, salt) to
 *     produce a 32-byte key. Argon2 parameters are tuned so derivation
 *     takes ~500ms on a mid-range device — this is the offline-brute-force
 *     cost for any attacker who exfiltrates the encrypted DB.
 *  3. That 32-byte key is handed to SQLCipher as the raw key. SQLCipher
 *     runs its own internal PBKDF2, but since our input is already
 *     high-entropy, that's belt-and-suspenders.
 *
 * What this does NOT yet do (future hardening):
 *  - Wrap the derived key with an Android Keystore AES-GCM key requiring
 *    biometric auth. That would prevent an attacker with physical device
 *    access + unlocked phone from dumping the DB without explicit biometric
 *    re-auth.
 *  - Rotate the salt on passphrase change (currently passphrase change
 *    requires full re-encryption of the DB — implement via ATTACH + rekey).
 *
 * Implemented:
 *  - Panic PIN: silently wipes DB — see UnlockViewModel.performPanicWipe().
 *
 * Memory hygiene:
 *  - Callers MUST zero the returned ByteArray after SQLCipher consumes it.
 *  - Passphrases are accepted as CharArray (not String) and zeroed here
 *    before returning. String is immutable in JVM and can't be reliably
 *    wiped from the intern pool.
 */
@Singleton
class CryptoManager @Inject constructor() {

    private val argon2 = Argon2Kt()

    /**
     * Derive the 32-byte SQLCipher key from the user's passphrase and the
     * per-install salt. Creates the salt on first call.
     *
     * The returned ByteArray MUST be zeroed by the caller after use.
     * The passphrase CharArray IS zeroed by this function before returning.
     */
    suspend fun deriveKey(context: Context, passphrase: CharArray): ByteArray {
        try {
            val salt = getOrCreateSalt(context)
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passphrase.toByteArrayUtf8(),
                salt = salt,
                tCostInIterations = ARGON2_T_COST,
                mCostInKibibyte = ARGON2_M_COST_KIB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = KEY_LENGTH_BYTES
            )
            return result.rawHashAsByteArray()
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private suspend fun getOrCreateSalt(context: Context): ByteArray {
        val prefs = context.cryptoDataStore.data.first()
        val existing = prefs[SALT_KEY]
        if (existing != null && existing.size == SALT_LENGTH_BYTES) return existing

        val fresh = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        context.cryptoDataStore.edit { edits ->
            edits[SALT_KEY] = fresh
            edits[SALT_VERSION_KEY] = 1
        }
        return fresh
    }

    /**
     * Returns the stored salt, or null if none exists yet.
     * Used by ExportManager to bundle salt.bin in the export zip.
     */
    suspend fun getSalt(context: Context): ByteArray? {
        val prefs = context.cryptoDataStore.data.first()
        return prefs[SALT_KEY]
    }

    /**
     * Replaces the stored salt. Used by ImportManager to restore
     * the salt from a backup archive.
     */
    suspend fun setSalt(context: Context, salt: ByteArray) {
        context.cryptoDataStore.edit { edits ->
            edits[SALT_KEY] = salt
        }
    }

    private fun CharArray.toByteArrayUtf8(): ByteArray {
        val cb = java.nio.CharBuffer.wrap(this)
        val bb = Charsets.UTF_8.encode(cb)
        val out = ByteArray(bb.remaining())
        bb.get(out)
        // Zero the intermediate ByteBuffer's backing array if accessible
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    companion object {
        // Tune these on your target device. Goal: ~500ms derivation.
        // mCost in KiB = 64 MB. tCost = 3 passes. Parallelism = 2 lanes.
        // On a Pixel 6 these produce ~400-600ms; on a $100 phone, ~1.5s
        // which is still acceptable for a once-per-app-open cost.
        private const val ARGON2_T_COST = 3
        private const val ARGON2_M_COST_KIB = 65_536
        private const val ARGON2_PARALLELISM = 2

        private const val KEY_LENGTH_BYTES = 32
        private const val SALT_LENGTH_BYTES = 16

        private val SALT_KEY = byteArrayPreferencesKey("argon2_salt")
        private val SALT_VERSION_KEY = intPreferencesKey("argon2_salt_version")
    }
}
