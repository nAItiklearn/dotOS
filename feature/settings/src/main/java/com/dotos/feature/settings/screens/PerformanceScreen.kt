package com.dotos.feature.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.ToggleRow

@Composable
internal fun PerformanceScreen(env: SettingsEnv, s: LauncherSettings) {
    var cleared by remember { mutableStateOf(false) }
    SettingsScaffold(title = "Performance", onBack = env.back) {
        SectionHeader("Motion")
        ToggleRow(
            title = "Reduce motion",
            description = "Replace springs and slides with simple fades",
            checked = s.reduceMotion,
            onCheckedChange = { on -> env.update { it.copy(reduceMotion = on) } },
        )
        ToggleRow(
            title = "Disable blur",
            description = "Skip blur effects for smoother scrolling on older phones",
            checked = s.disableBlur,
            onCheckedChange = { on -> env.update { it.copy(disableBlur = on) } },
        )

        SectionHeader("Feel")
        ToggleRow(
            title = "Haptic feedback",
            description = "Light vibration when you tap, snap or toggle",
            checked = s.haptics,
            onCheckedChange = { on -> env.update { it.copy(haptics = on) } },
        )

        SectionHeader("Maintenance")
        SettingsRow(
            title = "Clear icon cache",
            subtitle = if (cleared) "Cleared. Icons rebuild as you scroll." else "Rebuild every icon from scratch",
            onClick = {
                cleared = true
                env.launchIO { env.deps.icons.clearDiskCache() }
            },
        )
    }
}
