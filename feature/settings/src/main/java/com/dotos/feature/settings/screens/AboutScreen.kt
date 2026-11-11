package com.dotos.feature.settings.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotPalette
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.settings.R
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ConfirmDialog
import com.dotos.feature.settings.ui.NavRow
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val SOURCE_URL = "https://github.com/nAItiklearn/dotOS"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No browser installed: the URL is also shown as plain text.
    }
}

@Composable
internal fun AboutScreen(env: SettingsEnv, s: LauncherSettings) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    SettingsScaffold(title = "About", onBack = env.back) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("DotOS", style = DotTheme.type.display, color = DotTheme.colors.onSurface)
        }
        Text(
            "Launcher ${env.deps.versionName}",
            style = DotTheme.type.mono,
            color = DotTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        Caption(
            "Unofficial fan-made project, not affiliated with Nothing Technology Limited.",
            Modifier.fillMaxWidth(),
        )

        SectionHeader("Project")
        SettingsRow(
            title = "Source code",
            subtitle = SOURCE_URL,
            onClick = { openUrl(context, SOURCE_URL) },
        )
        NavRow(
            title = "Open-source licenses",
            subtitle = "Fonts and libraries bundled in DotOS",
            onClick = { env.navigate(Routes.LICENSES) },
        )
        Caption("No account, no ads, no analytics. DotOS has no internet permission and sends nothing anywhere.")

        SectionHeader("Reset")
        SettingsRow(
            title = "Reset everything",
            subtitle = "Restore default settings and clear the home layout, hidden apps and icon overrides",
            onClick = { confirmReset = true },
            titleColor = DotPalette.SignalRed,
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset everything?",
            message = "Your settings, home layout, hidden apps and per-app icons are erased. This cannot be undone. " +
                "Backups you exported are not touched.",
            confirmLabel = "Reset everything",
            onConfirm = {
                confirmReset = false
                resetEverything(env, context.applicationContext)
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** Defaults for settings, empty home (items), hidden apps and icon overrides. Saved wallpaper presets are kept. */
internal fun resetEverything(env: SettingsEnv, appContext: Context) {
    env.launchIO {
        val db = env.deps.database
        db.items().clear()
        db.appMeta().clearHidden()
        db.appMeta().clearOverrides()
        env.deps.icons.setOverrides(emptyMap())
        try {
            File(appContext.filesDir, "wallpapers").deleteRecursively()
        } catch (e: Exception) {
            // Best effort: a leftover photo copy is harmless.
        }
        env.update { LauncherSettings(firstRunDone = true) }
    }
}

private class LicenseItem(val name: String, val copyright: String, val license: String)

private val Licenses = listOf(
    LicenseItem(
        "Doto",
        "Copyright 2024 The Doto Project Authors (https://github.com/oliverlalan/Doto)",
        "SIL Open Font License 1.1",
    ),
    LicenseItem(
        "Space Grotesk",
        "Copyright 2020 The Space Grotesk Project Authors (https://github.com/floriankarsten/space-grotesk)",
        "SIL Open Font License 1.1",
    ),
    LicenseItem(
        "Space Mono",
        "Copyright 2016 The Space Mono Project Authors (https://github.com/googlefonts/spacemono)",
        "SIL Open Font License 1.1",
    ),
    LicenseItem("AndroidX (Compose, Room, DataStore, Lifecycle, Activity, Core)", "Copyright The Android Open Source Project", "Apache License 2.0"),
    LicenseItem("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors", "Apache License 2.0"),
)

@Composable
internal fun LicensesScreen(env: SettingsEnv, s: LauncherSettings) {
    val context = LocalContext.current
    val oflText by produceState("") {
        value = withContext(Dispatchers.IO) {
            try {
                context.resources.openRawResource(R.raw.ofl).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
        }
    }
    SettingsScaffold(title = "Open-source licenses", onBack = env.back) {
        SectionHeader("Fonts")
        Licenses.take(3).forEach { item ->
            SettingsRow(title = item.name, subtitle = "${item.copyright}\n${item.license}")
        }
        SectionHeader("Libraries")
        Licenses.drop(3).forEach { item ->
            SettingsRow(title = item.name, subtitle = "${item.copyright}\n${item.license}")
        }
        Caption("Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0")
        SectionHeader("SIL Open Font License 1.1")
        Caption("The licence text below applies to Doto, Space Grotesk and Space Mono. Copyright notices are listed above.")
        Text(
            oflText,
            style = DotTheme.type.mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = DotTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
