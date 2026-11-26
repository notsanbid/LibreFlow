package com.libreflow.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.libreflow.LibreFlowApplication
import com.libreflow.engine.PipelineState
import com.libreflow.ui.overlay.WaveformCanvas
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowMuted
import com.libreflow.ui.theme.FlowSurface
import com.libreflow.ui.theme.LibreFlowTheme

/**
 * Companion Voice Keyboard IME fallback for apps that block Accessibility paste.
 */
class LibreFlowImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        @Volatile
        var instance: LibreFlowImeService? = null
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (instance === this) instance = null
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val app = application as LibreFlowApplication
        val pipeline = app.pipeline

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LibreFlowImeService)
            setViewTreeSavedStateRegistryOwner(this@LibreFlowImeService)
            setContent {
                val state by pipeline.state.collectAsState()
                LibreFlowTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FlowSurface)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WaveformCanvas(
                            rmsProvider = pipeline.rmsProvider,
                            isActive = state is PipelineState.Listening,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            }) {
                                Icon(Icons.Rounded.Language, contentDescription = "Switch keyboard", tint = FlowInk)
                            }

                            Surface(
                                onClick = { pipeline.toggleDictation() },
                                shape = CircleShape,
                                color = if (state is PipelineState.Listening) Color(0xFFE53935) else FlowInk,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = if (state is PipelineState.Listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = "Dictate",
                                    tint = Color.White,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            IconButton(onClick = {
                                currentInputConnection?.deleteSurroundingText(1, 0)
                            }) {
                                Icon(Icons.Rounded.Backspace, contentDescription = "Backspace", tint = FlowInk)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (state) {
                                is PipelineState.Listening -> "Listening locally..."
                                is PipelineState.Processing -> "Polishing on-device..."
                                is PipelineState.Error -> (state as PipelineState.Error).message
                                else -> "Tap mic to dictate offline"
                            },
                            fontSize = 12.sp,
                            color = FlowMuted
                        )
                    }
                }
            }
        }
    }

    fun commitDictatedText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText("$text ", 1)
    }
}
