package com.dotos.feature.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.Accent
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.ThemeMode
import com.dotos.feature.icons.engine.color
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.ColoursIcon
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.ToggleRow

@Composable
internal fun ThemeScreen(env: SettingsEnv, s: LauncherSettings) {
    SettingsScaffold(title = "Theme", onBack = env.back) {
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

        SectionHeader("Accent colour")
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(Accent.entries, key = { it.name }) { accent ->
                val label = when (accent) {
                    Accent.RED -> "Red"
                    Accent.WHITE -> "White"
                    Accent.GREEN -> "Green"
                    Accent.BLUE -> "Blue"
                    Accent.AMBER -> "Amber"
                    Accent.NONE -> "None"
                }
                if (accent == Accent.NONE) {
                    RoundOption(
                        label = label,
                        selected = s.accent == accent,
                        onClick = { env.update { it.copy(accent = accent) } },
                        size = 52.dp,
                    ) { ColoursIcon() }
                } else {
                    RoundOption(
                        label = label,
                        selected = s.accent == accent,
                        onClick = { env.update { it.copy(accent = accent) } },
                        size = 52.dp,
                        fill = accent.color(),
                    )
                }
            }
        }

        SectionHeader("Typography")
        ToggleRow(
            title = "Dot display font",
            description = "Use the dot-matrix font for clocks and big numbers",
            checked = s.dotDisplayFont,
            onCheckedChange = { on -> env.update { it.copy(dotDisplayFont = on) } },
        )
    }
}
