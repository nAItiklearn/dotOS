package com.dotos.feature.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.GestureAction
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ChoiceRows
import com.dotos.feature.settings.ui.NavRow
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsScaffold

private fun GestureAction.friendly(): String = when (this) {
    GestureAction.NONE -> "Do nothing"
    GestureAction.OPEN_DRAWER -> "App drawer"
    GestureAction.OPEN_SEARCH -> "Search"
    GestureAction.NOTIFICATIONS -> "Notifications"
    GestureAction.QUICK_SETTINGS -> "Quick settings"
    GestureAction.LOCK_SCREEN -> "Lock screen"
    GestureAction.EDIT_MODE -> "Edit mode"
    GestureAction.DEFAULT_PAGE -> "Default page"
    GestureAction.LAUNCHER_SETTINGS -> "DotOS settings"
}

private fun GestureAction.describe(): String = when (this) {
    GestureAction.NONE -> "Nothing happens"
    GestureAction.OPEN_DRAWER -> "Opens the list of all your apps"
    GestureAction.OPEN_SEARCH -> "Opens search for apps and settings"
    GestureAction.NOTIFICATIONS -> "Pulls down the notification shade"
    GestureAction.QUICK_SETTINGS -> "Pulls down the quick settings panel"
    GestureAction.LOCK_SCREEN -> "Locks the phone; needs permission the first time"
    GestureAction.EDIT_MODE -> "Starts rearranging the home screen"
    GestureAction.DEFAULT_PAGE -> "Jumps back to your default home page"
    GestureAction.LAUNCHER_SETTINGS -> "Opens these settings"
}

@Composable
internal fun GesturesScreen(env: SettingsEnv, s: LauncherSettings) {
    val g = s.gestures
    SettingsScaffold(title = "Gestures", onBack = env.back) {
        SectionHeader("Home screen")
        GestureRow("Swipe up", "On an empty part of the home screen", g.swipeUp) { a ->
            env.update { it.copy(gestures = it.gestures.copy(swipeUp = a)) }
        }
        GestureRow("Swipe down", "On an empty part of the home screen", g.swipeDown) { a ->
            env.update { it.copy(gestures = it.gestures.copy(swipeDown = a)) }
        }
        GestureRow("Double tap", "On an empty part of the home screen", g.doubleTap) { a ->
            env.update { it.copy(gestures = it.gestures.copy(doubleTap = a)) }
        }
        GestureRow("Long press", "On an empty part of the home screen", g.longPressEmpty) { a ->
            env.update { it.copy(gestures = it.gestures.copy(longPressEmpty = a)) }
        }
        GestureRow("Home button", "When you are already on the home screen", g.homePress) { a ->
            env.update { it.copy(gestures = it.gestures.copy(homePress = a)) }
        }
        Caption("Locking the screen with a gesture needs the launcher to be allowed to lock the phone. DotOS asks the first time.")
    }
}

@Composable
private fun GestureRow(title: String, hint: String, current: GestureAction, onChange: (GestureAction) -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    NavRow(title = title, subtitle = hint, onClick = { open = !open }, value = current.friendly())
    AnimatedVisibility(visible = open) {
        Column(Modifier.padding(start = 12.dp)) {
            ChoiceRows(
                options = GestureAction.entries,
                selected = current,
                title = { it.friendly() },
                description = { it.describe() },
                onSelect = onChange,
            )
        }
    }
}
