package com.libreflow.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.libreflow.ui.theme.FlowBorder
import com.libreflow.ui.theme.FlowEmerald
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowSurface
import com.libreflow.ui.theme.FlowSurfaceSubtle
import kotlinx.coroutines.launch

@Composable
fun StyleScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LibreFlowApplication
    val scope = rememberCoroutineScope()

    val styles by app.database.styleDao().observeAll().collectAsState(initial = emptyList())
    val activeStyleId by app.preferences.activeStyleId.collectAsState(initial = "professional_bullets")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Dictation Style", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose how the on-device LLM formats grammar, bullet points, and tone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(styles, key = { it.id }) { style ->
            val selected = style.id == activeStyleId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) FlowInk else FlowBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        scope.launch { app.preferences.setActiveStyleId(style.id) }
                    },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) FlowSurfaceSubtle else FlowSurface
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(style.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(style.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = FlowEmerald
                        )
                    }
                }
            }
        }
    }
}
