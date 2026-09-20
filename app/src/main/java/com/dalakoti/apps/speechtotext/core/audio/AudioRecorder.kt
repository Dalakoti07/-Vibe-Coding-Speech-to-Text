package com.dalakoti.apps.speechtotext.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min

/**
 * The model fixes the format: 16 kHz, mono, and nothing else. A different sample rate
 * does not error, it just quietly degrades accuracy — so the constant lives here and
 * nowhere else.
 *
 * "Highest quality" within that constraint is about the *source*, not the rate:
 * VOICE_RECOGNITION is the one tuned for ASR (no aggressive AGC or noise suppression
 * mangling the signal), and UNPROCESSED is rawer still where the device supports it.
 */
object AudioFormatSpec {
    const val SAMPLE_RATE = 16_000
    const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
}

enum class CaptureSource(val label: String, val androidSource: Int, val detail: String) {
    VoiceRecognition(
        "Voice recognition",
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "Tuned for speech recognition. Recommended.",
    ),
    Unprocessed(
        "Unprocessed",
        MediaRecorder.AudioSource.UNPROCESSED,
        "Raw microphone, no device processing. Quieter; not on every device.",
    ),
    Mic(
        "Microphone",
        MediaRecorder.AudioSource.MIC,
        "Default source. May apply gain control that hurts accuracy.",
    );

    companion object {
        fun fromName(name: String?): CaptureSource =
            entries.firstOrNull { it.name == name } ?: VoiceRecognition
    }
}

class AudioCaptureException(message: String) : Exception(message)

/**
 * Buffers a whole utterance in RAM. 16 kHz mono float is 64 KB/s, so the 120 s cap is
 * 7.7 MB — not worth streaming to disk for.
 */
class AudioRecorder {

    private val tag = "AudioRecorder"

    /**
     * Stopping must not be cancellation: cancelling the coroutine throws away the audio
     * we just spent the whole utterance collecting. The read loop watches this flag and
     * returns the buffer normally.
     */
    private val stopRequested = AtomicBoolean(false)

    fun requestStop() {
        stopRequested.set(true)
    }

    private val _level = MutableStateFlow(0f)
    /** 0..1 amplitude, for the waveform on screen. */
    val level: StateFlow<Float> = _level.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun record(
        source: CaptureSource,
        maxMillis: Long,
        onElapsed: (Long) -> Unit,
    ): FloatArray = withContext(Dispatchers.IO) {
        stopRequested.set(false)
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioFormatSpec.SAMPLE_RATE,
            AudioFormatSpec.CHANNEL,
            AudioFormatSpec.ENCODING,
        )
        if (minBuffer <= 0) throw AudioCaptureException("This device rejected 16 kHz mono capture")

        // Four times the minimum: enough slack that a scheduling hiccup does not drop audio.
        val bufferBytes = maxOf(minBuffer * 4, AudioFormatSpec.SAMPLE_RATE * 2 / 2)

        val record = AudioRecord(
            source.androidSource,
            AudioFormatSpec.SAMPLE_RATE,
            AudioFormatSpec.CHANNEL,
            AudioFormatSpec.ENCODING,
            bufferBytes,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioCaptureException("Could not open the ${source.label} source")
        }
        if (record.sampleRate != AudioFormatSpec.SAMPLE_RATE) {
            val actual = record.sampleRate
            record.release()
            throw AudioCaptureException("Device gave $actual Hz, not 16 kHz")
        }

        val maxSamples = (AudioFormatSpec.SAMPLE_RATE * maxMillis / 1000).toInt()
        val collected = ArrayList<FloatArray>()
        var total = 0
        val chunk = ShortArray(bufferBytes / 2)
        val startedAt = System.currentTimeMillis()

        try {
            record.startRecording()
            Log.i(tag, "capture started: source=${source.label}")
            while (total < maxSamples && !stopRequested.get()) {
                coroutineContext.ensureActive()
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        throw AudioCaptureException("Microphone read failed ($read)")
                    }
                    continue
                }
                val take = min(read, maxSamples - total)
                val floats = FloatArray(take)
                var peak = 0
                for (i in 0 until take) {
                    val s = chunk[i].toInt()
                    if (abs(s) > peak) peak = abs(s)
                    floats[i] = s / 32768f
                }
                collected += floats
                total += take
                _level.value = (peak / 32768f).coerceIn(0f, 1f)
                onElapsed(System.currentTimeMillis() - startedAt)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            _level.value = 0f
        }

        Log.i(
            tag,
            "capture finished: $total samples = " +
                "%.2fs (stopRequested=${stopRequested.get()})".format(
                    total / AudioFormatSpec.SAMPLE_RATE.toFloat()
                ),
        )

        val out = FloatArray(total)
        var offset = 0
        for (part in collected) {
            part.copyInto(out, offset)
            offset += part.size
        }
        out
    }
}
