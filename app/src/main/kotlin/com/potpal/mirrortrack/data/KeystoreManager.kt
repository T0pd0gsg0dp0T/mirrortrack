package com.potpal.mirrortrack.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.potpal.mirrortrack.util.Logger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps/unwraps the Argon2-derived DB key using Android Keystore.
 * The Keystore key requires biometric authentication, so an attacker
 * with the encrypted DB cannot brute-force without the device biometric.
 *
 * Feature gated behind Settings toggle 'Enable biometric unlock'. Off by default.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun isKeyAvailable(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    fun generateKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(spec)
        keyGen.generateKey()
    }

    fun wrapKey(rawKey: ByteArray): ByteArray {
        val secretKey = getKey() ?: throw IllegalStateException("Keystore key not found")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(rawKey)
        // Prepend IV length (1 byte) + IV + encrypted data
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    /**
     * Returns a Cipher initialized for unwrapping. The caller must pass this
     * to BiometricPrompt.CryptoObject so the user authenticates before the
     * cipher can be used.
     */
    fun getUnwrapCipher(wrappedBlob: ByteArray): Cipher {
        val ivLen = wrappedBlob[0].toInt() and 0xFF
        val iv = wrappedBlob.sliceArray(1..ivLen)
        val secretKey = getKey() ?: throw IllegalStateException("Keystore key not found")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher
    }

    fun unwrapKey(cipher: Cipher, wrappedBlob: ByteArray): ByteArray {
        val ivLen = wrappedBlob[0].toInt() and 0xFF
        val encrypted = wrappedBlob.sliceArray((1 + ivLen) until wrappedBlob.size)
        return cipher.doFinal(encrypted)
    }

    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getKey(): SecretKey? =
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey

    companion object {
        private const val TAG = "KeystoreMgr"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mirrortrack_db_wrapper"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }
}
