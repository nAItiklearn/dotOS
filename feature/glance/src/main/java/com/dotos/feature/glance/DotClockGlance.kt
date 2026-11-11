package com.dotos.feature.glance

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize

private val ClockSmall = DpSize(110.dp, 110.dp)
private val ClockWide = DpSize(250.dp, 110.dp)

/** Layouts at or above this width use the single-line "HH:mm" face. */
private val WideMinWidth = 200.dp

/**
 * Dot Clock. Glance cannot tick once a minute, so the face is a plain RemoteViews layout (TextClock in Doto)
 * embedded with [AndroidRemoteViews]: the host's own TextClock updates it, the widget never wakes the app.
 * 2x2 stacks hours over minutes; 4x2 and larger show one line.
 */
class DotClockGlance : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ClockSmall, ClockWide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ClockContent() }
    }
}

class DotClockReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotClockGlance()
}

@Composable
private fun ClockContent() {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WideMinWidth
    val views = remember(wide) { buildClockViews(context, wide) }
    AndroidRemoteViews(views, GlanceModifier.fillMaxSize())
}

private fun buildClockViews(context: Context, wide: Boolean): RemoteViews {
    val views = RemoteViews(context.packageName, if (wide) R.layout.dotg_clock_wide else R.layout.dotg_clock_small)
    val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
    if (alarms.resolveActivity(context.packageManager) != null) {
        val pending = PendingIntent.getActivity(
            context,
            0,
            alarms,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.dotg_clock_root, pending)
    }
    return views
}
