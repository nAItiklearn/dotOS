package com.dotos.launcher

import com.dotos.core.data.db.Container
import com.dotos.core.data.db.ItemEntity
import com.dotos.core.data.db.ItemType
import com.dotos.feature.widgets.DotWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Used only by the automated screenshot workflow (intent extra `dotos.demoWidgets`). */
internal object DemoLayout {
    suspend fun placeAllWidgets(g: AppGraph) {
        g.layout.items.filterNotNull().first()
        delay(4000) // let the first-run seed land first
        val settings = g.settings.current()
        val cols = settings.gridColumns
        val rows = settings.gridRows
        val placed = g.layout.items.value.orEmpty().toMutableList()
        var page = 1
        for (provider in DotWidgets.all) {
            val size = provider.sizes.filter { it.spanY <= 2 && it.spanX <= cols }.maxByOrNull { it.spanX * it.spanY } ?: continue
            var pos: Pair<Int, Int>? = null
            while (pos == null) {
                val grid = g.layout.occupancy(placed, page, cols, rows)
                pos = g.layout.nearestFree(grid, size.spanX, size.spanY)
                if (pos == null) page++
            }
            val face = provider.faces.first().id
            g.layout.addDotWidget(provider.kind, face, page, pos.first, pos.second, size.spanX, size.spanY)
            placed += ItemEntity(
                type = ItemType.DOT_WIDGET, container = Container.HOME, screen = page,
                cellX = pos.first, cellY = pos.second, spanX = size.spanX, spanY = size.spanY,
            )
        }
        g.settings.update { it.copy(pageCount = page + 1) }
    }
}
