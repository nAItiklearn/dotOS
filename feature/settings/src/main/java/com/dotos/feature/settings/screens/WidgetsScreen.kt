package com.dotos.feature.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.widgets.DotWidgets
import com.dotos.feature.widgets.api.DotWidgetProvider

/** Read-only catalogue of the bundled widgets; adding them happens from the home screen. */
@Composable
internal fun WidgetsScreen(env: SettingsEnv, s: LauncherSettings) {
    val widgets: List<DotWidgetProvider> = remember {
        try {
            DotWidgets.all
        } catch (e: Exception) {
            emptyList()
        }
    }
    SettingsScaffold(title = "Widgets", onBack = env.back) {
        Caption("Long-press an empty spot on the home screen and choose Widgets to add one. Every widget works offline.")
        SectionHeader("Bundled widgets")
        if (widgets.isEmpty()) {
            Caption("No bundled widgets are available in this build.")
        }
        widgets.forEach { provider ->
            val sizes = provider.sizes.joinToString("  ") { it.toString() }
            SettingsRow(
                title = provider.title,
                subtitle = if (sizes.isEmpty()) provider.description else "$sizes · ${provider.description}",
            )
        }
    }
}
