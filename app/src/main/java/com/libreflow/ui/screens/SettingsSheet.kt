package com.libreflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.libreflow.LibreFlowApplication
import com.libreflow.engine.PipelineState
import com.libreflow.ui.components.FlowSwitch
import com.libreflow.ui.overlay.FloatingBubbleView
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LibreFlowApplication
    val scope = rememberCoroutineScope()

    val bubbleSize by app.preferences.bubbleSize.collectAsState(initial = 1.0f)
    val bubbleOpacity by app.preferences.bubbleOpacity.collectAsState(initial = 0.92f)
    val fastPath by app.preferences.fastPathEnabled.collectAsState(initial = true)
    val soundCues by app.preferences.soundCuesEnabled.collectAsState(initial = true)
    val haptics by app.preferences.hapticsEnabled.collectAsState(initial = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FlowSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Bubble & Engine Settings", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FloatingBubbleView(
                    state = PipelineState.Idle,
                    rmsProvider = { 0f },
                    sizeScale = bubbleSize,
                    opacity = bubbleOpacity,
                    onTap = {},
                    onDrag = { _, _ -> },
                    onDragEnd = {}
                )
            }

            Column {
                Text(
                    text = "Flow Bubble Size (${String.format("%.2fx", bubbleSize)})",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = bubbleSize,
                    onValueChange = { scope.launch { app.preferences.setBubbleSize(it) } },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = FlowInk, activeTrackColor = FlowInk)
                )
            }

            Column {
                Text(
                    text = "Flow Bubble Opacity (${(bubbleOpacity * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = bubbleOpacity,
                    onValueChange = { scope.launch { app.preferences.setBubbleOpacity(it) } },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = FlowInk, activeTrackColor = FlowInk)
                )
            }

            SettingToggleRow("Fast-Path (≤3 words ~50ms)", fastPath) {
                scope.launch { app.preferences.setFastPathEnabled(it) }
            }
            SettingToggleRow("Dictation Sound Cues", soundCues) {
                scope.launch { app.preferences.setSoundCuesEnabled(it) }
            }
            SettingToggleRow("Haptic Feedback", haptics) {
                scope.launch { app.preferences.setHapticsEnabled(it) }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
