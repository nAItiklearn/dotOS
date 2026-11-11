package com.dotos.feature.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.Accent
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.ThemeMode
import com.dotos.core.designsystem.component.DotSegmented
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.icons.engine.color
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ColoursIcon
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.TONE_KEY
import com.dotos.feature.settings.ui.ToggleRow
import com.dotos.feature.settings.ui.toArgbLong
import com.dotos.feature.settings.ui.tone
import com.dotos.feature.settings.ui.wallpaperPalette

private fun Accent.label(): String = when (this) {
    Accent.RED -> "Red"
    Accent.WHITE -> "White"
    Accent.GREEN -> "Green"
    Accent.BLUE -> "Blue"
    Accent.AMBER -> "Amber"
    Accent.NONE -> "None"
}

/** Colours (reference: preview, "Wallpaper colours | Basic colours", round swatches, themed-icons toggle). */
@Composable
internal fun ColoursScreen(env: SettingsEnv, s: LauncherSettings) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val haptics = rememberDotHaptics()
    val palette = remember(s.wallpaper) { wallpaperPalette(s.wallpaper) }
    val tone = s.wallpaper.tone()
    val toneArgb = tone?.toArgb()

    SettingsScaffold(title = "Colours", onBack = env.back) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(184.dp), extraIcons = 6)
        }

        DotSegmented(
            options = listOf("Wallpaper colours", "Basic colours"),
            selectedIndex = tab,
            onSelect = {
                haptics.tick()
                tab = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tab == 0) {
                item(key = "none") {
                    RoundOption(
                        label = "None",
                        selected = toneArgb == null,
                        onClick = {
                            env.update { cur ->
                                cur.copy(
                                    iconStyle = if (cur.iconStyle == IconStyle.THEMED) IconStyle.LINE else cur.iconStyle,
                                    wallpaper = cur.wallpaper.copy(colors = cur.wallpaper.colors - TONE_KEY),
                                )
                            }
                        },
                        size = 52.dp,
                    ) { ColoursIcon() }
                }
                items(palette, key = { it.toArgb() }) { c ->
                    RoundOption(
                        label = null,
                        selected = toneArgb != null && toneArgb == c.toArgb(),
                        onClick = {
                            env.update { cur ->
                                cur.copy(
                                    iconStyle = IconStyle.THEMED,
                                    wallpaper = cur.wallpaper.copy(colors = cur.wallpaper.colors + (TONE_KEY to c.toArgbLong())),
                                )
                            }
                        },
                        size = 52.dp,
                        fill = c,
                    )
                }
            } else {
                items(Accent.entries, key = { it.name }) { accent ->
                    if (accent == Accent.NONE) {
                        RoundOption(
                            label = accent.label(),
                            selected = s.accent == accent,
                            onClick = { env.update { it.copy(accent = accent) } },
                            size = 52.dp,
                        ) { ColoursIcon() }
                    } else {
                        RoundOption(
                            label = accent.label(),
                            selected = s.accent == accent,
                            onClick = { env.update { it.copy(accent = accent) } },
                            size = 52.dp,
                            fill = accent.color(),
                        )
                    }
                }
            }
        }
        Caption(
            if (tab == 0) {
                "Picks a tone from your wallpaper and tints icon plates with it (turns on Themed icons)."
            } else {
                "Accent colour for dots, highlights and active controls."
            },
            Modifier.padding(top = 8.dp),
        )

        SectionHeader("Appearance")
        OptionSegmented(
            options = ThemeMode.entries,
            selected = s.themeMode,
            label = {
                when (it) {
                    ThemeMode.DARK -> "Dark"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.AUTO -> "Auto"
                }
            },
            onSelect = { mode -> env.update { it.copy(themeMode = mode) } },
        )
        ToggleRow(
            title = "Themed icons",
            description = "Tint icons with colours from your wallpaper",
            checked = s.iconStyle == IconStyle.THEMED,
            onCheckedChange = { on -> env.update { it.copy(iconStyle = if (on) IconStyle.THEMED else IconStyle.LINE) } },
        )
        ToggleRow(
            title = "Dot display font",
            description = "Use the dot-matrix font for clocks and big numbers",
            checked = s.dotDisplayFont,
            onCheckedChange = { on -> env.update { it.copy(dotDisplayFont = on) } },
        )
    }
}
