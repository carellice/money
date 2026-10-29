package com.money.app.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Records 16 kHz mono PCM, the format Whisper works on, and wraps it in a WAV container. */
class VoiceRecorder {
    private val _level = MutableStateFlow(0f)
    /** Loudness of the last chunk, 0..1, to animate the recording blob. */
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile private var stopRequested = false

    fun stop() {
        stopRequested = true
    }

    /** Suspends until [stop] is called, the coroutine is cancelled or [maxSeconds] pass. Needs RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSeconds: Int = 120): ByteArray = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw AiException("Microfono non disponibile")
        }
        val pcm = ByteArrayOutputStream()
        val buffer = ShortArray(minBuffer)
        val bytes = ByteBuffer.allocate(minBuffer * 2).order(ByteOrder.LITTLE_ENDIAN)
        val maxBytes = RATE * 2 * maxSeconds
        try {
            recorder.startRecording()
            while (!stopRequested && coroutineContext.isActive && pcm.size() < maxBytes) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                bytes.clear()
                var peak = 0
                for (i in 0 until n) {
                    bytes.putShort(buffer[i])
                    peak = maxOf(peak, abs(buffer[i].toInt()))
                }
                pcm.write(bytes.array(), 0, n * 2)
                _level.value = (peak / 32768f).coerceIn(0f, 1f)
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            _level.value = 0f
        }
        wav(pcm.toByteArray())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    companion object {
        const val RATE = 16_000

        /** Seconds of audio in a WAV produced by [record]. */
        fun seconds(wav: ByteArray): Int = ((wav.size - 44) / (RATE * 2)).coerceAtLeast(0)
    }
}
