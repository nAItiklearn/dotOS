package com.dotos.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.home.layout.HomeItem

enum class DragSource { HOME, DOCK, FOLDER, DRAWER, LIBRARY }

/** What is being dragged. [item] is null for apps coming from the drawer / library (not yet placed). */
@Immutable
data class DragSession(
    val item: HomeItem?,
    val app: AppInfo?,
    val source: DragSource,
    /** Ghost size in px. */
    val width: Float,
    val height: Float,
    /** Pointer position inside the ghost when the drag began. */
    val grab: Offset,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

/** Where a long-press menu points (root coordinates). */
@Immutable
data class MenuTarget(
    val item: HomeItem?,
    val app: AppInfo?,
    val source: DragSource,
    val anchor: Rect,
)

enum class DropZone { NONE, REMOVE, INFO, UNINSTALL }

/** Shared, snapshot-backed drag/menu state for the whole home surface. */
@Stable
class HomeDragState {
    var session by mutableStateOf<DragSession?>(null)
    /** Pointer in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
    /** Pending long-press that becomes a drag once the finger moves far enough. */
    var pending by mutableStateOf<DragSession?>(null)
    var pendingTravel = 0f
    var menu by mutableStateOf<MenuTarget?>(null)
    var hoverZone by mutableStateOf(DropZone.NONE)

    val isDragging: Boolean get() = session != null

    fun ghostTopLeft(): Offset {
        val s = session ?: return Offset.Zero
        return pointer - s.grab
    }

    fun clear() {
        session = null
        pending = null
        pendingTravel = 0f
        hoverZone = DropZone.NONE
    }
}
