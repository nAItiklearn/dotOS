package com.dotos.feature.settings.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.core.designsystem.component.DotPageIndicator
import com.dotos.core.designsystem.component.pressScale
import com.dotos.feature.settings.SettingsDeps
import com.dotos.feature.settings.nav.SettingsController
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.screens.StyleSample
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.IMAGE_WALLPAPER_ID
import com.dotos.feature.settings.ui.PillButton
import com.dotos.feature.settings.ui.PlusIcon
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.SelectableFrame
import com.dotos.feature.settings.ui.SettingsTheme
import com.dotos.feature.settings.ui.rememberPhotoPicker
import com.dotos.feature.settings.ui.rememberSlidePx
import com.dotos.feature.settings.ui.screenTransition
import com.dotos.feature.settings.ui.thumbConfig
import com.dotos.feature.settings.ui.withWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperLayer
import com.dotos.feature.wallpaper.engine.WallpaperRegistry
import kotlinx.coroutines.launch

/**
 * First-run setup: three skippable screens (default launcher, wallpaper, icon style). Choices apply
 * live. When the user finishes or skips, `firstRunDone` is persisted and then [onDone] is called.
 */
@Composable
fun FirstRunFlow(deps: SettingsDeps, onDone: () -> Unit) {
    val settings by deps.settings.settingsOrNull.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        val controller = remember(deps) { SettingsController(deps) }
        DisposableEffect(controller) { onDispose { controller.close() } }
        val done by rememberUpdatedState(onDone)
        val env = remember(deps, controller) {
            SettingsEnv(deps = deps, controller = controller, navigate = {}, back = {}, close = { done() })
        }
        SettingsTheme(deps, current) {
            FirstRunContent(env, current) {
                controller.scope.launch {
                    try {
                        deps.settings.update { it.copy(firstRunDone = true) }
                    } catch (e: Exception) {
                        // Even if the flag cannot be saved, never trap the user in setup.
                    }
                    done()
                }
            }
        }
    }
}

@Composable
private fun FirstRunContent(env: SettingsEnv, s: LauncherSettings, finish: () -> Unit) {
    val colors = DotTheme.colors
    var step by rememberSaveable { mutableStateOf(0) }
    val lastStep = 2
    BackHandler(enabled = step > 0) { step -= 1 }
    val reduce = LocalReduceMotion.current
    val slidePx = rememberSlidePx()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotPageIndicator(pageCount = lastStep + 1, position = { step.toFloat() }, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            SkipButton(onClick = finish)
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = { screenTransition(forward = targetState > initialState, slidePx = slidePx, reduce = reduce) },
            label = "firstRun",
        ) { page ->
            when (page) {
                0 -> HomeStep(env, s)
                1 -> WallpaperStep(env, s)
                else -> IconStep(env, s)
            }
        }
        PillButton(
            text = if (step < lastStep) "Next" else "Get started",
            onClick = {
                if (step < lastStep) {
                    step += 1
                } else {
                    finish()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Skip", style = DotTheme.type.body.copy(fontWeight = FontWeight.Medium), color = DotTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, style = DotTheme.type.display.copy(fontSize = 32.sp, lineHeight = 38.sp), color = DotTheme.colors.onSurface)
        Text(
            body,
            style = DotTheme.type.body,
            color = DotTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun HomeStep(env: SettingsEnv, s: LauncherSettings) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val w = minOf(maxWidth * 0.62f, maxHeight * 0.92f * (9f / 19.5f))
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(w), animate = true, maxFps = 24)
        }
        StepHeader(
            title = "Make DotOS your home",
            body = "Set DotOS as your default home app. It works fully offline: no account, no ads, no tracking.",
        )
        PillButton(
            text = "Set as default",
            onClick = { env.deps.openDefaultLauncherChooser() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            filled = false,
        )
    }
}

@Composable
private fun WallpaperStep(env: SettingsEnv, s: LauncherSettings) {
    val pickPhoto = rememberPhotoPicker(env)
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Pick a wallpaper", body = "Drawn live on your phone. You can change it any time in Settings.")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(WallpaperRegistry.all, key = { it.id }) { wp ->
                val selected = s.wallpaper.id == wp.id
                val thumb = remember(wp.id) { thumbConfig(wp) }
                SelectableFrame(
                    selected = selected,
                    radius = 20.dp,
                    onClick = { env.updateWallpaper { it.withWallpaper(wp) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 19.5f),
                    description = wp.name,
                ) {
                    WallpaperLayer(
                        config = if (selected) s.wallpaper else thumb,
                        animate = selected,
                        maxFps = 30,
                        allowGlass = selected,
                    )
                }
            }
            item(key = "photo") {
                val selected = s.wallpaper.id == IMAGE_WALLPAPER_ID
                SelectableFrame(
                    selected = selected,
                    radius = 20.dp,
                    onClick = pickPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 19.5f),
                    description = "Choose a photo",
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(DotTheme.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { PlusIcon() }
                }
            }
        }
    }
}

@Composable
private fun IconStep(env: SettingsEnv, s: LauncherSettings) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Pick an icon style", body = "All styles are drawn on your phone. Fine-tune them later in Settings.")
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val w = minOf(maxWidth * 0.5f, maxHeight * 0.96f * (9f / 19.5f))
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(w), extraIcons = 6)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(IconStyle.LINE to "Line", IconStyle.DOT to "Dot", IconStyle.ORIGINAL to "Original").forEach { (style, label) ->
                RoundOption(
                    label = label,
                    selected = s.iconStyle == style && s.iconPackPackage == null,
                    onClick = { env.update { it.copy(iconStyle = style, iconPackPackage = null) } },
                ) { StyleSample(style) }
            }
        }
    }
}
