package com.dotos.feature.widgets.dot

import com.dotos.feature.widgets.api.DotWidgetProvider

/** Batch A: everyday glanceables. Order here is the picker order. */
object WidgetBatchA {
    val providers: List<DotWidgetProvider> = listOf(
        ClockWidget,
        DateWidget,
        BatteryWidget,
        CompassWidget,
        LifeInDotsWidget,
        QuickSettingsWidget,
        SearchWidget,
        PhotoWidget,
    )
}
