package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class MediaExifCollector @Inject constructor() : Collector {
    override val id = "media_exif"
    override val displayName = "Media EXIF"
    override val rationale =
        "Reads EXIF metadata from photos. Requires READ_MEDIA_IMAGES (A13+) " +
        "or READ_EXTERNAL_STORAGE."
    override val category = Category.PERSONAL
    override val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= 34) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 90.days

    @Volatile private var lastRunTimestamp = 0L

    override suspend fun isAvailable(context: Context): Boolean =
        requiredPermissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val since = lastRunTimestamp
        val now = System.currentTimeMillis()

        try {
            val selection = if (since > 0) "${MediaStore.Images.Media.DATE_ADDED} > ?" else null
            val args = if (since > 0) arrayOf((since / 1000).toString()) else null

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.SIZE
                ),
                selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val wIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val mediaId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)

                    val json = StringBuilder()
                    json.append("{\"media_id\":").append(mediaId)
                    json.append(",\"date_taken\":").append(if (dateIdx >= 0) cursor.getLong(dateIdx) else 0)
                    json.append(",\"mime_type\":\"").append(if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else "").append("\"")
                    json.append(",\"width\":").append(if (wIdx >= 0) cursor.getInt(wIdx) else 0)
                    json.append(",\"height\":").append(if (hIdx >= 0) cursor.getInt(hIdx) else 0)
                    json.append(",\"size_bytes\":").append(if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0)

                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val exif = ExifInterface(stream)
                            val ll = exif.latLong
                            if (ll != null) {
                                json.append(",\"exif_gps_lat\":").append(ll[0])
                                json.append(",\"exif_gps_lon\":").append(ll[1])
                            }
                            json.append(",\"exif_camera_make\":\"").append(exif.getAttribute(ExifInterface.TAG_MAKE) ?: "").append("\"")
                            json.append(",\"exif_camera_model\":\"").append(exif.getAttribute(ExifInterface.TAG_MODEL) ?: "").append("\"")
                        }
                    } catch (_: Exception) { }

                    json.append("}")
                    points.add(DataPoint.json(id, category, "image_$mediaId", json.toString()))
                }
            }
            lastRunTimestamp = now
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        }
        return points
    }

    companion object {
        private const val TAG = "MediaExif"
    }
}
