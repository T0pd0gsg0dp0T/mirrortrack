package com.potpal.mirrortrack.ui.unlock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potpal.mirrortrack.data.CryptoManager
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.scheduling.CollectionScheduler
import com.potpal.mirrortrack.settings.CollectorPreferences
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UnlockUiState {
    data object Idle : UnlockUiState
    data object Loading : UnlockUiState
    data object Success : UnlockUiState
    data class Error(val message: String) : UnlockUiState
    data object PanicWiped : UnlockUiState
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val databaseHolder: DatabaseHolder,
    private val prefs: CollectorPreferences,
    private val scheduler: CollectionScheduler
) : ViewModel() {

    private val _state = MutableStateFlow<UnlockUiState>(UnlockUiState.Idle)
    val state: StateFlow<UnlockUiState> = _state

    val isFirstRun: Boolean
        get() = !databaseExists()

    fun isDbOpen(): Boolean = databaseHolder.isOpen()

    fun unlock(passphrase: CharArray) {
        _state.value = UnlockUiState.Loading
        viewModelScope.launch {
            try {
                // Check panic PIN first
                val panicHash = prefs.getPanicPinHash().first()
                if (panicHash != null) {
                    val panicSalt = prefs.getPanicPinSalt().first()
                    if (panicSalt != null) {
                        val inputHash = derivePanicHash(passphrase.clone(), panicSalt)
                        if (inputHash == panicHash) {
                            performPanicWipe()
                            return@launch
                        }
                    }
                }

                val rawKey = cryptoManager.deriveKey(context, passphrase)
                // passphrase is already zeroed by deriveKey
                try {
                    databaseHolder.open(rawKey)
                    scheduler.refreshAll()
                    // rawKey is zeroed by DatabaseHolder.open
                    _state.value = UnlockUiState.Success
                } catch (e: Exception) {
                    rawKey.fill(0)
                    Logger.w(TAG, "Unlock failed", e)
                    _state.value = UnlockUiState.Error("Incorrect passphrase")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Key derivation failed", e)
                _state.value = UnlockUiState.Error("Key derivation failed")
            }
        }
    }

    private suspend fun derivePanicHash(pin: CharArray, salt: ByteArray): String {
        try {
            val key = cryptoManager.deriveKey(context, pin)
            // pin is zeroed by deriveKey
            val hash = key.joinToString("") { "%02x".format(it) }
            key.fill(0)
            return hash
        } catch (e: Exception) {
            pin.fill('\u0000')
            return ""
        }
    }

    private suspend fun performPanicWipe() {
        try {
            databaseHolder.close()
            val dbFile = context.getDatabasePath("mirrortrack.db")
            dbFile.delete()
            val shmFile = java.io.File(dbFile.path + "-shm")
            val walFile = java.io.File(dbFile.path + "-wal")
            shmFile.delete()
            walFile.delete()

            val cryptoPrefs = context.getDir("datastore", Context.MODE_PRIVATE)
            cryptoPrefs.listFiles()?.forEach { it.delete() }

            prefs.clearAll()

            _state.value = UnlockUiState.PanicWiped
        } catch (e: Exception) {
            _state.value = UnlockUiState.PanicWiped
        }
    }

    private fun databaseExists(): Boolean {
        val dbFile = context.getDatabasePath("mirrortrack.db")
        return dbFile.exists()
    }

    fun resetState() {
        _state.value = UnlockUiState.Idle
    }

    companion object {
        private const val TAG = "UnlockVM"
    }
}
