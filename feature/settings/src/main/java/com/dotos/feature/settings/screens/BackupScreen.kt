package com.dotos.feature.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotPalette
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotCard
import com.dotos.core.designsystem.component.DotLoading
import com.dotos.feature.settings.backup.BackupManager
import com.dotos.feature.settings.backup.BackupResult
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ConfirmDialog
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export / import everything as one `.dotbackup` zip through the Storage Access Framework. */
@Composable
internal fun BackupScreen(env: SettingsEnv, s: LauncherSettings) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BackupResult?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true
            result = null
            // The controller scope outlives this screen, so leaving the page never aborts a write halfway.
            env.controller.scope.launch {
                result = BackupManager.export(context, env.deps, uri)
                busy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    SettingsScaffold(title = "Backup & restore", onBack = env.back) {
        Caption(
            "One .dotbackup file holds your settings, home layout, hidden apps, per-app icons and saved wallpaper presets. " +
                "Photo wallpapers and third-party widgets are not included.",
            Modifier.padding(top = 4.dp),
        )

        SectionHeader("Backup")
        SettingsRow(
            title = "Export backup",
            subtitle = "Save everything to a .dotbackup file",
            onClick = if (busy) {
                null
            } else {
                {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("dotos-$stamp.dotbackup")
                }
            },
            trailing = if (busy) {
                { DotLoading() }
            } else {
                null
            },
        )
        SettingsRow(
            title = "Restore backup",
            subtitle = "Replace everything with the contents of a .dotbackup file",
            onClick = if (busy) null else ({ importLauncher.launch(arrayOf("*/*")) }),
        )

        val shown = result
        if (shown != null) {
            DotCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                radius = 20.dp,
            ) {
                Text(
                    shown.message,
                    style = DotTheme.type.body,
                    color = if (shown.ok) DotTheme.colors.onSurface else DotPalette.SignalRed,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    val restoreUri = pendingRestore
    if (restoreUri != null) {
        ConfirmDialog(
            title = "Restore this backup?",
            message = "Your current settings, home layout, hidden apps and per-app icons are replaced.",
            confirmLabel = "Restore",
            onConfirm = {
                pendingRestore = null
                busy = true
                result = null
                env.controller.scope.launch {
                    result = BackupManager.restore(context, env.deps, restoreUri)
                    busy = false
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
}
