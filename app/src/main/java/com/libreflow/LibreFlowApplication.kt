package com.libreflow

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import com.libreflow.data.local.LibreFlowDatabase
import com.libreflow.data.repository.HuggingFaceModelDownloader
import com.libreflow.data.repository.PreferenceManager
import com.libreflow.engine.PipelineCoordinator
import com.libreflow.engine.asr.SherpaAsrEngine
import com.libreflow.engine.audio.AudioCaptureManager
import com.libreflow.engine.slm.LlamaSlmEngine
import com.libreflow.engine.vad.SileroVadEngine

enum class DeviceRamTier(val label: String, val recommendedAsrId: String, val recommendedSlmId: String) {
    LOW_6GB("≤ 6 GB RAM", "moonshine-tiny-en", "qwen3-0.6b"),
    MID_8GB("8 GB RAM", "moonshine-base-en", "qwen3-0.6b"),
    HIGH_12GB("12 GB+ RAM", "parakeet-tdt-0.6b-v2", "qwen-2.5-1.5b")
}

class LibreFlowApplication : Application() {

    val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    lateinit var database: LibreFlowDatabase
        private set
    lateinit var preferences: PreferenceManager
        private set
    lateinit var downloader: HuggingFaceModelDownloader
        private set
    lateinit var audioCapture: AudioCaptureManager
        private set
    lateinit var vadEngine: SileroVadEngine
        private set
    lateinit var asrEngine: SherpaAsrEngine
        private set
    lateinit var slmEngine: LlamaSlmEngine
        private set
    lateinit var pipeline: PipelineCoordinator
        private set
    lateinit var ramTier: DeviceRamTier
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        ramTier = detectRamTier()

        database = LibreFlowDatabase.getInstance(this)
        preferences = PreferenceManager(this)
        downloader = HuggingFaceModelDownloader(this, database.modelDao())
        audioCapture = AudioCaptureManager(this)
        vadEngine = SileroVadEngine()
        asrEngine = SherpaAsrEngine()
        slmEngine = LlamaSlmEngine()
        pipeline = PipelineCoordinator(
            context = this,
            database = database,
            preferences = preferences,
            audioCapture = audioCapture,
            vadEngine = vadEngine,
            asrEngine = asrEngine,
            slmEngine = slmEngine
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Release heavy SLM first under OS memory pressure while keeping lightweight VAD/ASR intact
            slmEngine.unload()
        }
    }

    private fun detectRamTier(): DeviceRamTier {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        return when {
            totalGb >= 10.5 -> DeviceRamTier.HIGH_12GB
            totalGb >= 7.0 -> DeviceRamTier.MID_8GB
            else -> DeviceRamTier.LOW_6GB
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY,
            "LibreFlow Dictation Bubble",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the local on-device dictation bubble ready"
            setShowBadge(false)
        }
        nm.createNotificationChannel(overlayChannel)
    }

    companion object {
        const val CHANNEL_OVERLAY = "libreflow_overlay_channel"
    }
}
