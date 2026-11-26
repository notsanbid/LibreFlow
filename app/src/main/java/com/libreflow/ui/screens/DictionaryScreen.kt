package com.libreflow.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.libreflow.data.local.entity.DictionaryWordEntity
import com.libreflow.ui.theme.FlowBorder
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowMuted
import com.libreflow.ui.theme.FlowSurface
import kotlinx.coroutines.launch

@Composable
fun DictionaryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LibreFlowApplication
    val scope = rememberCoroutineScope()
    val words by app.database.dictionaryDao().observeAll().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Personal Dictionary", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add custom names, technical terms, and pronunciation hints so local models spell them accurately.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (words.isEmpty()) {
                item {
                    Text(
                        text = "No custom words yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(words, key = { it.id }) { entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FlowBorder, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        color = FlowSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, style = MaterialTheme.typography.titleMedium)
                                if (entry.phoneticHint.isNotBlank()) {
                                    Text(
                                        text = "Replaces: \"${entry.phoneticHint}\"",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            IconButton(onClick = {
                                scope.launch { app.database.dictionaryDao().delete(entry) }
                            }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = FlowMuted)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = FlowInk,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add word")
        }
    }

    if (showAddDialog) {
        var word by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Vocabulary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text("Exact spelling (e.g., LibreFlow)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it },
                        label = { Text("Misheard phrase (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (word.isNotBlank()) {
                            scope.launch {
                                app.database.dictionaryDao().insert(
                                    DictionaryWordEntity(word = word.trim(), phoneticHint = hint.trim())
                                )
                            }
                            showAddDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
