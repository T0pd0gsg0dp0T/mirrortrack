package com.potpal.mirrortrack.collectors.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Singleton
class AmbientSoundCollector @Inject constructor() : Collector {

    override val id: String = "ambient_sound"
    override val displayName: String = "Ambient Sound Level"
    override val rationale: String =
        "Samples a short microphone window and stores only aggregate loudness metrics. " +
            "No raw audio or transcript is retained."
    override val category: Category = Category.SENSORS
    override val requiredPermissions: List<String> = listOf(Manifest.permission.RECORD_AUDIO)
    override val accessTier: AccessTier = AccessTier.RUNTIME
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration? = 10.minutes
    override val defaultRetention: Duration = 7.days

    override suspend fun isAvailable(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun collect(context: Context): List<DataPoint> {
        if (!isAvailable(context)) return emptyList()
        val sample = withContext(Dispatchers.IO) { sampleSoundLevel() } ?: return emptyList()
        return listOf(
            DataPoint.double(id, category, "ambient_sound_dbfs", sample.dbfs),
            DataPoint.double(id, category, "ambient_sound_rms", sample.rms),
            DataPoint.long(id, category, "ambient_sound_peak", sample.peak.toLong()),
            DataPoint.string(id, category, "ambient_sound_label", soundLabel(sample.dbfs))
        )
    }

    @SuppressLint("MissingPermission")
    private fun sampleSoundLevel(): SoundSample? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null

        val bufferSize = minBuffer.coerceAtLeast(SAMPLE_RATE / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        return try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return null
            val buffer = ShortArray(bufferSize)
            var sumSquares = 0.0
            var samples = 0L
            var peak = 0
            val deadline = System.currentTimeMillis() + SAMPLE_WINDOW_MS

            recorder.startRecording()
            while (System.currentTimeMillis() < deadline) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                for (i in 0 until read) {
                    val value = buffer[i].toInt()
                    val abs = kotlin.math.abs(value)
                    if (abs > peak) peak = abs
                    sumSquares += value.toDouble() * value.toDouble()
                }
                samples += read
            }

            if (samples == 0L) {
                null
            } else {
                val rms = sqrt(sumSquares / samples)
                val dbfs = 20.0 * log10((rms / Short.MAX_VALUE).coerceAtLeast(0.000_001))
                SoundSample(dbfs = dbfs, rms = rms, peak = peak)
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
        }
    }

    private fun soundLabel(dbfs: Double): String = when {
        dbfs <= QUIET_DBFS -> "quiet"
        dbfs <= MODERATE_DBFS -> "moderate"
        else -> "loud"
    }

    private data class SoundSample(
        val dbfs: Double,
        val rms: Double,
        val peak: Int
    )

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val SAMPLE_WINDOW_MS = 3_000L
        const val QUIET_DBFS = -45.0
        const val MODERATE_DBFS = -30.0
    }
}
