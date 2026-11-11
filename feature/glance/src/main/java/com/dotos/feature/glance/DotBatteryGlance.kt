package com.dotos.feature.glance

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.dotos.feature.glance.render.DotBitmaps

private val BatteryCompact = DpSize(110.dp, 40.dp)
private val BatteryCompactWide = DpSize(250.dp, 40.dp)
private val BatteryRegular = DpSize(110.dp, 110.dp)
private val BatteryRegularWide = DpSize(250.dp, 110.dp)

/** Snapshot of the phone battery used to render one widget update. */
internal data class BatteryStatus(val level: Int, val charging: Boolean) {
    companion object {
        fun read(context: Context): BatteryStatus {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            return BatteryStatus(
                level = level.coerceIn(0, 100),
                charging = manager?.isCharging == true,
            )
        }
    }
}

/**
 * Dot Battery: a dot-matrix percentage and a dot bar, rendered to a bitmap so the look is identical on
 * every launcher. 2x1 shows percentage + 2x5 bar side by side; 2x2 and larger stack caption, percentage and bar.
 * Tapping opens the system battery usage screen.
 */
class DotBatteryGlance : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(BatteryCompact, BatteryCompactWide, BatteryRegular, BatteryRegularWide),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val status = BatteryStatus.read(context)
        provideContent { BatteryContent(status) }
    }
}

class DotBatteryReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotBatteryGlance()
}

/** Plug / unplug changes the widget immediately instead of waiting for the 30 minute system update. */
class BatteryRefreshReceiver : GlanceRefreshReceiver() {
    override suspend fun refresh(context: Context) {
        DotBatteryGlance().updateAll(context)
    }
}

@Composable
private fun BatteryContent(status: BatteryStatus) {
    val context = LocalContext.current
    val size = LocalSize.current
    val caption = context.getString(
        if (status.charging) R.string.dotg_caption_charging else R.string.dotg_caption_battery,
    )
    val bitmap = remember(size, status, caption) {
        DotBitmaps.battery(
            widthDp = size.width.value,
            heightDp = size.height.value,
            scale = context.dotRenderScale(),
            level = status.level,
            charging = status.charging,
            caption = caption,
        )
    }
    val description = context.getString(
        if (status.charging) R.string.dotg_battery_charging_cd else R.string.dotg_battery_cd,
        status.level,
    )
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.dotg_plate))
            .opens(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)),
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
