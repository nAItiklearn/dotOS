package com.dotos.feature.settings.screens

import androidx.compose.runtime.Composable
import com.dotos.core.data.settings.FolderStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ChoiceRows
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsScaffold

@Composable
internal fun FoldersScreen(env: SettingsEnv, s: LauncherSettings) {
    SettingsScaffold(title = "Folders", onBack = env.back) {
        SectionHeader("Folder style")
        ChoiceRows(
            options = FolderStyle.entries,
            selected = s.folderStyle,
            title = {
                when (it) {
                    FolderStyle.POPUP -> "Popup"
                    FolderStyle.FULLSCREEN -> "Full screen"
                }
            },
            description = {
                when (it) {
                    FolderStyle.POPUP -> "A rounded card opens over the home screen"
                    FolderStyle.FULLSCREEN -> "The folder fills the whole screen"
                }
            },
            onSelect = { style -> env.update { it.copy(folderStyle = style) } },
        )
        Caption("Drag an app onto another app to make a folder. Drag it out of the popup to take it back out.")
    }
}
