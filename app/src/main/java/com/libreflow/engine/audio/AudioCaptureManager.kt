package com.libreflow.engine.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Low-overhead 16 kHz 16-bit mono PCM capture manager.
 * Exposes a lock-free [rmsProvider] lambda so Compose DrawScope can animate at 120Hz
 * without triggering recomposition.
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_SAMPLES = 512 // 32 ms @ 16 kHz (matches Silero VAD v5)
    }

    private val isRecording = AtomicBoolean(false)
    private val currentRms = AtomicReference(0f)
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var audioChannel: Channel<FloatArray>? = null
    private var deviceCallback: AudioDeviceCallback? = null

    /** Lock-free RMS reader for 60/120Hz Canvas DrawScope. */
    val rmsProvider: () -> Float = { currentRms.get() }

    @SuppressLint("MissingPermission")
    fun start(): Flow<FloatArray> {
        stop()
        val channel = Channel<FloatArray>(capacity = 64)
        audioChannel = channel

        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufBytes * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord failed to initialize")
        }

        audioRecord = recorder
        registerDeviceCallback()
        isRecording.set(true)
        recorder.startRecording()

        captureThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val shortBuf = ShortArray(FRAME_SAMPLES)
            var smoothedRms = 0f

            while (isRecording.get()) {
                val read = recorder.read(shortBuf, 0, FRAME_SAMPLES)
                if (read > 0) {
                    val floatBuf = FloatArray(read)
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val f = shortBuf[i] / 32768.0f
                        floatBuf[i] = f
                        sumSq += (f * f)
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    // Smooth envelope for buttery 120Hz waveform motion
                    val target = (rms * 5.5f).coerceIn(0f, 1f)
                    smoothedRms = smoothedRms * 0.65f + target * 0.35f
                    currentRms.set(smoothedRms)
                    channel.trySend(floatBuf)
                }
            }
            currentRms.set(0f)
            channel.close()
        }, "LibreFlow-AudioCapture").apply { start() }

        return channel.receiveAsFlow()
    }

    fun stop() {
        isRecording.set(false)
        unregisterDeviceCallback()
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            captureThread?.join(350)
        } catch (_: InterruptedException) {
        }
        captureThread = null
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        audioChannel?.close()
        audioChannel = null
        currentRms.set(0f)
    }

    private fun registerDeviceCallback() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                // AudioRecord automatically routes to active SCO/LE mic when VOICE_RECOGNITION is active
            }
        }
        deviceCallback = cb
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
    }

    private fun unregisterDeviceCallback() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        deviceCallback?.let { am.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }
}
