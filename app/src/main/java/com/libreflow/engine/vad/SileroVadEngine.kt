package com.libreflow.engine.vad

import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Wraps Sherpa-ONNX Silero VAD v5 for low-latency speech boundary detection
 * and utterance segmentation at 16 kHz.
 */
class SileroVadEngine {

    @Volatile
    private var vad: Vad? = null

    val isLoaded: Boolean
        get() = vad != null

    @Synchronized
    fun load(modelDir: File): Result<Unit> = runCatching {
        unload()
        val modelFile = File(modelDir, "silero_vad.onnx")
        require(modelFile.exists() && modelFile.canRead() && modelFile.length() > 1024L) {
            "Missing or unreadable silero_vad.onnx in ${modelDir.absolutePath}"
        }

        Log.d("LibreFlow", "Loading Silero VAD from ${modelFile.absolutePath} (${modelFile.length()} bytes)")
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelFile.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.35f,
                minSpeechDuration = 0.16f,
                windowSize = 512,
                maxSpeechDuration = 25.0f
            ),
            sampleRate = 16000,
            numThreads = 1,
            debug = false
        )
        vad = Vad(assetManager = null, config = config)
        Log.d("LibreFlow", "Silero VAD loaded successfully")
    }

    @Synchronized
    fun acceptWaveform(samples: FloatArray) {
        vad?.acceptWaveform(samples)
    }

    @Synchronized
    fun isSpeechDetected(): Boolean = vad?.isSpeechDetected() ?: false

    @Synchronized
    fun popCompletedSegments(): List<FloatArray> {
        val instance = vad ?: return emptyList()
        val segments = mutableListOf<FloatArray>()
        while (!instance.empty()) {
            val seg = instance.front()
            instance.pop()
            if (seg.samples.isNotEmpty()) {
                segments.add(seg.samples)
            }
        }
        return segments
    }

    @Synchronized
    fun flushAndPopAll(): List<FloatArray> {
        val instance = vad ?: return emptyList()
        instance.flush()
        return popCompletedSegments()
    }

    @Synchronized
    fun reset() {
        vad?.reset()
    }

    @Synchronized
    fun unload() {
        vad?.release()
        vad = null
    }
}
