package com.libreflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val polishedText: String,
    val styleName: String,
    val wordCount: Int,
    val durationMs: Long,
    val latencyMs: Long,
    val targetPackage: String?,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_words")
data class DictionaryWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val phoneticHint: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "style_profiles")
data class StyleProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val usesSlm: Boolean,
    val isBuiltIn: Boolean = true
)

enum class ModelStage {
    VAD,
    ASR,
    SLM
}

enum class ModelDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADED,
    ERROR
}

@Entity(tableName = "models")
data class ModelDescriptorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stage: String,
    val engineType: String,
    val description: String,
    val sizeMb: Int,
    val ramRequirementMb: Int,
    val filesJson: String,
    val downloadState: String = ModelDownloadState.NOT_DOWNLOADED.name,
    val downloadProgress: Int = 0,
    val localDirPath: String = "",
    val errorMessage: String? = null
)
