package com.dotos.feature.home.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Request code for the host-routed configure fallback; the result is not observable from Compose. */
private const val REQUEST_CONFIGURE = 0x0D08

/**
 * Returns a function that starts the full add flow for a provider:
 * allocate id, bind (silently when the system already trusts this launcher, otherwise through the system
 * ACTION_APPWIDGET_BIND consent screen), then run the provider's configure activity if it has one.
 *
 * [onBound] fires once the widget is ready to be placed. On cancel or any failure the allocated id is
 * deleted and [onFailed] fires. Starting a new flow while one is pending silently discards the old one.
 */
@Composable
fun rememberAppWidgetBinder(
    controller: AppWidgetController,
    onBound: (appWidgetId: Int, info: AppWidgetProviderInfo) -> Unit,
    onFailed: () -> Unit,
): (AppWidgetProviderInfo) -> Unit {
    val context = LocalContext.current
    val flow = remember(controller) { AppWidgetBindFlow(controller) }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        flow.onBindResult(result.resultCode == Activity.RESULT_OK)
    }
    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        flow.onConfigureResult(result.resultCode == Activity.RESULT_OK)
    }

    SideEffect {
        flow.activity = context.findHostActivity()
        flow.launchBind = { intent -> bindLauncher.launch(intent) }
        flow.launchConfigure = { intent -> configureLauncher.launch(intent) }
        flow.onBound = onBound
        flow.onFailed = onFailed
    }
    DisposableEffect(flow) {
        onDispose { flow.cancelPending() }
    }

    return remember(flow) { { info: AppWidgetProviderInfo -> flow.begin(info) } }
}

/**
 * State machine behind [rememberAppWidgetBinder]. Everything runs on the main thread; at most one add
 * flow is pending at a time.
 */
internal class AppWidgetBindFlow(private val controller: AppWidgetController) {
    var activity: Activity? = null
    var launchBind: (Intent) -> Unit = {}
    var launchConfigure: (Intent) -> Unit = {}
    var onBound: (Int, AppWidgetProviderInfo) -> Unit = { _, _ -> }
    var onFailed: () -> Unit = {}

    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingInfo: AppWidgetProviderInfo? = null

    fun begin(info: AppWidgetProviderInfo) {
        cancelPending()
        val id = runCatching { controller.allocate() }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            onFailed()
            return
        }
        pendingId = id
        pendingInfo = info

        val boundSilently = runCatching {
            controller.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        }.getOrDefault(false)
        if (boundSilently) configureOrFinish() else requestBindConsent(id, info)
    }

    /** Drops the pending flow (if any) and frees its id without notifying anyone. */
    fun cancelPending() {
        val id = pendingId
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingInfo = null
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) controller.delete(id)
    }

    fun onBindResult(ok: Boolean) {
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (ok) configureOrFinish() else fail()
    }

    fun onConfigureResult(ok: Boolean) {
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (ok) succeed() else fail()
    }

    private fun requestBindConsent(id: Int, info: AppWidgetProviderInfo) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        val launched = try {
            launchBind(intent)
            true
        } catch (e: RuntimeException) {
            false
        }
        if (!launched) fail()
    }

    private fun configureOrFinish() {
        val info = pendingInfo
        val id = pendingId
        if (info == null || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            fail()
            return
        }
        val configure = info.configure
        if (configure == null) {
            succeed()
            return
        }
        // Work-profile providers cannot be launched by an explicit intent from the personal profile.
        if (info.profile != Process.myUserHandle()) {
            configureThroughHost(id)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        intent.component = configure
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        try {
            launchConfigure(intent)
        } catch (e: RuntimeException) {
            // SecurityException for non-exported configure activities (the usual case), or
            // ActivityNotFoundException. The system can still start them on our behalf.
            configureThroughHost(id)
        }
    }

    /** Result of this path is delivered to Activity.onActivityResult, so it is treated as success. */
    private fun configureThroughHost(id: Int) {
        val host = activity
        if (host == null) {
            fail()
            return
        }
        val started = runCatching {
            controller.host.startAppWidgetConfigureActivityForResult(host, id, 0, REQUEST_CONFIGURE, null)
        }.isSuccess
        if (started) succeed() else fail()
    }

    private fun succeed() {
        val id = pendingId
        val info = pendingInfo
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingInfo = null
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || info == null) {
            onFailed()
        } else {
            onBound(id, info)
        }
    }

    private fun fail() {
        val id = pendingId
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingInfo = null
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) controller.delete(id)
        onFailed()
    }
}

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
