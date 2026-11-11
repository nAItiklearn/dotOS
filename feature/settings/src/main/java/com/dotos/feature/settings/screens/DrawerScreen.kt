package com.dotos.feature.settings.screens

import androidx.compose.runtime.Composable
import com.dotos.core.data.settings.DrawerLayout
import com.dotos.core.data.settings.DrawerSort
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.component.DotChoiceRow
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.ChoiceRows
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.SliderRow
import com.dotos.feature.settings.ui.ToggleRow
import com.dotos.feature.settings.ui.percentText
import kotlin.math.roundToInt

@Composable
internal fun DrawerScreen(env: SettingsEnv, s: LauncherSettings) {
    SettingsScaffold(title = "App drawer", onBack = env.back) {
        SectionHeader("Layout")
        OptionSegmented(
            options = DrawerLayout.entries,
            selected = s.drawerLayout,
            label = {
                when (it) {
                    DrawerLayout.GRID -> "Grid"
                    DrawerLayout.LIST -> "List"
                }
            },
            onSelect = { layout -> env.update { it.copy(drawerLayout = layout) } },
        )
        if (s.drawerLayout == DrawerLayout.GRID) {
            SettingsRow(
                title = "Columns",
                subtitle = "${s.drawerColumns} columns in the grid",
                trailing = {
                    DotChoiceRow(
                        options = listOf(3, 4, 5, 6),
                        selected = s.drawerColumns,
                        label = { it.toString() },
                        onSelect = { cols -> env.update { it.copy(drawerColumns = cols) } },
                    )
                },
            )
        }
        ToggleRow(
            title = "App labels",
            description = "Show app names in the drawer",
            checked = s.drawerLabels,
            onCheckedChange = { on -> env.update { it.copy(drawerLabels = on) } },
        )

        SectionHeader("Sorting")
        ChoiceRows(
            options = DrawerSort.entries,
            selected = s.drawerSort,
            title = {
                when (it) {
                    DrawerSort.ALPHABETICAL -> "A to Z"
                    DrawerSort.MOST_USED -> "Most used"
                    DrawerSort.RECENT -> "Recently installed"
                }
            },
            description = {
                when (it) {
                    DrawerSort.ALPHABETICAL -> "Alphabetical order by app name"
                    DrawerSort.MOST_USED -> "Apps you open most come first"
                    DrawerSort.RECENT -> "Newest installs come first"
                }
            },
            onSelect = { sort -> env.update { it.copy(drawerSort = sort) } },
        )

        SectionHeader("Search and scrolling")
        ToggleRow(
            title = "Open keyboard automatically",
            description = "Start typing as soon as the drawer opens",
            checked = s.autoKeyboard,
            onCheckedChange = { on -> env.update { it.copy(autoKeyboard = on) } },
        )
        ToggleRow(
            title = "Fast scroller",
            description = "Alphabet scroller on the edge of the drawer",
            checked = s.fastScroller,
            onCheckedChange = { on -> env.update { it.copy(fastScroller = on) } },
        )

        SectionHeader("Background")
        SliderRow(
            title = "Blur",
            value = s.drawerBlur,
            onValueChange = { v -> env.update { it.copy(drawerBlur = v) } },
            valueRange = 0f..48f,
            valueText = { "${it.roundToInt()} dp" },
            description = "How much the wallpaper blurs behind the drawer",
            enabled = !s.disableBlur,
        )
        SliderRow(
            title = "Dim",
            value = s.drawerDim,
            onValueChange = { v -> env.update { it.copy(drawerDim = v) } },
            valueRange = 0f..0.9f,
            valueText = ::percentText,
            description = "How dark the wallpaper gets behind the drawer",
        )
    }
}
