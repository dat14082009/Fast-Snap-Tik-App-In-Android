package com.stone.tiktoksave.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.stone.tiktoksave.AppContainer
import com.stone.tiktoksave.ui.components.ShareModeDialog
import com.stone.tiktoksave.ui.screens.AboutScreen
import com.stone.tiktoksave.ui.screens.HistoryScreen
import com.stone.tiktoksave.ui.screens.HomeScreen
import com.stone.tiktoksave.ui.screens.SettingsScreen
import com.stone.tiktoksave.ui.screens.ThemeScreen
import kotlinx.coroutines.launch

private enum class Screen { HOME, SETTINGS, ABOUT, THEME, HISTORY }

@Composable
fun AppNavigation(vm: MainViewModel, container: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.ABOUT, Screen.THEME, Screen.HISTORY -> Screen.SETTINGS
            else -> Screen.HOME
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            vm = vm,
            onOpenSettings = { screen = Screen.SETTINGS },
            onGearLongPress = { showModeDialog = true }
        )
        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.HOME },
            onAbout = { screen = Screen.ABOUT },
            onTheme = { screen = Screen.THEME },
            onHistory = { screen = Screen.HISTORY }
        )
        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
        Screen.THEME -> ThemeScreen(
            onBack = { screen = Screen.SETTINGS },
            onSelect = { choice -> scope.launch { container.settings.setTheme(choice) } }
        )
        Screen.HISTORY -> HistoryScreen(history = container.history, onBack = { screen = Screen.SETTINGS })
    }

    if (showModeDialog) {
        val mode by container.settings.shareMode.collectAsState(initial = null)
        ShareModeDialog(
            current = mode,
            onSelect = { selected ->
                scope.launch { container.settings.setShareMode(selected) }
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false }
        )
    }
}
