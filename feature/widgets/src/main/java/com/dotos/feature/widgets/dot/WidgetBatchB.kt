package com.dotos.feature.widgets.dot

import com.dotos.feature.widgets.api.DotWidgetProvider

/** Batch B: utility widgets (recorder, pedometer, away time, breathing, timer, notes, LED, games, media, habits). */
object WidgetBatchB {
    val providers: List<DotWidgetProvider> = listOf(
        RecorderWidget,
        PedometerWidget,
        AwayTimeWidget,
        BreathingWidget,
        TimerWidget,
        StickyNoteWidget,
        LedSignWidget,
        SnakeWidget,
        MusicWidget,
        CounterWidget,
        QuickCaptureWidget,
    )
}
