package com.dotos.feature.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "DotGlance"

/** Bitmap density for the dot art: sharp enough on every phone, but never huge (RemoteViews memory budget). */
internal fun Context.dotRenderScale(): Float = resources.displayMetrics.density.coerceIn(2f, 3f)

/** Makes the widget open [intent]'s target when tapped, but only if something on the device can handle it. */
internal fun GlanceModifier.opens(context: Context, intent: Intent): GlanceModifier =
    if (intent.resolveActivity(context.packageManager) != null) {
        this.clickable(actionStartActivity(intent))
    } else {
        this
    }

/**
 * Base for the small receivers that refresh a widget on system broadcasts. Manifest receivers get ~10 s,
 * so the Glance update runs inside [goAsync] and always finishes the pending result.
 */
abstract class GlanceRefreshReceiver : BroadcastReceiver() {

    protected abstract suspend fun refresh(context: Context)

    final override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                refresh(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed for ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
