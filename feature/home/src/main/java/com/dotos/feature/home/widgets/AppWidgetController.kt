package com.dotos.feature.home.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.dotos.feature.widgets.api.WidgetSize
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Owns the [AppWidgetHost] (host id [HOST_ID]) and the helpers every third-party widget flow needs.
 *
 * Call [start] from the activity's onStart and [stop] from onStop. Every method is defensive: widget
 * providers are third-party code and the platform services throw on a surprising number of OEM builds,
 * so nothing here is allowed to take the home screen down.
 */
class AppWidgetController(context: Context) {
    private val appContext: Context = context.applicationContext

    val host: AppWidgetHost = AppWidgetHost(appContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)

    private val appLabels = ConcurrentHashMap<String, String>()

    /**
     * Decoded picker images (widget previews and app icons), shared across picker openings. Bounded by
     * decoded size so a phone with hundreds of widget providers cannot exhaust the heap.
     */
    internal val imageCache: LruCache<String, ImageBitmap> =
        object : LruCache<String, ImageBitmap>(IMAGE_CACHE_KB) {
            override fun sizeOf(key: String, value: ImageBitmap): Int =
                ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
        }

    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** All providers for all profiles visible to the launcher, sorted by app label then widget label. */
    fun providers(): List<AppWidgetProviderInfo> {
        val found = ArrayList<AppWidgetProviderInfo>()
        val users = runCatching { appContext.getSystemService(UserManager::class.java)?.userProfiles }
            .getOrNull()
            .orEmpty()
        for (user in users) {
            val list = runCatching { manager.getInstalledProvidersForProfile(user) }.getOrNull()
            if (list != null) found.addAll(list)
        }
        if (found.isEmpty()) {
            val list = runCatching { manager.installedProviders }.getOrNull()
            if (list != null) found.addAll(list)
        }
        return found
            .distinctBy { it.provider.flattenToString() + "|" + it.profile }
            .map { SortableProvider(appLabel(it).lowercase(), widgetLabel(it).lowercase(), it) }
            .sortedWith(compareBy<SortableProvider>({ it.appKey }, { it.widgetKey }))
            .map { it.info }
    }

    /** Label of the app that ships [info], cached per package. Safe to call from any thread. */
    internal fun appLabel(info: AppWidgetProviderInfo): String {
        val pkg = info.provider.packageName
        val cached = appLabels[pkg]
        if (cached != null) return cached
        val loaded = runCatching { appContext.packageManager.getApplicationInfo(pkg, 0).loadLabel(appContext.packageManager).toString() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: pkg
        appLabels[pkg] = loaded
        return loaded
    }

    /** Label of the widget itself, falling back to the receiver's short class name. */
    internal fun widgetLabel(info: AppWidgetProviderInfo): String {
        val loaded = runCatching { info.loadLabel(appContext.packageManager) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return loaded ?: info.provider.shortClassName
    }

    private class SortableProvider(val appKey: String, val widgetKey: String, val info: AppWidgetProviderInfo)

    companion object {
        const val HOST_ID: Int = 0x0D07
        private const val IMAGE_CACHE_KB = 16 * 1024
    }
}

private const val MAX_ROWS = 8

/** Fraction of a cell pitch assumed to be gutter when deciding how many cells a minimum size needs. */
private const val GUTTER_FRACTION = 0.22f

/**
 * Grid span for [info] given the cell size in dp. On Android 12+ the provider's own
 * `targetCellWidth/Height` win; otherwise (and for providers that leave them at 0) the span is the
 * smallest number of cells whose combined size, minus gutters, covers `minWidth`/`minHeight`.
 *
 * `minWidth`/`minHeight` are delivered in pixels by the platform, so they are converted to dp with the
 * system display density (this function deliberately takes no Context).
 */
fun spanFor(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, maxColumns: Int): WidgetSize {
    var spanX = 0
    var spanY = 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        spanX = info.targetCellWidth
        spanY = info.targetCellHeight
    }
    if (spanX <= 0 || spanY <= 0) {
        val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(0.5f)
        if (spanX <= 0) spanX = cellsFor(info.minWidth / density, cellWidthDp)
        if (spanY <= 0) spanY = cellsFor(info.minHeight / density, cellHeightDp)
    }
    return WidgetSize(spanX.coerceIn(1, maxColumns.coerceAtLeast(1)), spanY.coerceIn(1, MAX_ROWS))
}

private fun cellsFor(sizeDp: Float, cellDp: Float): Int {
    if (cellDp <= 0f) return 1
    return ceil((sizeDp + GUTTER_FRACTION * cellDp) / cellDp - 0.02f).toInt().coerceAtLeast(1)
}
