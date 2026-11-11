package com.dotos.feature.widgets

import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.dot.WidgetBatchA
import com.dotos.feature.widgets.dot.WidgetBatchB

/** Assembled from the widget batches. Each batch exposes a `providers` list. */
internal object DotWidgetCatalog {
    val providers: List<DotWidgetProvider> = WidgetBatchA.providers + WidgetBatchB.providers
}
