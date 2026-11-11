package com.dotos.feature.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ChevronIcon
import com.dotos.feature.settings.ui.DotMatrixGlyph
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SearchField
import com.dotos.feature.settings.ui.SectionGlyphs
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold

private class Section(val title: String, val subtitle: String, val route: String, val glyph: List<String>)

private val Sections = listOf(
    Section("Home screen", "Wallpaper, colours, icon pack and layout", Routes.HOME, SectionGlyphs.home),
    Section("App drawer", "Layout, sorting, search and scroller", Routes.DRAWER, SectionGlyphs.drawer),
    Section("Icons", "Style, shape, size and per-app icons", Routes.ICONS, SectionGlyphs.icons),
    Section("Wallpaper & atmosphere", "Gallery, customiser, Glass and live wallpaper", Routes.WALLPAPERS, SectionGlyphs.wallpaper),
    Section("Widgets", "The bundled DotOS widgets", Routes.WIDGETS, SectionGlyphs.widgets),
    Section("Folders", "How folders open", Routes.FOLDERS, SectionGlyphs.folders),
    Section("Gestures", "What swipes, taps and presses do", Routes.GESTURES, SectionGlyphs.gestures),
    Section("Theme", "Dark, light, accent colour and font", Routes.THEME, SectionGlyphs.theme),
    Section("Notifications & badges", "Badge style and notification access", Routes.BADGES, SectionGlyphs.badges),
    Section("Hidden apps & privacy", "Hide apps behind your screen lock", Routes.PRIVACY, SectionGlyphs.privacy),
    Section("Backup & restore", "Export or import everything as one file", Routes.BACKUP, SectionGlyphs.backup),
    Section("Performance", "Animation, blur and haptics", Routes.PERFORMANCE, SectionGlyphs.performance),
    Section("About", "Version, licenses and reset", Routes.ABOUT, SectionGlyphs.about),
)

@Composable
internal fun MainScreen(env: SettingsEnv, s: LauncherSettings) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SettingsIndex.search(query) }
    SettingsScaffold(title = "Settings", onBack = env.back) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search settings",
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
        )
        if (query.isBlank()) {
            Sections.forEach { section ->
                SettingsRow(
                    title = section.title,
                    subtitle = section.subtitle,
                    onClick = { env.navigate(section.route) },
                    leading = { DotMatrixGlyph(section.glyph) },
                    trailing = { ChevronIcon() },
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "DotOS Launcher ${env.deps.versionName}",
                    style = DotTheme.type.mono,
                    color = DotTheme.colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (results.isEmpty()) {
            Caption("No settings match “${query.trim()}”.", Modifier.padding(top = 12.dp))
        } else {
            results.forEach { entry ->
                SettingsRow(
                    title = entry.title,
                    subtitle = "${entry.section} · ${entry.description}",
                    onClick = { env.navigate(entry.route) },
                    trailing = { ChevronIcon() },
                )
            }
        }
    }
}
