package com.potpal.mirrortrack.collectors.personal

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
import com.potpal.mirrortrack.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Speech-gated transcription.
 *
 * Continuously samples short ambient sound windows. When sustained non-quiet
 * sound is detected for ~10–12 seconds, fires up Vosk for a brief
 * transcription window. Otherwise stays cheap and idle.
 *
 * Avoids the fixed-interval problem of VoiceTranscriptionCollector
 * (transcribing silence) and the blind sampling of AmbientSoundCollector
 * (no signal about content). Audio is discarded; only derived signals are stored.
 */
@Singleton
class SpeechGatedTranscriptionCollector @Inject constructor() : Collector {

    override val id: String = "speech_gated_transcription"
    override val displayName: String = "Speech-Gated Transcription"
    override val rationale: String =
        "Continuously samples short ambient sound windows. When sustained " +
            "loudness suggests speech, runs Vosk on-device for a brief " +
            "transcription window. Audio is discarded; only derived signals " +
            "and context tags are stored."
    override val category: Category = Category.PERSONAL
    override val requiredPermissions: List<String> = listOf(Manifest.permission.RECORD_AUDIO)
    override val accessTier: AccessTier = AccessTier.RUNTIME
    override val defaultEnabled: Boolean = false
    override val defaultPollInterval: Duration? = null
    override val defaultRetention: Duration = 7.days

    override suspend fun isAvailable(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun stream(context: Context): Flow<DataPoint> = flow {
        val modelDir = VoiceModelInstaller.ensureInstalled(context)
        if (modelDir == null) {
            emit(DataPoint.string(id, category, "model_status", "missing"))
            emit(
                DataPoint.string(
                    id, category, "model_expected_path",
                    VoiceModelInstaller.expectedModelPath(context)
                )
            )
            return@flow
        }
        emit(DataPoint.string(id, category, "model_status", "ready"))

        val model = withContext(Dispatchers.IO) { Model(modelDir.absolutePath) }
        try {
            var consecutiveLoud = 0
            var lastTranscriptionAt = 0L

            while (true) {
                coroutineContext.ensureActive()
                if (!isAvailable(context)) {
                    delay(SENTRY_INTERVAL_MS)
                    continue
                }

                val sample = withContext(Dispatchers.IO) { sampleSentry() }
                if (sample == null) {
                    delay(SENTRY_INTERVAL_MS)
                    continue
                }

                emit(DataPoint.double(id, category, "ambient_dbfs", sample.dbfs))

                val now = System.currentTimeMillis()
                val cooldownActive = (now - lastTranscriptionAt) < COOLDOWN_MS
                val nonQuiet = sample.dbfs > QUIET_DBFS

                if (nonQuiet) consecutiveLoud++ else consecutiveLoud = 0

                if (consecutiveLoud >= TRIGGER_THRESHOLD && !cooldownActive) {
                    consecutiveLoud = 0
                    lastTranscriptionAt = now
                    emit(DataPoint.bool(id, category, "transcription_triggered", true))
                    val window = transcribeWindow(model)
                    window.toDataPoints().forEach { emit(it) }
                }

                delay(SENTRY_INTERVAL_MS)
            }
        } finally {
            try {
                model.close()
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sampleSentry(): SoundSample? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SENTRY_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val bufferSize = minBuffer.coerceAtLeast(SENTRY_SAMPLE_RATE / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SENTRY_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        return try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return null
            val buffer = ShortArray(bufferSize)
            var sumSquares = 0.0
            var samples = 0L
            val deadline = System.currentTimeMillis() + SENTRY_WINDOW_MS

            recorder.startRecording()
            while (System.currentTimeMillis() < deadline) {
                coroutineContext.ensureActive()
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                for (i in 0 until read) {
                    val v = buffer[i].toDouble()
                    sumSquares += v * v
                }
                samples += read
            }
            if (samples == 0L) return null
            val rms = sqrt(sumSquares / samples)
            val dbfs = 20.0 * log10((rms / Short.MAX_VALUE).coerceAtLeast(0.000_001))
            SoundSample(dbfs)
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
        }
    }

    private suspend fun transcribeWindow(model: Model): TranscriptWindow =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                var speechService: SpeechService? = null
                var recognizer: Recognizer? = null
                var bestText = ""
                val startedAt = System.currentTimeMillis()

                fun finish(error: String? = null) {
                    if (!continuation.isActive) return
                    try {
                        speechService?.stop()
                        speechService?.shutdown()
                    } catch (_: Exception) {
                    }
                    try {
                        recognizer?.close()
                    } catch (_: Exception) {
                    }
                    continuation.resume(
                        TranscriptWindow(
                            startedAt = startedAt,
                            durationMs = System.currentTimeMillis() - startedAt,
                            text = bestText.trim(),
                            error = error
                        )
                    )
                }

                try {
                    recognizer = Recognizer(model, VOSK_SAMPLE_RATE)
                    speechService = SpeechService(recognizer, VOSK_SAMPLE_RATE)
                    val listener = object : RecognitionListener {
                        override fun onPartialResult(hypothesis: String) {
                            val partial = extractText(hypothesis, "partial")
                            if (partial.length > bestText.length) bestText = partial
                        }

                        override fun onResult(hypothesis: String) {
                            val text = extractText(hypothesis, "text")
                            if (text.isNotBlank()) bestText = text
                        }

                        override fun onFinalResult(hypothesis: String) {
                            val text = extractText(hypothesis, "text")
                            if (text.isNotBlank()) bestText = text
                            finish()
                        }

                        override fun onError(exception: Exception) {
                            Logger.w(TAG, "Speech-gated transcription failed", exception)
                            finish(exception.message ?: "unknown")
                        }

                        override fun onTimeout() {
                            finish()
                        }
                    }
                    speechService?.startListening(listener, WINDOW_MS)
                } catch (e: Exception) {
                    Logger.w(TAG, "Unable to start speech-gated transcription", e)
                    finish(e.message ?: "unknown")
                }

                continuation.invokeOnCancellation {
                    try {
                        speechService?.cancel()
                        speechService?.shutdown()
                    } catch (_: Exception) {
                    }
                    try {
                        recognizer?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private fun TranscriptWindow.toDataPoints(): List<DataPoint> {
        val tags = inferTags(text)
        val context = inferContext(tags, text)
        val wordCount = text.splitToSequence(Regex("\\s+")).count { it.isNotBlank() }
        val density = if (durationMs > 0) wordCount / (durationMs / 60_000.0) else 0.0
        val points = mutableListOf<DataPoint>(
            DataPoint.long(id, category, "window_duration_ms", durationMs),
            DataPoint.long(id, category, "transcript_word_count", wordCount.toLong()),
            DataPoint.double(id, category, "speech_density_wpm", density),
            DataPoint.bool(id, category, "conversation_present", wordCount >= MIN_WORDS_FOR_CONTEXT),
            DataPoint.string(id, category, "inferred_context", context),
            DataPoint.json(id, category, "context_tags", json.encodeToString(tags))
        )
        if (text.isNotBlank()) {
            points += DataPoint.string(id, category, "transcript_text", text)
        }
        if (error != null) {
            points += DataPoint.string(id, category, "error", error)
        }
        return points
    }

    private fun inferTags(text: String): List<String> {
        val normalized = text.lowercase()
        val tags = mutableSetOf<String>()
        keywordTags.forEach { (tag, words) ->
            if (words.any { normalized.contains(it) }) tags += tag
        }
        return tags.toList().sorted()
    }

    private fun inferContext(tags: List<String>, text: String): String {
        val wordCount = text.splitToSequence(Regex("\\s+")).count { it.isNotBlank() }
        return when {
            wordCount < MIN_WORDS_FOR_CONTEXT -> "quiet_or_no_speech"
            "work" in tags || "meeting" in tags -> "work_or_meeting"
            "errand" in tags || "travel" in tags -> "errand_or_travel"
            "home" in tags || "family" in tags -> "home_or_family"
            "social" in tags -> "social"
            "media" in tags -> "media_or_background_audio"
            else -> "conversation"
        }
    }

    private fun extractText(resultJson: String, key: String): String =
        try {
            json.parseToJsonElement(resultJson).jsonObject[key]?.jsonPrimitive?.content.orEmpty()
        } catch (_: Exception) {
            ""
        }

    private data class SoundSample(val dbfs: Double)

    private data class TranscriptWindow(
        val startedAt: Long,
        val durationMs: Long,
        val text: String,
        val error: String?
    )

    private companion object {
        const val TAG = "SpeechGatedTranscription"
        const val SENTRY_SAMPLE_RATE = 8_000
        // Each sentry pass opens the mic for SENTRY_WINDOW_MS, then sleeps for
        // SENTRY_INTERVAL_MS before the next pass. Keeping the gap large (≥30s)
        // is critical for battery: the previous 1.5s/1.5s pattern meant the mic
        // was open ~50% of the time. With these defaults the mic-on duty cycle
        // for sentry passes is well under 5%.
        const val SENTRY_WINDOW_MS = 1_500L
        const val SENTRY_INTERVAL_MS = 30_000L
        const val QUIET_DBFS = -45.0
        // Three consecutive loud passes (~90s of sustained activity at the new
        // interval) before a transcription is triggered.
        const val TRIGGER_THRESHOLD = 3
        const val COOLDOWN_MS = 5 * 60_000L
        const val VOSK_SAMPLE_RATE = 16_000.0f
        // Capped at 8s — long enough to capture a sentence, short enough that a
        // mid-recording cancel can never leak more than that.
        const val WINDOW_MS = 8_000
        const val MIN_WORDS_FOR_CONTEXT = 4

        val json = Json { ignoreUnknownKeys = true }

        val keywordTags = mapOf(
            "work" to listOf("work", "client", "project", "deadline", "report", "email"),
            "meeting" to listOf("meeting", "agenda", "schedule", "call", "zoom", "teams"),
            "home" to listOf("home", "kitchen", "dinner", "laundry", "chores"),
            "family" to listOf("mom", "dad", "child", "kids", "school", "family"),
            "social" to listOf("friend", "party", "dinner", "coffee", "hang out"),
            "travel" to listOf("drive", "traffic", "airport", "train", "bus", "route"),
            "errand" to listOf("store", "groceries", "appointment", "pickup", "pharmacy"),
            "media" to listOf("subscribe", "episode", "breaking news", "advertisement", "sponsored")
        )
    }
}
