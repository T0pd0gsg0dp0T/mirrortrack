package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Periodic offline speech transcription.
 *
 * The collector records short windows, runs Vosk locally, stores recognized
 * text plus low-dimensional context tags, and discards audio immediately.
 */
@Singleton
class VoiceTranscriptionCollector @Inject constructor() : Collector {

    override val id: String = "voice_transcription"
    override val displayName: String = "Voice Transcription"
    override val rationale: String =
        "Periodically records short microphone windows, transcribes them on-device " +
            "with Vosk, discards audio, and uses text/context tags to improve " +
            "activity, routine, and social-context inference."
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
            emit(DataPoint.string(id, category, "model_expected_path", VoiceModelInstaller.expectedModelPath(context)))
            return@flow
        }
        emit(DataPoint.string(id, category, "model_status", "ready"))

        val model = withContext(Dispatchers.IO) { Model(modelDir.absolutePath) }
        try {
            while (true) {
                coroutineContext.ensureActive()
                val window = transcribeWindow(model)
                window.toDataPoints().forEach { emit(it) }
                delay(SAMPLE_INTERVAL_MS)
            }
        } finally {
            try {
                model.close()
            } catch (_: Exception) {
            }
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
                    recognizer = Recognizer(model, SAMPLE_RATE)
                    speechService = SpeechService(recognizer, SAMPLE_RATE)
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
                            Logger.w(TAG, "Voice transcription failed", exception)
                            finish(exception.message ?: "unknown")
                        }

                        override fun onTimeout() {
                            finish()
                        }
                    }
                    speechService?.startListening(listener, WINDOW_MS)
                } catch (e: Exception) {
                    Logger.w(TAG, "Unable to start voice transcription", e)
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

    private data class TranscriptWindow(
        val startedAt: Long,
        val durationMs: Long,
        val text: String,
        val error: String?
    )

    private companion object {
        const val TAG = "VoiceTranscription"
        const val SAMPLE_RATE = 16_000.0f
        // Capped at 8s per recording. Long enough to capture a usable sentence,
        // short enough that cancellation mid-recording cannot leak more than
        // 8s of further mic-on time.
        const val WINDOW_MS = 8_000
        const val SAMPLE_INTERVAL_MS = 15 * 60_000L
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

object VoiceModelInstaller {
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"

    fun isInstalled(context: Context): Boolean =
        findModelDir(context) != null

    suspend fun ensureInstalled(context: Context): File? =
        withContext(Dispatchers.IO) {
            findModelDir(context)?.let { return@withContext it }
            if (!assetPathExists(context, MODEL_DIR_NAME)) return@withContext null

            val target = File(context.filesDir, MODEL_DIR_NAME)
            val staging = File(context.filesDir, "$MODEL_DIR_NAME.tmp")
            try {
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()
                copyAssetTree(context, MODEL_DIR_NAME, staging)
                if (!File(staging, "am").exists() || !File(staging, "conf").exists()) {
                    staging.deleteRecursively()
                    return@withContext null
                }
                if (target.exists()) target.deleteRecursively()
                if (!staging.renameTo(target)) {
                    copyDirectory(staging, target)
                    staging.deleteRecursively()
                }
                findModelDir(context)
            } catch (e: Exception) {
                Logger.w("VoiceModelInstaller", "Unable to install bundled voice model", e)
                staging.deleteRecursively()
                null
            }
        }

    fun expectedModelPath(context: Context): String =
        File(context.filesDir, MODEL_DIR_NAME).absolutePath

    private fun findModelDir(context: Context): File? =
        modelCandidates(context).firstOrNull { dir ->
            dir.isDirectory && File(dir, "am").exists() && File(dir, "conf").exists()
        }

    private fun modelCandidates(context: Context): List<File> = listOfNotNull(
        File(context.filesDir, MODEL_DIR_NAME),
        File(context.filesDir, "vosk/model"),
        context.getExternalFilesDir(null)?.let { File(it, MODEL_DIR_NAME) },
        context.getExternalFilesDir(null)?.let { File(it, "vosk/model") }
    )

    private fun assetPathExists(context: Context, path: String): Boolean =
        try {
            context.assets.list(path) != null
        } catch (_: Exception) {
            false
        }

    private fun copyAssetTree(context: Context, assetPath: String, targetDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        targetDir.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(targetDir, child))
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyDirectory(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
