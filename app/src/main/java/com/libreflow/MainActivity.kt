package com.libreflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.libreflow.ui.screens.DictionaryScreen
import com.libreflow.ui.screens.HomeScreen
import com.libreflow.ui.screens.ModelsScreen
import com.libreflow.ui.screens.SettingsSheet
import com.libreflow.ui.screens.StyleScreen
import com.libreflow.ui.theme.FlowBackground
import com.libreflow.ui.theme.FlowInk
import com.libreflow.ui.theme.FlowSurface
import com.libreflow.ui.theme.GaramondFamily
import com.libreflow.ui.theme.LibreFlowTheme

enum class FlowTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    DICTIONARY("Dictionary", Icons.Rounded.Book),
    STYLE("Style", Icons.Rounded.AutoFixHigh),
    MODELS("Models", Icons.Rounded.Memory)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibreFlowTheme {
                LibreFlowRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibreFlowRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentTab by remember { mutableStateOf(FlowTab.HOME) }
    var showSettings by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val requestPermissions = {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permLauncher.launch(perms)
    }

    Scaffold(
        containerColor = FlowBackground,
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_libreflow_logo),
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.Unspecified,
                            modifier = androidx.compose.ui.Modifier.size(28.dp)
                        )
                        Text(
                            text = "LibreFlow",
                            fontFamily = GaramondFamily,
                            color = FlowInk
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Settings", tint = FlowInk)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlowBackground)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = FlowSurface) {
                FlowTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = FlowInk,
                            selectedTextColor = FlowInk,
                            indicatorColor = com.libreflow.ui.theme.FlowSurfaceSubtle
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 120),
                label = "tabCrossfade"
            ) { tab ->
                when (tab) {
                    FlowTab.HOME -> HomeScreen(
                        hasMicPermission = hasMicPermission,
                        onRequestMicPermission = requestPermissions
                    )
                    FlowTab.DICTIONARY -> DictionaryScreen()
                    FlowTab.STYLE -> StyleScreen()
                    FlowTab.MODELS -> ModelsScreen()
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}
