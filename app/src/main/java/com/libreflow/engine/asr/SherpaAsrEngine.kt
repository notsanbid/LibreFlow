package com.libreflow.engine.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.libreflow.data.local.entity.ModelDescriptorEntity
import java.io.File

/**
 * On-device ASR engine backed by Sherpa-ONNX (supports Moonshine, Whisper, SenseVoice).
 */
class SherpaAsrEngine {

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    var loadedModelId: String? = null
        private set

    val isLoaded: Boolean
        get() = recognizer != null

    @Synchronized
    fun load(model: ModelDescriptorEntity, modelDir: File): Result<Unit> = runCatching {
        unload()
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

        val modelConfig = when (model.engineType) {
            "moonshine" -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    preprocessor = File(modelDir, "preprocess.onnx").absolutePath,
                    encoder = File(modelDir, "encode.int8.onnx").absolutePath,
                    uncachedDecoder = File(modelDir, "uncached_decode.int8.onnx").absolutePath,
                    cachedDecoder = File(modelDir, "cached_decode.int8.onnx").absolutePath
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = threads,
                debug = false,
                modelType = "moonshine"
            )

            "whisper" -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "base.en-encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "base.en-decoder.int8.onnx").absolutePath,
                    language = "en",
                    task = "transcribe"
                ),
                tokens = File(modelDir, "base.en-tokens.txt").absolutePath,
                numThreads = threads,
                debug = false,
                modelType = "whisper"
            )

            "sense_voice" -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                    language = "auto",
                    useInverseTextNormalization = true
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = threads,
                debug = false,
                modelType = "sense_voice"
            )

            else -> error("Unsupported ASR engine type: ${model.engineType}")
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(assetManager = null, config = config)
        loadedModelId = model.id
    }

    @Synchronized
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        val rec = recognizer ?: return ""
        if (samples.isEmpty()) return ""
        val stream = rec.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            rec.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    @Synchronized
    fun unload() {
        recognizer?.release()
        recognizer = null
        loadedModelId = null
    }
}
