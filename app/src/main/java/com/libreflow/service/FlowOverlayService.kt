package com.libreflow.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.libreflow.LibreFlowApplication
import com.libreflow.MainActivity
import com.libreflow.R
import com.libreflow.ui.overlay.FloatingBubbleView
import com.libreflow.ui.theme.LibreFlowTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Floating Compose overlay host.
 * Starts under FOREGROUND_SERVICE_TYPE_SPECIAL_USE (Android 14+ safe even without mic permission)
 * and promotes to FOREGROUND_SERVICE_TYPE_MICROPHONE only during active dictation.
 */
class FlowOverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context) {
            val intent = Intent(context, FlowOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlowOverlayService::class.java))
        }
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayContainer: BubbleOverlayContainer
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForegroundWithType(micActive = false)
        setupOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::overlayContainer.isInitialized) {
            runCatching { windowManager.removeView(overlayContainer) }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun setupOverlay() {
        val app = application as LibreFlowApplication
        val pipeline = app.pipeline
        val prefs = app.preferences

        val metrics = resources.displayMetrics
        val bubblePx = (66 * metrics.density).toInt()
        val marginPx = (14 * metrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - bubblePx - marginPx).coerceAtLeast(marginPx)
            y = (metrics.heightPixels * 0.65f).toInt()
        }

        overlayContainer = BubbleOverlayContainer(this).apply {
            setViewTreeLifecycleOwner(this@FlowOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FlowOverlayService)
            composeView.setContent {
                val state by pipeline.state.collectAsState()
                val sizeScale by prefs.bubbleSize.collectAsState(initial = 1.0f)
                val alphaValue by prefs.bubbleOpacity.collectAsState(initial = 0.92f)

                LibreFlowTheme {
                    FloatingBubbleView(
                        state = state,
                        rmsProvider = pipeline.rmsProvider,
                        sizeScale = sizeScale,
                        opacity = alphaValue,
                        onTap = {
                            triggerHaptic()
                            pipeline.toggleDictation { micActive ->
                                startForegroundWithType(micActive)
                            }
                        },
                        onDrag = { dx, dy ->
                            val params = this@FlowOverlayService.layoutParams
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            runCatching { windowManager.updateViewLayout(overlayContainer, params) }
                        },
                        onDragEnd = {
                            snapToNearestEdge()
                        }
                    )
                }
            }
        }

        windowManager.addView(overlayContainer, layoutParams)
    }

    private fun snapToNearestEdge() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val margin = (12 * metrics.density).toInt()
        val viewWidth = overlayContainer.width
        val centerX = layoutParams.x + viewWidth / 2
        layoutParams.x = if (centerX < screenWidth / 2) {
            margin
        } else {
            (screenWidth - viewWidth - margin).coerceAtLeast(margin)
        }
        runCatching { windowManager.updateViewLayout(overlayContainer, layoutParams) }
    }

    private fun startForegroundWithType(micActive: Boolean) {
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notification = createNotification(micActive)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (micActive && hasMicPerm) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(micActive: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LibreFlowApplication.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (micActive) "LibreFlow Listening (On-Device)" else "LibreFlow Bubble Active")
            .setContentText(if (micActive) "Streaming locally with zero cloud" else "Tap the floating bubble to dictate")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun triggerHaptic() {
        serviceScope.launch {
            val app = application as LibreFlowApplication
            if (!app.preferences.hapticsEnabled.first()) return@launch
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            } ?: return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f)
                        .compose()
                )
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    /**
     * Custom FrameLayout matching Wispr Flow's BubbleOverlayContainer measurement behavior
     * so dynamic pill expansion never clips horizontally.
     */
    private class BubbleOverlayContainer(context: Context) : FrameLayout(context) {
        val composeView = ComposeView(context)

        init {
            addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        }
    }
}
