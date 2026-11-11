package com.dotos.feature.glance

import android.content.Context
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
import java.time.LocalDate

private val LifeSmall = DpSize(110.dp, 110.dp)
private val LifeWide = DpSize(250.dp, 110.dp)
private val LifeLarge = DpSize(250.dp, 250.dp)

/** Where the widget is in the year, resolved once per update. */
internal data class YearProgress(val year: Int, val dayOfYear: Int, val totalDays: Int) {
    val percent: Int get() = dayOfYear * 100 / totalDays

    companion object {
        fun now(): YearProgress {
            val today = LocalDate.now()
            return YearProgress(today.year, today.dayOfYear, today.lengthOfYear())
        }
    }
}

/**
 * Life in Dots: one dot per day of the year (elapsed lit, today in signal red, rest dim) with a
 * "32% OF 2026" header. Fully local; refreshed daily and whenever the date, clock or time zone changes.
 */
class LifeInDotsGlance : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(LifeSmall, LifeWide, LifeLarge))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val progress = YearProgress.now()
        provideContent { LifeContent(progress) }
    }
}

class LifeInDotsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeInDotsGlance()
}

/** Midnight rollover, manual date changes and time zone changes redraw the grid right away. */
class LifeInDotsRefreshReceiver : GlanceRefreshReceiver() {
    override suspend fun refresh(context: Context) {
        LifeInDotsGlance().updateAll(context)
    }
}

@Composable
private fun LifeContent(progress: YearProgress) {
    val context = LocalContext.current
    val size = LocalSize.current
    val bitmap = remember(size, progress) {
        DotBitmaps.yearProgress(
            widthDp = size.width.value,
            heightDp = size.height.value,
            scale = context.dotRenderScale(),
            dayOfYear = progress.dayOfYear,
            totalDays = progress.totalDays,
            ofLabel = "${context.getString(R.string.dotg_life_of)} ${progress.year}",
        )
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.dotg_plate)),
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = context.getString(R.string.dotg_life_cd, progress.percent, progress.year),
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
