package com.potpal.mirrortrack.export

import android.content.Context
import android.net.Uri
import com.potpal.mirrortrack.data.CryptoManager
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports a previously exported MirrorTrack backup (zip with DB + salt).
 *
 * The import replaces the current database and salt. The user must
 * know the original passphrase — the imported DB is still encrypted
 * with that passphrase's derived key.
 *
 * Flow:
 *  1. Close current database
 *  2. Extract zip to staging directory
 *  3. Validate: DB file must exist in archive
 *  4. Backup current DB, replace with imported
 *  5. Restore salt if present
 *  6. User re-enters passphrase to unlock the imported DB
 */
@Singleton
class ImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseHolder: DatabaseHolder,
    private val cryptoManager: CryptoManager
) {
    sealed interface ImportResult {
        data object Success : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        Logger.i(TAG, "Starting import")

        val staging = File(context.cacheDir, "import_staging")
        staging.deleteRecursively()
        staging.mkdirs()

        try {
            // Extract zip
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Cannot open file")

            val extractedFiles = mutableSetOf<String>()
            inputStream.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.contains("..")) {
                            return@withContext ImportResult.Error("Invalid archive: path traversal")
                        }
                        val outFile = File(staging, name)
                        outFile.outputStream().use { zip.copyTo(it) }
                        extractedFiles.add(name)
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            // Validate
            if (DB_NAME !in extractedFiles) {
                return@withContext ImportResult.Error("Archive missing database file")
            }

            val importedDb = File(staging, DB_NAME)
            if (importedDb.length() < 1024) {
                return@withContext ImportResult.Error("Database file too small — corrupt?")
            }

            // Close current DB
            databaseHolder.close()

            // Replace DB file
            val targetDb = context.getDatabasePath(DB_NAME)
            targetDb.parentFile?.mkdirs()

            // Backup current DB
            val backup = File(targetDb.path + ".bak")
            if (targetDb.exists()) {
                targetDb.copyTo(backup, overwrite = true)
            }

            try {
                importedDb.copyTo(targetDb, overwrite = true)

                // Delete WAL/SHM from old DB
                File(targetDb.path + "-shm").delete()
                File(targetDb.path + "-wal").delete()

                // Replace salt if present in archive
                val saltFile = File(staging, "salt.bin")
                if (saltFile.exists()) {
                    val saltBytes = saltFile.readBytes()
                    if (saltBytes.size == 16) {
                        cryptoManager.setSalt(context, saltBytes)
                        Logger.i(TAG, "Salt restored from archive")
                    }
                }

                Logger.i(TAG, "Import completed — user must re-enter passphrase")
                backup.delete()
                ImportResult.Success
            } catch (e: Exception) {
                // Restore backup on failure
                if (backup.exists()) {
                    backup.copyTo(targetDb, overwrite = true)
                }
                backup.delete()
                Logger.e(TAG, "Import failed, restored backup", e)
                ImportResult.Error("Import failed: ${e.message}")
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        private const val TAG = "ImportManager"
        private const val DB_NAME = "mirrortrack.db"
    }
}
