package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.VoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    val defaultVoices = listOf(
        VoiceOption("en_us_female", "Luna AI", Locale.US, "Female", "Natural American English"),
        VoiceOption("en_us_male", "Aria AI", Locale.US, "Male", "Deep American English"),
        VoiceOption("en_gb_female", "Victoria AI", Locale.UK, "Female", "British English"),
        VoiceOption("en_gb_male", "Arthur AI", Locale.UK, "Male", "Classic British"),
        VoiceOption("es_es_female", "Sofia AI", Locale("es", "ES"), "Female", "Spanish (Spain)"),
        VoiceOption("fr_fr_female", "Chloe AI", Locale.FRANCE, "Female", "French"),
        VoiceOption("de_de_female", "Hannah AI", Locale.GERMANY, "Female", "German"),
        VoiceOption("ja_jp_female", "Sakura AI", Locale.JAPAN, "Female", "Japanese")
    )

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.US
            Log.d("TtsManager", "TextToSpeech initialized successfully")
        } else {
            isInitialized = false
            Log.e("TtsManager", "TextToSpeech initialization failed with status $status")
        }
    }

    /**
     * Synthesizes text to an audio file.
     * Tries native TTS `synthesizeToFile` first; if TTS engine is unavailable or fails,
     * falls back to online TTS endpoint to guarantee audio generation!
     */
    suspend fun generateAudioFile(
        text: String,
        voice: VoiceOption,
        pitch: Float,
        speed: Float
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val audioDir = File(context.cacheDir, "generated_tts")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            val fileName = "tts_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.wav"
            val outputFile = File(audioDir, fileName)

            var success = false

            if (isInitialized && tts != null) {
                tts?.let { engine ->
                    engine.language = voice.locale
                    engine.setPitch(pitch)
                    engine.setSpeechRate(speed)

                    val utteranceId = "utt_${System.currentTimeMillis()}"
                    val params = Bundle()

                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        engine.synthesizeToFile(text, params, outputFile, utteranceId)
                    } else {
                        @Suppress("DEPRECATION")
                        val map = HashMap<String, String>()
                        map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                        @Suppress("DEPRECATION")
                        engine.synthesizeToFile(text, map, outputFile.absolutePath)
                    }

                    if (result == TextToSpeech.SUCCESS) {
                        // Wait briefly for file to be written completely
                        var checkCount = 0
                        while ((!outputFile.exists() || outputFile.length() == 0L) && checkCount < 30) {
                            kotlinx.coroutines.delay(100)
                            checkCount++
                        }
                        if (outputFile.exists() && outputFile.length() > 0L) {
                            success = true
                        }
                    }
                }
            }

            // Fallback: If local TTS failed or produced empty file, use online TTS endpoint
            if (!success || !outputFile.exists() || outputFile.length() == 0L) {
                Log.d("TtsManager", "Local TTS synthesize failed/unsupported; using online TTS fallback")
                val fallbackSuccess = downloadOnlineTtsAudio(text, voice.locale.language, outputFile)
                if (fallbackSuccess && outputFile.exists() && outputFile.length() > 0L) {
                    success = true
                }
            }

            if (success) {
                Result.success(outputFile)
            } else {
                Result.failure(Exception("Failed to generate audio file with current settings."))
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error generating audio", e)
            Result.failure(e)
        }
    }

    private fun downloadOnlineTtsAudio(text: String, lang: String, targetFile: File): Boolean {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val languageCode = if (lang.isNotBlank()) lang else "en"
            val urlString = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=$languageCode&client=tw-ob"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Online TTS download error", e)
            false
        }
    }

    /**
     * Gets audio file duration in milliseconds.
     */
    fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 3000L
        } catch (e: Exception) {
            3000L
        }
    }

    /**
     * Prepares and starts playback of an audio file using MediaPlayer.
     */
    fun startPlayback(
        file: File,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    onCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    onError("Playback error occurred")
                    true
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "MediaPlayer error", e)
            onError(e.localizedMessage ?: "Playback initialization failed")
        }
    }

    fun pausePlayback() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Pause error", e)
        }
    }

    fun resumePlayback() {
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Resume error", e)
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("TtsManager", "Stop error", e)
            mediaPlayer = null
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
        } catch (e: Exception) {
            Log.e("TtsManager", "Seek error", e)
        }
    }

    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downloads/Exports the synthesized audio file to public Download storage or MediaStore.
     */
    suspend fun saveAudioToDownloads(sourceFile: File, title: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
            val fileName = "VoiceAI_${sanitizedTitle}_${System.currentTimeMillis()}.mp3"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AI_TextToSpeech")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create download entry"))

                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Result.success("Saved to Downloads/AI_TextToSpeech/$fileName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "AI_TextToSpeech")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val destFile = File(targetDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                Result.success("Saved to ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to download/save file", e)
            Result.failure(e)
        }
    }

    /**
     * Shares audio file via Android Share Intent.
     */
    fun shareAudioFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share AI Voice Audio")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("TtsManager", "Error sharing file", e)
        }
    }

    fun shutdown() {
        stopPlayback()
        tts?.stop()
        tts?.shutdown()
    }
}
