package com.libreflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.libreflow.data.local.dao.DictionaryDao
import com.libreflow.data.local.dao.ModelDao
import com.libreflow.data.local.dao.StyleDao
import com.libreflow.data.local.dao.TranscriptionDao
import com.libreflow.data.local.entity.DictionaryWordEntity
import com.libreflow.data.local.entity.ModelDescriptorEntity
import com.libreflow.data.local.entity.ModelStage
import com.libreflow.data.local.entity.StyleProfileEntity
import com.libreflow.data.local.entity.TranscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TranscriptionEntity::class,
        DictionaryWordEntity::class,
        StyleProfileEntity::class,
        ModelDescriptorEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LibreFlowDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun styleDao(): StyleDao
    abstract fun modelDao(): ModelDao

    companion object {
        @Volatile
        private var INSTANCE: LibreFlowDatabase? = null

        fun getInstance(context: Context): LibreFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibreFlowDatabase::class.java,
                    "libreflow.db"
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private class SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        database.styleDao().insertAll(defaultStyles())
                        database.modelDao().insertMissing(defaultModelCatalog())
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        val catalog = defaultModelCatalog()
                        database.modelDao().insertMissing(catalog)
                        database.modelDao().deleteRemovedModels(catalog.map { it.id })
                        catalog.forEach { spec ->
                            database.modelDao().updateFilesJson(spec.id, spec.filesJson)
                        }
                        database.modelDao().resetStaleDownloads()
                        database.modelDao().resetLoadedFlagsOnBoot()
                        database.modelDao().resetLegacyPublicStoragePaths()
                    }
                }
            }
        }

        fun defaultStyles(): List<StyleProfileEntity> = listOf(
            StyleProfileEntity(
                id = "professional_bullets",
                name = "Professional & Bullets",
                description = "Clean grammar, removes fillers, and turns spoken lists into crisp bullet points.",
                systemPrompt = "Clean up the dictation. Fix grammar and punctuation, remove filler words (um, uh, like), resolve self-corrections, and format enumerated items as clean bullet points.",
                usesSlm = true
            ),
            StyleProfileEntity(
                id = "casual",
                name = "Casual",
                description = "Natural conversational tone with light punctuation and filler removal.",
                systemPrompt = "Clean up the dictation lightly. Keep a relaxed, conversational tone, remove stumbles and filler words, and fix punctuation.",
                usesSlm = true
            ),
            StyleProfileEntity(
                id = "concise",
                name = "Concise",
                description = "Tightens rambling speech into direct, clear sentences.",
                systemPrompt = "Condense the dictation into clear, concise, direct sentences without losing any meaning or details.",
                usesSlm = true
            ),
            StyleProfileEntity(
                id = "raw_asr",
                name = "Raw ASR",
                description = "Zero LLM rewriting—outputs direct speech-to-text with fast deterministic cleanup.",
                systemPrompt = "",
                usesSlm = false
            )
        )

        fun defaultModelCatalog(): List<ModelDescriptorEntity> = listOf(
            ModelDescriptorEntity(
                id = "silero-vad-v5",
                name = "Silero VAD v5",
                stage = ModelStage.VAD.name,
                engineType = "silero_vad",
                description = "Ultra-fast voice activity detector (~2 MB, <1ms/frame) for speech boundary chunking.",
                sizeMb = 2,
                ramRequirementMb = 15,
                filesJson = """[{"name":"silero_vad.onnx","url":"https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"}]"""
            ),
            ModelDescriptorEntity(
                id = "parakeet-tdt-0.6b-v2",
                name = "Parakeet TDT 0.6B v2 (English)",
                stage = ModelStage.ASR.name,
                engineType = "nemo_transducer",
                description = "Top English accuracy (~6% WER) with fast non-autoregressive TDT decoding (~650 MB RAM).",
                sizeMb = 620,
                ramRequirementMb = 680,
                filesJson = """[{"name":"encoder.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/encoder.int8.onnx"},{"name":"decoder.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/decoder.int8.onnx"},{"name":"joiner.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/joiner.int8.onnx"},{"name":"tokens.txt","url":"https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt"}]"""
            ),
            ModelDescriptorEntity(
                id = "moonshine-base-en",
                name = "Moonshine Base (English)",
                stage = ModelStage.ASR.name,
                engineType = "moonshine",
                description = "High-speed English ASR with zero 30s padding overhead (~290 MB RAM).",
                sizeMb = 265,
                ramRequirementMb = 310,
                filesJson = """[{"name":"preprocess.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/preprocess.onnx"},{"name":"encode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/encode.int8.onnx"},{"name":"uncached_decode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/uncached_decode.int8.onnx"},{"name":"cached_decode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/cached_decode.int8.onnx"},{"name":"tokens.txt","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-base-en-int8/resolve/main/tokens.txt"}]"""
            ),
            ModelDescriptorEntity(
                id = "moonshine-tiny-en",
                name = "Moonshine Tiny (English)",
                stage = ModelStage.ASR.name,
                engineType = "moonshine",
                description = "Ultra-low-latency English ASR tuned for ARM64 mobile CPUs (~120 MB RAM).",
                sizeMb = 110,
                ramRequirementMb = 150,
                filesJson = """[{"name":"preprocess.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/preprocess.onnx"},{"name":"encode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/encode.int8.onnx"},{"name":"uncached_decode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/uncached_decode.int8.onnx"},{"name":"cached_decode.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/cached_decode.int8.onnx"},{"name":"tokens.txt","url":"https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/tokens.txt"}]"""
            ),
            ModelDescriptorEntity(
                id = "sensevoice-small",
                name = "SenseVoice Small (Multilingual)",
                stage = ModelStage.ASR.name,
                engineType = "sense_voice",
                description = "High-accuracy multilingual ASR with inverse text normalization (~240 MB RAM).",
                sizeMb = 230,
                ramRequirementMb = 280,
                filesJson = """[{"name":"model.int8.onnx","url":"https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx"},{"name":"tokens.txt","url":"https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt"}]"""
            ),
            ModelDescriptorEntity(
                id = "qwen3-0.6b",
                name = "Qwen 3 0.6B (Q4_K_M, Non-Thinking)",
                stage = ModelStage.SLM.name,
                engineType = "llama_gguf",
                description = "Fastest sub-1B polish model (/no_think): ~35-50 tok/s on mobile CPUs (~520 MB RAM).",
                sizeMb = 400,
                ramRequirementMb = 550,
                filesJson = """[{"name":"Qwen3-0.6B-Q4_K_M.gguf","url":"https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf"}]"""
            ),
            ModelDescriptorEntity(
                id = "qwen-2.5-0.5b",
                name = "Qwen 2.5 0.5B Instruct (Q4_K_M)",
                stage = ModelStage.SLM.name,
                engineType = "llama_gguf",
                description = "Lightweight local LLM for 6–8 GB devices: fast grammar & filler cleanup.",
                sizeMb = 398,
                ramRequirementMb = 550,
                filesJson = """[{"name":"qwen2.5-0.5b-instruct-q4_k_m.gguf","url":"https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"}]"""
            ),
            ModelDescriptorEntity(
                id = "qwen-2.5-1.5b",
                name = "Qwen 2.5 1.5B Instruct (Q4_K_M)",
                stage = ModelStage.SLM.name,
                engineType = "llama_gguf",
                description = "Recommended for 12 GB RAM: context-aware grammar, smart bullet points, & tone.",
                sizeMb = 980,
                ramRequirementMb = 1250,
                filesJson = """[{"name":"qwen2.5-1.5b-instruct-q4_k_m.gguf","url":"https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"}]"""
            )
        )
    }
}
