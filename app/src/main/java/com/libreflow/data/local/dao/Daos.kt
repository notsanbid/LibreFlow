package com.libreflow.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.libreflow.data.local.entity.DictionaryWordEntity
import com.libreflow.data.local.entity.ModelDescriptorEntity
import com.libreflow.data.local.entity.StyleProfileEntity
import com.libreflow.data.local.entity.TranscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<TranscriptionEntity>>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM transcriptions")
    fun observeTotalWords(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transcriptions")
    fun observeTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptionEntity): Long

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_words ORDER BY word COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DictionaryWordEntity>>

    @Query("SELECT * FROM dictionary_words")
    suspend fun getAllSnapshot(): List<DictionaryWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DictionaryWordEntity): Long

    @Delete
    suspend fun delete(entity: DictionaryWordEntity)
}

@Dao
interface StyleDao {
    @Query("SELECT * FROM style_profiles")
    fun observeAll(): Flow<List<StyleProfileEntity>>

    @Query("SELECT * FROM style_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(styles: List<StyleProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(style: StyleProfileEntity)

    @Query("DELETE FROM style_profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    fun observeAll(): Flow<List<ModelDescriptorEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ModelDescriptorEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(models: List<ModelDescriptorEntity>)

    @Query("UPDATE models SET filesJson = :filesJson WHERE id = :id")
    suspend fun updateFilesJson(id: String, filesJson: String)

    @Query("UPDATE models SET downloadState = 'NOT_DOWNLOADED', downloadProgress = 0 WHERE downloadState = 'DOWNLOADING'")
    suspend fun resetStaleDownloads()

    @Query(
        "UPDATE models SET downloadState = :state, downloadProgress = :progress, " +
            "localDirPath = :localDirPath, errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun updateState(
        id: String,
        state: String,
        progress: Int,
        localDirPath: String,
        errorMessage: String?
    )
}
