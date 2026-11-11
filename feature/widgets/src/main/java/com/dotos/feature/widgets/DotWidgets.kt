package com.dotos.feature.widgets

import com.dotos.feature.widgets.api.DotWidgetProvider

/** Registry of every built-in widget, in picker order. Providers live in the dot package. */
object DotWidgets {
    val all: List<DotWidgetProvider> by lazy {
        val list = DotWidgetCatalog.providers
        check(list.map { it.kind }.toSet().size == list.size) { "Duplicate widget kind" }
        list
    }

    operator fun get(kind: String?): DotWidgetProvider? = all.firstOrNull { it.kind == kind }
}
