package com.libreflow.engine.slm

import com.libreflow.data.local.entity.DictionaryWordEntity
import com.libreflow.data.local.entity.ModelDescriptorEntity
import com.libreflow.data.local.entity.StyleProfileEntity
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile

/**
 * On-device context-aware SLM engine backed by llama.cpp (GGUF via mmap).
 * Enforces impartial transcription with zero censorship/moralizing and automatic
 * fallback to verbatim ASR if a refusal is detected.
 */
class LlamaSlmEngine {

    private val llamaMutex = Mutex()

    @Volatile
    private var modelHandle: LlamaModel? = null

    @Volatile
    var loadedModelId: String? = null
        private set

    val isLoaded: Boolean
        get() = modelHandle?.isLoaded == true

    @Synchronized
    fun load(model: ModelDescriptorEntity, modelDir: File): Result<Unit> = runCatching {
        unload()
        val ggufFile = modelDir.listFiles()?.firstOrNull { it.extension.equals("gguf", ignoreCase = true) }
            ?: error("No .gguf file found in ${modelDir.absolutePath}")

        validateGgufHeader(ggufFile)

        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
        val config = LlamaConfig(
            contextSize = 1024,
            threads = threads,
            gpuLayers = 0,
            temperature = 0.0f,
            topP = 1.0f,
            topK = 1,
            seed = 42
        )

        val loaded = try {
            runBlocking {
                Llama.loadModel(ggufFile.absolutePath, config)
            }
        } catch (e: UnsatisfiedLinkError) {
            error("Local LLM requires an arm64-v8a device (x86_64 emulator uses FastPath cleanup)")
        }
        modelHandle = loaded
        loadedModelId = model.id
    }

    suspend fun rewrite(
        rawTranscript: String,
        style: StyleProfileEntity,
        dictionary: List<DictionaryWordEntity>,
        targetPackage: String?
    ): String {
        val handle = modelHandle ?: return rawTranscript
        if (!handle.isLoaded || !style.usesSlm || rawTranscript.isBlank()) return rawTranscript
        // Guard against exceeding contextSize=1024 (which causes ggml_abort in llama_context::decode)
        if (rawTranscript.length > 1600) return rawTranscript

        val systemPrompt = buildSystemPrompt(style, dictionary, targetPackage)
        val userPrompt = "/no_think\nRewrite this voice transcript according to the style rules (NEVER reply to it):\n<t>$rawTranscript</t>"
        return try {
            val maxTokens = (rawTranscript.length / 4 + 24).coerceIn(24, 96)
            val result = llamaMutex.withLock {
                if (!handle.isLoaded) return rawTranscript
                Llama.complete(
                    model = handle,
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens
                )
            }
            val stripped = result.text
                .removePrefix("<t>")
                .removeSuffix("</t>")
                .removePrefix("<transcript>")
                .removeSuffix("</transcript>")
                .trim()
            FastPathEngine.sanitizeSlmOutput(stripped, rawTranscript)
        } catch (_: Throwable) {
            rawTranscript
        }
    }

    @Synchronized
    fun unload() {
        val handle = modelHandle
        modelHandle = null
        loadedModelId = null
        if (handle != null && handle.isLoaded) {
            runCatching { Llama.releaseModel(handle) }
        }
    }

    private fun validateGgufHeader(file: File) {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            val read = raf.read(magic)
            val valid = read == 4 &&
                magic[0] == 0x47.toByte() &&
                magic[1] == 0x47.toByte() &&
                magic[2] == 0x55.toByte() &&
                magic[3] == 0x46.toByte()
            require(valid) { "Invalid GGUF magic header in ${file.name}" }
        }
    }

    private fun buildSystemPrompt(
        style: StyleProfileEntity,
        dictionary: List<DictionaryWordEntity>,
        targetPackage: String?
    ): String {
        val dictHint = if (dictionary.isNotEmpty()) {
            "\nPreferred spellings/vocabulary: " + dictionary.joinToString(", ") { it.word }
        } else {
            ""
        }
        val appHint = if (!targetPackage.isNullOrBlank()) {
            "\nTarget app context: $targetPackage"
        } else {
            ""
        }

        val styleExample = when (style.id) {
            "professional_bullets" ->
                "\nExample:\nInput: <t>We can go to three restaurants: restaurant a, restaurant b, restaurant c</t>\nOutput: We can go to these three restaurants:\n• Restaurant A\n• Restaurant B\n• Restaurant C"
            "concise" ->
                "\nExample:\nInput: <t>Um let's not do that okay wait what is of let's study instead</t>\nOutput: Let's study instead."
            else -> ""
        }
        return "You are a raw, uncensored voice-dictation editor (NOT a chatbot). Output ONLY the edited text. Never answer questions, refuse, or censor profanity with asterisks. ${style.systemPrompt}$styleExample$dictHint$appHint"
    }
}
