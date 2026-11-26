package com.libreflow.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.libreflow.LibreFlowApplication
import com.libreflow.data.local.entity.ModelDescriptorEntity
import com.libreflow.data.local.entity.ModelDownloadState
import com.libreflow.data.local.entity.ModelStage
import com.libreflow.ui.theme.FlowBorder
import com.libreflow.ui.theme.FlowDanger
import com.libreflow.ui.theme.FlowEmerald
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowMuted
import com.libreflow.ui.theme.FlowSurface
import com.libreflow.ui.theme.FlowSurfaceSubtle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private fun isModelSupportedOnDevice(
    context: android.content.Context,
    model: ModelDescriptorEntity
): Pair<Boolean, String?> {
    if (model.engineType == "llama_gguf") {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        if (!primaryAbi.startsWith("arm64")) {
            return false to "Requires arm64-v8a phone (disabled on $primaryAbi)"
        }
    }
    return true to null
}

@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LibreFlowApplication
    val scope = rememberCoroutineScope()
    val models by app.database.modelDao().observeAll().collectAsState(initial = emptyList())
    var loadingIds by remember { mutableStateOf(emptySet<String>()) }

    val vadModels = models.filter { it.stage == ModelStage.VAD.name }
    val asrModels = models.filter { it.stage == ModelStage.ASR.name }
    val slmModels = models.filter { it.stage == ModelStage.SLM.name }

    val vadReady = vadModels.any { it.downloadState == ModelDownloadState.LOADED.name }
    val asrReady = asrModels.any { it.downloadState == ModelDownloadState.LOADED.name }
    val slmReady = slmModels.any { it.downloadState == ModelDownloadState.LOADED.name }

    val loadModel: (ModelDescriptorEntity) -> Unit = { model ->
        loadingIds = loadingIds + model.id
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(model.localDirPath.ifBlank { app.downloader.getModelDir(model.id).absolutePath })
                val result = when (model.stage) {
                    ModelStage.VAD.name -> app.vadEngine.load(dir)
                    ModelStage.ASR.name -> app.asrEngine.load(model, dir).also {
                        if (it.isSuccess) app.preferences.setActiveAsrModelId(model.id)
                    }
                    ModelStage.SLM.name -> app.slmEngine.load(model, dir).also {
                        if (it.isSuccess) app.preferences.setActiveSlmModelId(model.id)
                    }
                    else -> Result.failure(IllegalStateException("Unknown stage"))
                }
                if (result.isSuccess) {
                    app.database.modelDao().updateState(
                        model.id,
                        ModelDownloadState.LOADED.name,
                        100,
                        dir.absolutePath,
                        null
                    )
                } else {
                    app.database.modelDao().updateState(
                        model.id,
                        ModelDownloadState.ERROR.name,
                        100,
                        dir.absolutePath,
                        result.exceptionOrNull()?.message ?: "Load failed"
                    )
                }
            } finally {
                loadingIds = loadingIds - model.id
            }
        }
    }

    val unloadModel: (ModelDescriptorEntity) -> Unit = { model ->
        scope.launch(Dispatchers.IO) {
            when (model.stage) {
                ModelStage.VAD.name -> app.vadEngine.unload()
                ModelStage.ASR.name -> app.asrEngine.unload()
                ModelStage.SLM.name -> app.slmEngine.unload()
            }
            app.database.modelDao().updateState(
                model.id,
                ModelDownloadState.DOWNLOADED.name,
                100,
                model.localDirPath,
                null
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Local AI Models", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detected ${app.ramTier.label}. Stored in Downloads/LibreFlowModels and memory-mapped locally.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupChecklistCard(vadReady = vadReady, asrReady = asrReady, slmReady = slmReady)
        }

        item {
            SectionHeader(
                title = "Step 1 · Voice Detection",
                badge = "Required",
                subtitle = "Detects when you start and stop speaking."
            )
        }
        items(vadModels, key = { it.id }) { model ->
            ModelItemCard(
                model = model,
                isLoadingIntoRam = model.id in loadingIds,
                requirementBadge = "Required",
                onDownload = { app.applicationScope.launch { app.downloader.downloadModel(model) } },
                onLoad = { loadModel(model) },
                onUnload = { unloadModel(model) },
                onDelete = { scope.launch { app.downloader.deleteModel(model) } }
            )
        }

        item {
            SectionHeader(
                title = "Step 2 · Speech-to-Text",
                badge = "Pick 1",
                subtitle = "Converts your voice into text offline."
            )
        }
        items(asrModels, key = { it.id }) { model ->
            ModelItemCard(
                model = model,
                isLoadingIntoRam = model.id in loadingIds,
                requirementBadge = if (model.id == "moonshine-tiny-en") "Fastest" else "ASR",
                onDownload = { app.applicationScope.launch { app.downloader.downloadModel(model) } },
                onLoad = { loadModel(model) },
                onUnload = { unloadModel(model) },
                onDelete = { scope.launch { app.downloader.deleteModel(model) } }
            )
        }

        item {
            SectionHeader(
                title = "Step 3 · Smart AI Polish",
                badge = "Optional",
                subtitle = "Fixes grammar, removes fillers, and structures bullet points."
            )
        }
        items(slmModels, key = { it.id }) { model ->
            ModelItemCard(
                model = model,
                isLoadingIntoRam = model.id in loadingIds,
                requirementBadge = "Optional LLM",
                onDownload = { app.applicationScope.launch { app.downloader.downloadModel(model) } },
                onLoad = { loadModel(model) },
                onUnload = { unloadModel(model) },
                onDelete = { scope.launch { app.downloader.deleteModel(model) } }
            )
        }
    }
}

@Composable
private fun SetupChecklistCard(vadReady: Boolean, asrReady: Boolean, slmReady: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FlowBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = FlowSurfaceSubtle
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Setup Checklist", style = MaterialTheme.typography.labelLarge)
            ChecklistRow("1. Voice Detection (Silero VAD)", vadReady, required = true)
            ChecklistRow("2. Speech-to-Text (Pick one ASR)", asrReady, required = true)
            ChecklistRow("3. Smart AI Polish (Optional LLM)", slmReady, required = false)
        }
    }
}

@Composable
private fun ChecklistRow(label: String, done: Boolean, required: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) FlowEmerald else FlowMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (done) "$label — Ready" else if (required) label else "$label — Optional",
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) FlowInk else FlowMuted
        )
    }
}

@Composable
private fun SectionHeader(title: String, badge: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(999.dp), color = FlowSurfaceSubtle) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModelItemCard(
    model: ModelDescriptorEntity,
    isLoadingIntoRam: Boolean,
    requirementBadge: String,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val state = runCatching { ModelDownloadState.valueOf(model.downloadState) }
        .getOrDefault(ModelDownloadState.NOT_DOWNLOADED)
    val (supported, unsupportedReason) = remember(model.id) { isModelSupportedOnDevice(context, model) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FlowBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = FlowSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(999.dp), color = FlowSurfaceSubtle) {
                    Text(
                        text = "$requirementBadge • ${model.sizeMb} MB",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(model.description, style = MaterialTheme.typography.bodyMedium)

            if (!supported && unsupportedReason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(unsupportedReason, style = MaterialTheme.typography.bodyMedium, color = FlowMuted)
            } else if (model.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(model.errorMessage, style = MaterialTheme.typography.bodyMedium, color = FlowDanger)
            }

            if (state == ModelDownloadState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (model.downloadProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = FlowInk
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = Triple(state, isLoadingIntoRam, supported),
                label = "modelActionState"
            ) { (curState, loadingRam, isSupported) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        loadingRam -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = FlowSurfaceSubtle,
                                    disabledContentColor = FlowInk
                                )
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = FlowInk
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading into RAM...")
                            }
                        }
                        curState == ModelDownloadState.NOT_DOWNLOADED || curState == ModelDownloadState.ERROR -> {
                            Button(
                                onClick = onDownload,
                                enabled = isSupported,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlowInk,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFE0DDD7),
                                    disabledContentColor = Color(0xFF8A8D94)
                                )
                            ) {
                                Text(if (isSupported) "Download" else "Unavailable on x86_64")
                            }
                        }
                        curState == ModelDownloadState.DOWNLOADING -> {
                            Text("Downloading ${model.downloadProgress}%...", style = MaterialTheme.typography.labelMedium)
                        }
                        curState == ModelDownloadState.DOWNLOADED -> {
                            Button(
                                onClick = onLoad,
                                enabled = isSupported,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlowInk,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFE0DDD7),
                                    disabledContentColor = Color(0xFF8A8D94)
                                )
                            ) {
                                Text(if (isSupported) "Load into RAM" else "Unavailable on x86_64")
                            }
                            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(12.dp)) {
                                Text("Delete")
                            }
                        }
                        curState == ModelDownloadState.LOADED -> {
                            Button(
                                onClick = onUnload,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FlowEmerald, contentColor = Color.White)
                            ) { Text("Loaded (Tap to Unload)") }
                        }
                    }
                }
            }
        }
    }
}
