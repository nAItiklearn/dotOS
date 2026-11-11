package com.dotos.feature.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.DockStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.component.DotChoiceRow
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.ChevronIcon
import com.dotos.feature.settings.ui.DigitSelector
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.ToggleRow

/** Home screen layout (reference: preview, App Grid 4|5, App label, Search bar...). */
@Composable
internal fun LayoutScreen(env: SettingsEnv, s: LauncherSettings) {
    var advanced by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = "Home screen layout", onBack = env.back) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(172.dp))
        }

        SettingsRow(
            title = "App Grid",
            subtitle = "${s.gridColumns} columns",
            trailing = {
                DigitSelector(
                    options = listOf(4, 5),
                    selected = s.gridColumns,
                    onSelect = { cols -> env.update { it.copy(gridColumns = cols) } },
                )
            },
        )
        SettingsRow(
            title = "Advanced grid",
            subtitle = "Up to 7 columns and 8 rows",
            onClick = { advanced = !advanced },
            trailing = { ChevronIcon() },
        )
        AnimatedVisibility(visible = advanced) {
            Column {
                SettingsRow(
                    title = "Columns",
                    trailing = {
                        DotChoiceRow(
                            options = listOf(4, 5, 6, 7),
                            selected = s.gridColumns,
                            label = { it.toString() },
                            onSelect = { cols -> env.update { it.copy(gridColumns = cols) } },
                        )
                    },
                )
                SettingsRow(
                    title = "Rows",
                    subtitle = "${s.gridRows} rows",
                    trailing = {
                        DotChoiceRow(
                            options = listOf(4, 5, 6, 7, 8),
                            selected = s.gridRows,
                            label = { it.toString() },
                            onSelect = { rows -> env.update { it.copy(gridRows = rows) } },
                        )
                    },
                )
            }
        }

        ToggleRow(
            title = "App label",
            description = "Display app label on home screen",
            checked = s.showLabels,
            onCheckedChange = { on -> env.update { it.copy(showLabels = on) } },
        )
        ToggleRow(
            title = "Search bar",
            description = "Show the search pill under the dock",
            checked = s.searchBar,
            onCheckedChange = { on -> env.update { it.copy(searchBar = on) } },
        )

        SectionHeader("Dock")
        ToggleRow(
            title = "Dock",
            description = "Keep your favourite apps pinned at the bottom",
            checked = s.dockEnabled,
            onCheckedChange = { on -> env.update { it.copy(dockEnabled = on) } },
        )
        if (s.dockEnabled) {
            SettingsRow(
                title = "Dock icons",
                subtitle = "${s.dockCount} slots",
                trailing = {
                    DigitSelector(
                        options = listOf(4, 5, 6),
                        selected = s.dockCount,
                        onSelect = { n -> env.update { it.copy(dockCount = n) } },
                    )
                },
            )
            OptionSegmented(
                options = DockStyle.entries,
                selected = s.dockStyle,
                label = {
                    when (it) {
                        DockStyle.NONE -> "No background"
                        DockStyle.BLUR -> "Blur"
                        DockStyle.SOLID -> "Solid"
                    }
                },
                onSelect = { style -> env.update { it.copy(dockStyle = style) } },
            )
        }

        SectionHeader("Pages and behaviour")
        ToggleRow(
            title = "Page indicator",
            description = "Show dots for the current home page",
            checked = s.pageIndicator,
            onCheckedChange = { on -> env.update { it.copy(pageIndicator = on) } },
        )
        ToggleRow(
            title = "Infinite scroll",
            description = "Wrap around from the last page to the first",
            checked = s.infiniteScroll,
            onCheckedChange = { on -> env.update { it.copy(infiniteScroll = on) } },
        )
        ToggleRow(
            title = "Lock layout",
            description = "Prevent icons and widgets from being moved",
            checked = s.lockLayout,
            onCheckedChange = { on -> env.update { it.copy(lockLayout = on) } },
        )
        ToggleRow(
            title = "Allow rotation",
            description = "Let the home screen rotate with the device",
            checked = s.allowRotation,
            onCheckedChange = { on -> env.update { it.copy(allowRotation = on) } },
        )
        ToggleRow(
            title = "Status bar",
            description = "Show the status bar on the home screen",
            checked = s.showStatusBar,
            onCheckedChange = { on -> env.update { it.copy(showStatusBar = on) } },
        )
    }
}
