package com.libreflow.engine

import android.content.Context
import android.media.MediaPlayer
import com.libreflow.R
import com.libreflow.data.local.LibreFlowDatabase
import com.libreflow.data.local.entity.ModelDownloadState
import com.libreflow.data.local.entity.TranscriptionEntity
import com.libreflow.data.repository.PreferenceManager
import com.libreflow.engine.asr.SherpaAsrEngine
import com.libreflow.engine.audio.AudioCaptureManager
import com.libreflow.engine.slm.FastPathEngine
import com.libreflow.engine.slm.LlamaSlmEngine
import com.libreflow.engine.vad.SileroVadEngine
import com.libreflow.service.FlowAccessibilityService
import com.libreflow.service.LibreFlowImeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

sealed interface PipelineState {
    data object Idle : PipelineState
    data class Listening(val livePartialText: String = "") : PipelineState
    data object Processing : PipelineState
    data class Done(val text: String, val latencyMs: Long) : PipelineState
    data class Error(val message: String) : PipelineState
}

/**
 * Coordinates end-to-end local dictation:
 * AudioCapture (16kHz) -> Silero VAD -> Sherpa-ONNX ASR -> FastPath / Llama.cpp SLM -> Injection + History.
 */
class PipelineCoordinator(
    private val context: Context,
    private val database: LibreFlowDatabase,
    private val preferences: PreferenceManager,
    private val audioCapture: AudioCaptureManager,
    private val vadEngine: SileroVadEngine,
    private val asrEngine: SherpaAsrEngine,
    private val slmEngine: LlamaSlmEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    val rmsProvider: () -> Float
        get() = audioCapture.rmsProvider

    private var captureJob: Job? = null
    private val accumulatedSegments = mutableListOf<String>()
    private val fullAudioBuffer = mutableListOf<FloatArray>()
    private var recordingStartMs = 0L

    fun toggleDictation(onMicStateChanged: (Boolean) -> Unit = {}) {
        when (_state.value) {
            is PipelineState.Listening -> stopAndFinalize(onMicStateChanged)
            is PipelineState.Idle, is PipelineState.Done, is PipelineState.Error -> startDictation(onMicStateChanged)
            is PipelineState.Processing -> Unit
        }
    }

    fun startDictation(onMicStateChanged: (Boolean) -> Unit = {}) {
        if (_state.value is PipelineState.Listening || _state.value is PipelineState.Processing) return

        scope.launch {
            if (!asrEngine.isLoaded) {
                val loaded = ensureModelsLoaded()
                if (!loaded) {
                    _state.value = PipelineState.Error("Download & load an ASR model in Models tab first")
                    return@launch
                }
            }

            // Capture target focus node BEFORE starting overlay/mic transitions
            FlowAccessibilityService.instance?.captureFocusedNode()

            accumulatedSegments.clear()
            fullAudioBuffer.clear()
            vadEngine.reset()
            recordingStartMs = System.currentTimeMillis()
            _state.value = PipelineState.Listening()
            onMicStateChanged(true)

            // Pre-warm SLM in background while user is speaking so stop-to-insert latency stays low
            scope.launch(Dispatchers.IO) {
                if (!slmEngine.isLoaded) {
                    val styleId = preferences.activeStyleId.first()
                    val style = database.styleDao().getById(styleId)
                    if (style?.usesSlm == true) {
                        ensureSlmLoaded()
                    }
                }
            }

            if (preferences.soundCuesEnabled.first()) {
                playRawCue(R.raw.dictation_start)
            }

            captureJob = scope.launch {
                try {
                    audioCapture.start().collect { frame ->
                        fullAudioBuffer.add(frame)
                        if (vadEngine.isLoaded) {
                            vadEngine.acceptWaveform(frame)
                            val completed = vadEngine.popCompletedSegments()
                            if (completed.isNotEmpty()) {
                                for (seg in completed) {
                                    val text = asrEngine.transcribe(seg)
                                    if (text.isNotBlank()) {
                                        accumulatedSegments.add(text)
                                        _state.value = PipelineState.Listening(accumulatedSegments.joinToString(" "))
                                    }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    _state.value = PipelineState.Error(t.message ?: "Microphone capture error")
                    onMicStateChanged(false)
                }
            }
        }
    }

    fun stopAndFinalize(onMicStateChanged: (Boolean) -> Unit = {}) {
        if (_state.value !is PipelineState.Listening) return

        scope.launch {
            val stopRequestedMs = System.currentTimeMillis()
            _state.value = PipelineState.Processing
            audioCapture.stop()
            captureJob?.join()
            captureJob = null
            onMicStateChanged(false)

            if (preferences.soundCuesEnabled.first()) {
                playRawCue(R.raw.dictation_stop)
            }

            try {
                // Flush remaining VAD tail or transcribe full buffer if VAD wasn't loaded
                if (vadEngine.isLoaded) {
                    val tailSegments = vadEngine.flushAndPopAll()
                    for (seg in tailSegments) {
                        val text = asrEngine.transcribe(seg)
                        if (text.isNotBlank()) accumulatedSegments.add(text)
                    }
                } else if (fullAudioBuffer.isNotEmpty()) {
                    val totalLen = fullAudioBuffer.sumOf { it.size }
                    val merged = FloatArray(totalLen)
                    var offset = 0
                    for (chunk in fullAudioBuffer) {
                        chunk.copyInto(merged, offset)
                        offset += chunk.size
                    }
                    val text = asrEngine.transcribe(merged)
                    if (text.isNotBlank()) accumulatedSegments.add(text)
                }

                val rawText = accumulatedSegments.joinToString(" ").trim()
                if (rawText.isBlank()) {
                    _state.value = PipelineState.Idle
                    return@launch
                }

                val dictionary = database.dictionaryDao().getAllSnapshot()
                val styleId = preferences.activeStyleId.first()
                val style = database.styleDao().getById(styleId)
                    ?: LibreFlowDatabase.defaultStyles().first()
                val fastPathEnabled = preferences.fastPathEnabled.first()
                val targetPkg = FlowAccessibilityService.instance?.activeTargetPackage

                if (style.usesSlm && !FastPathEngine.shouldBypassSlm(rawText, fastPathEnabled) && !slmEngine.isLoaded) {
                    ensureSlmLoaded()
                }

                val polished = if (FastPathEngine.shouldBypassSlm(rawText, fastPathEnabled) || !style.usesSlm || !slmEngine.isLoaded) {
                    FastPathEngine.applyDeterministicCleanup(
                        rawText = rawText,
                        dictionary = dictionary,
                        formatLists = style.id == "professional_bullets"
                    )
                } else {
                    val preCleaned = FastPathEngine.applyDeterministicCleanup(rawText, dictionary, formatLists = false)
                    val slmText = slmEngine.rewrite(preCleaned, style, dictionary, targetPkg)
                    FastPathEngine.applyDeterministicCleanup(slmText, dictionary, formatLists = style.id == "professional_bullets")
                }

                val now = System.currentTimeMillis()
                val latencyMs = (now - stopRequestedMs).coerceAtLeast(1L)
                val durationMs = (stopRequestedMs - recordingStartMs).coerceAtLeast(1L)
                val wordCount = polished.split(Regex("""\s+""")).count { it.isNotBlank() }

                // Inject into target app (IME first if active, else Accessibility Service)
                val ime = LibreFlowImeService.instance
                if (ime != null && ime.isInputViewShown) {
                    ime.commitDictatedText(polished)
                } else {
                    FlowAccessibilityService.instance?.insertText(polished)
                }

                database.transcriptionDao().insert(
                    TranscriptionEntity(
                        rawText = rawText,
                        polishedText = polished,
                        styleName = style.name,
                        wordCount = wordCount,
                        durationMs = durationMs,
                        latencyMs = latencyMs,
                        targetPackage = targetPkg
                    )
                )

                _state.value = PipelineState.Done(polished, latencyMs)
                scope.launch {
                    kotlinx.coroutines.delay(1100L)
                    if (_state.value is PipelineState.Done) {
                        _state.value = PipelineState.Idle
                    }
                }
            } catch (t: Throwable) {
                _state.value = PipelineState.Error(t.message ?: "Transcription failed")
                scope.launch {
                    kotlinx.coroutines.delay(2500L)
                    if (_state.value is PipelineState.Error) {
                        _state.value = PipelineState.Idle
                    }
                }
            }
        }
    }

    fun resetToIdle() {
        _state.value = PipelineState.Idle
    }

    private suspend fun ensureModelsLoaded(): Boolean {
        val vadModel = database.modelDao().getById("silero-vad-v5")
        if (vadModel != null && vadModel.localDirPath.isNotBlank() && !vadEngine.isLoaded) {
            vadEngine.load(File(vadModel.localDirPath))
        }

        val asrId = preferences.activeAsrModelId.first()
        val asrModel = database.modelDao().getById(asrId)
        if (asrModel != null && asrModel.localDirPath.isNotBlank() && File(asrModel.localDirPath).exists()) {
            val res = asrEngine.load(asrModel, File(asrModel.localDirPath))
            if (res.isSuccess) {
                database.modelDao().updateState(asrModel.id, ModelDownloadState.LOADED.name, 100, asrModel.localDirPath, null)
                return true
            }
        }
        return false
    }

    private suspend fun ensureSlmLoaded(): Boolean {
        val slmId = preferences.activeSlmModelId.first()
        val slmModel = database.modelDao().getById(slmId) ?: return false
        if (slmModel.localDirPath.isNotBlank() && File(slmModel.localDirPath).exists()) {
            val res = slmEngine.load(slmModel, File(slmModel.localDirPath))
            if (res.isSuccess) {
                database.modelDao().updateState(slmModel.id, ModelDownloadState.LOADED.name, 100, slmModel.localDirPath, null)
                return true
            }
        }
        return false
    }

    private fun playRawCue(resId: Int) {
        runCatching {
            val mp = MediaPlayer.create(context, resId) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.start()
        }
    }
}
