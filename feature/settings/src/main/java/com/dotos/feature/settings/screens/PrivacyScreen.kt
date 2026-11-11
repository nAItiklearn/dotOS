package com.dotos.feature.settings.screens

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.db.HiddenAppEntity
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotSwitch
import com.dotos.core.launcher.ProfileKind
import com.dotos.feature.icons.AppIcon
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.PillButton
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SearchField
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.bottomInsetPadding

/**
 * Screen-lock gate built on the framework BiometricPrompt (no androidx.biometric needed).
 * Biometrics or the device PIN / pattern / password unlock it. Devices without a secure lock skip
 * the prompt, because there is nothing to authenticate against.
 */
internal object DeviceAuth {
    fun isSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Returns a signal to cancel the prompt, or null when no prompt was needed / possible. */
    @Suppress("DEPRECATION")
    fun authenticate(context: Context, title: String, onResult: (Boolean) -> Unit): CancellationSignal? {
        if (!isSecure(context)) {
            onResult(true)
            return null
        }
        return try {
            val builder = BiometricPrompt.Builder(context).setTitle(title)
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                builder.setDeviceCredentialAllowed(true)
            }
            val signal = CancellationSignal()
            builder.build().authenticate(
                signal,
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        onResult(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onResult(false)
                    }
                },
            )
            signal
        } catch (e: Exception) {
            onResult(false)
            null
        }
    }
}

/** Hidden apps: list of every app with a hide switch, behind the screen lock. */
@Composable
internal fun PrivacyScreen(env: SettingsEnv, s: LauncherSettings) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    DisposableEffect(attempt) {
        val signal = if (!unlocked) {
            DeviceAuth.authenticate(context, "Unlock hidden apps") { ok -> if (ok) unlocked = true }
        } else {
            null
        }
        onDispose { signal?.cancel() }
    }

    SettingsScaffold(title = "Hidden apps & privacy", onBack = env.back, scroll = false) {
        if (unlocked) {
            HiddenAppsList(env)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Hidden apps are locked",
                    style = DotTheme.type.title,
                    color = DotTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Use your fingerprint or screen lock to see and change the list.",
                    style = DotTheme.type.body,
                    color = DotTheme.colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
                PillButton(text = "Unlock", onClick = { attempt += 1 })
            }
        }
    }
}

@Composable
private fun HiddenHint() {
    Caption("Hidden apps disappear from the home screen and the drawer. Search finds them only while this list is unlocked.")
}

@Composable
private fun HiddenAppsList(env: SettingsEnv) {
    val apps by env.deps.apps.apps.collectAsStateWithLifecycle()
    val dao = remember(env.deps) { env.deps.database.appMeta() }
    val hidden by remember(dao) { dao.observeHidden() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val hiddenKeys = remember(hidden) { hidden.map { it.componentKey }.toSet() }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    }

    HiddenHint()
    SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = "Search apps",
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
        items(filtered, key = { it.key }) { app ->
            val isHidden = app.key in hiddenKeys
            val setHidden: (Boolean) -> Unit = { hide ->
                env.launchIO {
                    if (hide) dao.hide(HiddenAppEntity(app.key)) else dao.unhide(app.key)
                }
            }
            SettingsRow(
                title = app.label,
                subtitle = when (app.profile) {
                    ProfileKind.WORK -> "Work profile"
                    ProfileKind.PRIVATE -> "Private Space"
                    ProfileKind.MAIN, ProfileKind.OTHER -> null
                },
                onClick = { setHidden(!isHidden) },
                leading = { AppIcon(app, size = 44.dp) },
                trailing = { DotSwitch(checked = isHidden, onCheckedChange = setHidden) },
            )
        }
    }
}
