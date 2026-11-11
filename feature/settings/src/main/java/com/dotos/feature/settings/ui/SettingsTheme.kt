package com.dotos.feature.settings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.dotos.core.data.settings.Accent
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.ThemeMode
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.icons.engine.IconSpec
import com.dotos.feature.icons.engine.LocalIconEngine
import com.dotos.feature.icons.engine.LocalIconSpec
import com.dotos.feature.icons.engine.color
import com.dotos.feature.icons.engine.toIconSpec
import com.dotos.feature.settings.SettingsDeps
import com.dotos.feature.wallpaper.engine.resolve

/**
 * Key inside [WallpaperConfig.colors] that stores the user's chosen "wallpaper colour" tone (ARGB).
 * It is not a wallpaper colour slot (the renderer ignores it); the Themed icon style reads it to
 * tint plates and glyphs. When absent, the middle tone of the wallpaper palette is used.
 */
internal const val TONE_KEY = "tone"

/** The wallpaper's own palette, darkest first. Used for "Wallpaper colours" swatches. */
internal fun wallpaperPalette(config: WallpaperConfig): List<Color> {
    val resolved = config.resolve()
    return resolved.wallpaper.colorSlots
        .mapNotNull { resolved.colors[it.id] }
        .map { it.copy(alpha = 1f) }
        .distinct()
        .sortedBy { it.luminance() }
}

internal fun WallpaperConfig.tone(): Color? = colors[TONE_KEY]?.let { Color(it.toInt()) }

internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** (plate, glyph) used by the Themed icon style. */
internal fun themedPair(config: WallpaperConfig): Pair<Color, Color> {
    val palette = wallpaperPalette(config)
    val base = config.tone() ?: palette.getOrNull(palette.size / 2) ?: Color(0xFF8A8A8A)
    return lerp(base, Color.Black, 0.62f) to lerp(base, Color.White, 0.82f)
}

/** The exact [IconSpec] every preview uses, so previews match the home screen. */
internal fun LauncherSettings.previewIconSpec(): IconSpec {
    val (plate, glyph) = themedPair(wallpaper)
    return toIconSpec(plate, glyph)
}

/** Colour of the accent as the theme wants it (NONE falls back to the content colour). */
internal fun Accent.themeColor(): Color = if (this == Accent.NONE) Color.Unspecified else color()

/**
 * Applies the user's own theme (mode, accent, font, motion, haptics) to the settings UI so every
 * change is visible instantly, and provides the icon engine + spec the previews render with.
 */
@Composable
internal fun SettingsTheme(deps: SettingsDeps, settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    DotTheme(
        dark = dark,
        accent = settings.accent.themeColor(),
        useDotDisplayFont = settings.dotDisplayFont,
        reduceMotion = settings.reduceMotion,
        hapticsEnabled = settings.haptics,
    ) {
        val spec = remember(settings) { settings.previewIconSpec() }
        CompositionLocalProvider(
            LocalIconEngine provides deps.icons,
            LocalIconSpec provides spec,
            content = content,
        )
    }
}
