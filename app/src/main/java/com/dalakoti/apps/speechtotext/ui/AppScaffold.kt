package com.dalakoti.apps.speechtotext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    Dictate("Dictate", Icons.Default.Mic),
    History("History", Icons.Default.History),
    Settings("Settings", Icons.Default.Tune),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: DictationViewModel) {
    // Saved as an ordinal: enums need a custom Saver, an Int does not.
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tabIndex = entry.ordinal },
                        icon = { Icon(entry.icon, entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Dictate -> DictationScreen(vm, onOpenSettings = { tabIndex = Tab.Settings.ordinal })
                Tab.History -> HistoryScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}
