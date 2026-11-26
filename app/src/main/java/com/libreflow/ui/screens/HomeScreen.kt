package com.libreflow.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.libreflow.LibreFlowApplication
import com.libreflow.data.local.entity.TranscriptionEntity
import com.libreflow.engine.PipelineState
import com.libreflow.service.FlowAccessibilityService
import com.libreflow.service.FlowOverlayService
import com.libreflow.ui.components.FlowSwitch
import com.libreflow.ui.components.HomeStatCard
import com.libreflow.ui.overlay.WaveformCanvas
import com.libreflow.ui.theme.FlowBorder
import com.libreflow.ui.theme.FlowDanger
import com.libreflow.ui.theme.FlowEmerald
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowMuted
import com.libreflow.ui.theme.FlowSurface
import com.libreflow.ui.theme.FlowSurfaceSubtle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LibreFlowApplication
    val scope = rememberCoroutineScope()

    val totalWords by app.database.transcriptionDao().observeTotalWords().collectAsState(initial = 0)
    val transcripts by app.database.transcriptionDao().observeAll().collectAsState(initial = emptyList())
    val pipelineState by app.pipeline.state.collectAsState()
    val overlayEnabled by app.preferences.overlayEnabled.collectAsState(initial = false)

    val pm = remember { context.getSystemService(android.os.PowerManager::class.java) }
    val canDrawOverlaysState = remember { androidx.compose.runtime.mutableStateOf(Settings.canDrawOverlays(context)) }
    val isA11yEnabledState = remember { androidx.compose.runtime.mutableStateOf(isAccessibilityEnabled(context)) }
    val isBatteryUnrestrictedState = remember {
        androidx.compose.runtime.mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                canDrawOverlaysState.value = Settings.canDrawOverlays(context)
                isA11yEnabledState.value = isAccessibilityEnabled(context)
                isBatteryUnrestrictedState.value = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canDrawOverlays = canDrawOverlaysState.value
    val isA11yEnabled = isA11yEnabledState.value
    val isBatteryUnrestricted = isBatteryUnrestrictedState.value

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeStatCard(
                totalWords = totalWords
            )
        }

        // Overlay & Permissions Control Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FlowBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = FlowSurface
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating Dictation Bubble", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (overlayEnabled && canDrawOverlays) "Active over all apps" else "Enable to dictate anywhere",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        FlowSwitch(
                            checked = overlayEnabled && canDrawOverlays,
                            onCheckedChange = { enable ->
                                if (enable && !canDrawOverlays) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    scope.launch {
                                        app.preferences.setOverlayEnabled(enable)
                                        if (enable) FlowOverlayService.start(context)
                                        else FlowOverlayService.stop(context)
                                    }
                                }
                            }
                        )
                    }

                    if (!hasMicPermission || !isA11yEnabled || !isBatteryUnrestricted) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasMicPermission) {
                                OutlinedButton(
                                    onClick = onRequestMicPermission,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Grant Mic")
                                }
                            }
                            if (!isA11yEnabled) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, FlowDanger),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FlowDanger)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = FlowDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enable Injector", color = FlowDanger)
                                }
                            }
                            if (!isBatteryUnrestricted) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Unrestricted CPU")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick In-App Dictation Sandbox
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FlowBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = FlowSurfaceSubtle
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (val s = pipelineState) {
                                is PipelineState.Listening -> s.livePartialText.ifBlank { "Listening locally..." }
                                is PipelineState.Processing -> "Polishing on-device..."
                                is PipelineState.Done -> "Ready (${s.latencyMs} ms)"
                                is PipelineState.Error -> s.message
                                else -> "Quick Test Dictation"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pipelineState is PipelineState.Error) FlowDanger else FlowInk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        WaveformCanvas(
                            rmsProvider = app.pipeline.rmsProvider,
                            isActive = pipelineState is PipelineState.Listening,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                onRequestMicPermission()
                            } else {
                                app.pipeline.toggleDictation()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pipelineState is PipelineState.Listening) FlowDanger else FlowInk,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (pipelineState is PipelineState.Listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pipelineState is PipelineState.Listening) "Stop" else "Dictate",
                            color = Color.White
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Transcriptions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (transcripts.isEmpty()) {
            item {
                Text(
                    text = "No dictations yet. Download a VAD + ASR model in the Models tab and tap Dictate.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(transcripts, key = { it.id }) { item ->
                TranscriptCard(
                    entity = item,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("LibreFlow", item.polishedText))
                    },
                    onDelete = {
                        scope.launch { app.database.transcriptionDao().deleteById(item.id) }
                    }
                )
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    if (FlowAccessibilityService.instance != null) return true
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains("${context.packageName}/${FlowAccessibilityService::class.java.name}") ||
        enabled.contains("${context.packageName}/.service.FlowAccessibilityService")
}

@Composable
private fun TranscriptCard(
    entity: TranscriptionEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(entity.createdAtEpochMs) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entity.createdAtEpochMs))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FlowBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = FlowSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$timeStr • ${entity.styleName} • ${entity.latencyMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = FlowMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = FlowMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = entity.polishedText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
