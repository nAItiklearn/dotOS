package com.dotos.feature.home.layout

import androidx.compose.runtime.Immutable
import com.dotos.core.data.db.Container
import com.dotos.core.data.db.ItemEntity
import com.dotos.core.data.db.ItemType
import com.dotos.core.launcher.AppInfo

/** UI model: an entity joined with its resolved app (null when the app is not installed). */
@Immutable
data class HomeItem(
    val entity: ItemEntity,
    val app: AppInfo? = null,
    val children: List<HomeItem> = emptyList(),
) {
    val id: Long get() = entity.id
    val isFolder: Boolean get() = entity.type == ItemType.FOLDER
    val isApp: Boolean get() = entity.type == ItemType.APP
    val config: Map<String, String> get() = WidgetConfigCodec.decode(entity.widgetConfig)
}

@Immutable
data class HomeLayout(
    val pages: List<List<HomeItem>>,
    val dock: List<HomeItem>,
) {
    companion object {
        val Empty = HomeLayout(emptyList(), emptyList())
    }
}

/** Joins raw entities with the live app list. Apps that are not installed are dropped. */
fun buildLayout(items: List<ItemEntity>, appsByKey: Map<String, AppInfo>, pageCount: Int): HomeLayout {
    val byContainer = items.groupBy { it.container }
    fun resolve(e: ItemEntity): HomeItem? = when (e.type) {
        ItemType.APP, ItemType.SHORTCUT -> LayoutRepository.componentKey(e)?.let { appsByKey[it] }?.let { HomeItem(e, it) }
        ItemType.FOLDER -> {
            val kids = byContainer[e.id].orEmpty().sortedBy { it.rank }.mapNotNull { child ->
                LayoutRepository.componentKey(child)?.let { appsByKey[it] }?.let { HomeItem(child, it) }
            }
            if (kids.isEmpty()) null else HomeItem(e, null, kids)
        }
        else -> HomeItem(e)
    }
    val home = byContainer[Container.HOME].orEmpty()
    val maxScreen = home.maxOfOrNull { it.screen } ?: 0
    val count = maxOf(pageCount, maxScreen + 1, 1)
    val pages = (0 until count).map { s -> home.filter { it.screen == s }.mapNotNull(::resolve) }
    val dock = byContainer[Container.DOCK].orEmpty().sortedBy { it.rank }.mapNotNull(::resolve)
    return HomeLayout(pages, dock)
}
