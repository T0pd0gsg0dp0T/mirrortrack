package com.potpal.mirrortrack.export

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.data.CryptoManager
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseHolder: DatabaseHolder,
    private val ingestor: Ingestor,
    private val cryptoManager: CryptoManager
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        Logger.i(TAG, "Starting export")

        ingestor.flush()

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Logger.e(TAG, "Database file not found")
            return@withContext
        }

        val salt: ByteArray? = try {
            cryptoManager.getSalt(context)
        } catch (e: Exception) {
            Logger.w(TAG, "Could not read salt", e)
            null
        }

        val manifest = buildManifest()

        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext

        outputStream.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(DB_NAME))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                if (salt != null) {
                    zip.putNextEntry(ZipEntry("salt.bin"))
                    zip.write(salt)
                    zip.closeEntry()
                }

                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        Logger.i(TAG, "Export completed")
    }

    private fun buildManifest(): String {
        val versionCode = try {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        } catch (_: Exception) { 1L }

        return """{"kdf":"argon2id","t":3,"m":65536,"p":2,"app_version_code":$versionCode,"export_ts_ms":${System.currentTimeMillis()}}"""
    }

    companion object {
        private const val TAG = "ExportManager"
        private const val DB_NAME = "mirrortrack.db"
    }
}
