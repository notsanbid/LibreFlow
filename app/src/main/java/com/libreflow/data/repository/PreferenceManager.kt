package com.libreflow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "libreflow_prefs")

class PreferenceManager(private val context: Context) {

    private object Keys {
        val BUBBLE_SIZE = floatPreferencesKey("bubble_size")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val ACTIVE_STYLE_ID = stringPreferencesKey("active_style_id")
        val ACTIVE_ASR_MODEL_ID = stringPreferencesKey("active_asr_model_id")
        val ACTIVE_SLM_MODEL_ID = stringPreferencesKey("active_slm_model_id")
        val FAST_PATH_ENABLED = booleanPreferencesKey("fast_path_enabled")
        val SOUND_CUES_ENABLED = booleanPreferencesKey("sound_cues_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    val bubbleSize: Flow<Float> = context.dataStore.data.map { it[Keys.BUBBLE_SIZE] ?: 1.0f }
    val bubbleOpacity: Flow<Float> = context.dataStore.data.map { it[Keys.BUBBLE_OPACITY] ?: 0.92f }
    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.OVERLAY_ENABLED] ?: false }
    val activeStyleId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_STYLE_ID] ?: "professional_bullets" }
    val activeAsrModelId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_ASR_MODEL_ID] ?: "whisper-base-en" }
    val activeSlmModelId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_SLM_MODEL_ID] ?: "qwen-2.5-1.5b" }
    val fastPathEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.FAST_PATH_ENABLED] ?: true }
    val soundCuesEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SOUND_CUES_ENABLED] ?: true }
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: true }

    suspend fun setBubbleSize(value: Float) = context.dataStore.edit { it[Keys.BUBBLE_SIZE] = value.coerceIn(0.7f, 1.4f) }
    suspend fun setBubbleOpacity(value: Float) = context.dataStore.edit { it[Keys.BUBBLE_OPACITY] = value.coerceIn(0.2f, 1.0f) }
    suspend fun setOverlayEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.OVERLAY_ENABLED] = enabled }
    suspend fun setActiveStyleId(id: String) = context.dataStore.edit { it[Keys.ACTIVE_STYLE_ID] = id }
    suspend fun setActiveAsrModelId(id: String) = context.dataStore.edit { it[Keys.ACTIVE_ASR_MODEL_ID] = id }
    suspend fun setActiveSlmModelId(id: String) = context.dataStore.edit { it[Keys.ACTIVE_SLM_MODEL_ID] = id }
    suspend fun setFastPathEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.FAST_PATH_ENABLED] = enabled }
    suspend fun setSoundCuesEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND_CUES_ENABLED] = enabled }
    suspend fun setHapticsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
}
