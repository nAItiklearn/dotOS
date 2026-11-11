package com.dotos.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.settings.firstrun.FirstRunFlow
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsController
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.screens.AboutScreen
import com.dotos.feature.settings.screens.BackupScreen
import com.dotos.feature.settings.screens.BadgesScreen
import com.dotos.feature.settings.screens.ColoursScreen
import com.dotos.feature.settings.screens.DrawerScreen
import com.dotos.feature.settings.screens.FoldersScreen
import com.dotos.feature.settings.screens.GesturesScreen
import com.dotos.feature.settings.screens.HomeCustomizerScreen
import com.dotos.feature.settings.screens.IconOverridesScreen
import com.dotos.feature.settings.screens.IconPackScreen
import com.dotos.feature.settings.screens.IconPickScreen
import com.dotos.feature.settings.screens.LayoutScreen
import com.dotos.feature.settings.screens.LicensesScreen
import com.dotos.feature.settings.screens.MainScreen
import com.dotos.feature.settings.screens.PerformanceScreen
import com.dotos.feature.settings.screens.PrivacyScreen
import com.dotos.feature.settings.screens.ThemeScreen
import com.dotos.feature.settings.screens.WallpaperEditorScreen
import com.dotos.feature.settings.screens.WallpaperGalleryScreen
import com.dotos.feature.settings.screens.WidgetsScreen
import com.dotos.feature.settings.ui.SettingsTheme
import com.dotos.feature.settings.ui.rememberSlidePx
import com.dotos.feature.settings.ui.screenTransition

/**
 * The whole settings experience.
 *
 * @param startScreen one of the [SettingsScreens] ids (deep link from the home edit sheet), or null for the main list.
 *   Back on the first screen calls [onClose].
 * The screen applies the user's own theme (mode, accent, font) so every change is visible instantly. The host does
 * not need to wrap it in a DotTheme.
 */
@Composable
fun SettingsRoot(
    deps: SettingsDeps,
    startScreen: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by deps.settings.settingsOrNull.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
    } else {
        val controller = remember(deps) { SettingsController(deps) }
        DisposableEffect(controller) { onDispose { controller.close() } }
        SettingsTheme(deps, current) {
            SettingsNavHost(deps, current, controller, startScreen, onClose, modifier)
        }
    }
}

private data class NavState(val route: String, val depth: Int)

@Composable
private fun SettingsNavHost(
    deps: SettingsDeps,
    s: LauncherSettings,
    controller: SettingsController,
    startScreen: String?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val initial = Routes.fromDeepLink(startScreen)
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf(initial) }
    val closeHost by rememberUpdatedState(onClose)
    val env = remember(deps, controller, stack) {
        SettingsEnv(
            deps = deps,
            controller = controller,
            navigate = { route -> if (stack.lastOrNull() != route) stack.add(route) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else closeHost() },
            close = { closeHost() },
        )
    }
    BackHandler { env.back() }

    val holder = rememberSaveableStateHolder()
    val reduce = LocalReduceMotion.current
    val slidePx = rememberSlidePx()
    val nav = NavState(stack.last(), stack.size)

    Box(modifier.fillMaxSize().background(DotTheme.colors.background)) {
        AnimatedContent(
            targetState = nav,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                screenTransition(forward = targetState.depth >= initialState.depth, slidePx = slidePx, reduce = reduce)
            },
            label = "settingsNav",
        ) { target ->
            holder.SaveableStateProvider(target.route) {
                SettingsScreen(target.route, env, s)
            }
        }
    }
}

@Composable
private fun SettingsScreen(encoded: String, env: SettingsEnv, s: LauncherSettings) {
    when (Routes.idOf(encoded)) {
        Routes.MAIN -> MainScreen(env, s)
        Routes.HOME -> HomeCustomizerScreen(env, s)
        Routes.WALLPAPERS -> WallpaperGalleryScreen(env, s)
        Routes.WALLPAPER_EDITOR -> WallpaperEditorScreen(env, s)
        Routes.COLOURS -> ColoursScreen(env, s)
        Routes.ICONS -> IconPackScreen(env, s)
        Routes.ICON_OVERRIDES -> IconOverridesScreen(env, s)
        Routes.ICON_PICK -> IconPickScreen(env, s, Routes.argOf(encoded))
        Routes.LAYOUT -> LayoutScreen(env, s)
        Routes.DRAWER -> DrawerScreen(env, s)
        Routes.FOLDERS -> FoldersScreen(env, s)
        Routes.GESTURES -> GesturesScreen(env, s)
        Routes.THEME -> ThemeScreen(env, s)
        Routes.BADGES -> BadgesScreen(env, s)
        Routes.PRIVACY -> PrivacyScreen(env, s)
        Routes.BACKUP -> BackupScreen(env, s)
        Routes.PERFORMANCE -> PerformanceScreen(env, s)
        Routes.ABOUT -> AboutScreen(env, s)
        Routes.LICENSES -> LicensesScreen(env, s)
        Routes.WIDGETS -> WidgetsScreen(env, s)
        Routes.FIRST_RUN -> FirstRunFlow(deps = env.deps, onDone = env.close)
        else -> MainScreen(env, s)
    }
}
