package com.dotos.core.data.settings

import kotlinx.serialization.Serializable

/**
 * Every user-facing setting. Stored as one JSON document so adding a field never needs a
 * migration (unknown keys are ignored, missing keys take the defaults below) and backup/restore
 * is a straight copy. Defaults are tuned to look like a fresh Nothing-style phone.
 */
@Serializable
data class LauncherSettings(
    val version: Int = CURRENT_VERSION,
    val firstRunDone: Boolean = false,

    // Theme
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.RED,
    val dotDisplayFont: Boolean = true,

    // Home
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val pageCount: Int = 2,
    val defaultPage: Int = 0,
    val showLabels: Boolean = false,
    val dockEnabled: Boolean = true,
    val dockCount: Int = 5,
    val dockStyle: DockStyle = DockStyle.NONE,
    val searchBar: Boolean = true,
    val infiniteScroll: Boolean = false,
    val lockLayout: Boolean = false,
    val allowRotation: Boolean = false,
    val showStatusBar: Boolean = true,
    val pageIndicator: Boolean = true,

    // Icons
    val iconStyle: IconStyle = IconStyle.LINE,
    val plateShape: PlateShape = PlateShape.CIRCLE,
    val plateColor: PlateColor = PlateColor.DARK,
    val iconScale: Float = 1f,
    val glyphTint: GlyphTint = GlyphTint.WHITE,
    val dotDensity: Int = 18,
    val dotShape: DotShape = DotShape.ROUND,
    val iconPackPackage: String? = null,

    // Drawer
    val drawerLayout: DrawerLayout = DrawerLayout.GRID,
    val drawerColumns: Int = 4,
    val drawerSort: DrawerSort = DrawerSort.ALPHABETICAL,
    val drawerLabels: Boolean = true,
    val autoKeyboard: Boolean = false,
    val fastScroller: Boolean = true,
    val drawerBlur: Float = 24f,
    val drawerDim: Float = 0.55f,

    // Wallpaper
    val wallpaper: WallpaperConfig = WallpaperConfig(),
    val useSystemWallpaper: Boolean = false,
    val homeBlur: Float = 0f,
    val homeDim: Float = 0f,

    // Folders
    val folderStyle: FolderStyle = FolderStyle.POPUP,

    // Gestures
    val gestures: GestureConfig = GestureConfig(),

    // Badges
    val badgeStyle: BadgeStyle = BadgeStyle.DOT,

    // Performance / feel
    val reduceMotion: Boolean = false,
    val disableBlur: Boolean = false,
    val haptics: Boolean = true,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class WallpaperConfig(
    val id: String = "atmosphere",
    val presetId: String = "paper",
    /** Numeric params keyed by param id. Missing keys use the preset / wallpaper default. */
    val params: Map<String, Float> = emptyMap(),
    /** ARGB colours keyed by colour slot id. Missing keys use the preset colour. */
    val colors: Map<String, Long> = emptyMap(),
    val animation: AnimationSpeed = AnimationSpeed.NORMAL,
    val animateOnlyWhileCharging: Boolean = false,
    val pauseInBatterySaver: Boolean = true,
    val parallax: Float = 0.4f,
    val scrollParallax: Boolean = true,
    val glass: Boolean = false,
    val glassStripes: Float = 22f,
    /** Absolute file path for a user-picked photo; used when id == "image". */
    val imagePath: String? = null,
)

@Serializable
data class GestureConfig(
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.NONE,
    val longPressEmpty: GestureAction = GestureAction.EDIT_MODE,
    val homePress: GestureAction = GestureAction.DEFAULT_PAGE,
)

@Serializable enum class ThemeMode { DARK, LIGHT, AUTO }

@Serializable enum class Accent { RED, WHITE, GREEN, BLUE, AMBER, NONE }

@Serializable enum class DockStyle { NONE, BLUR, SOLID }

@Serializable enum class IconStyle { DOT, LINE, ORIGINAL, THEMED }

@Serializable enum class PlateShape { NONE, CIRCLE, SQUIRCLE, ROUNDED }

@Serializable enum class PlateColor { DARK, BLACK, WHITE, ACCENT, TRANSPARENT }

@Serializable enum class GlyphTint { WHITE, BLACK, ACCENT }

@Serializable enum class DotShape { ROUND, SQUARE, RING }

@Serializable enum class DrawerLayout { GRID, LIST }

@Serializable enum class DrawerSort { ALPHABETICAL, MOST_USED, RECENT }

@Serializable enum class FolderStyle { POPUP, FULLSCREEN }

@Serializable enum class BadgeStyle { DOT, COUNT, OFF }

@Serializable enum class AnimationSpeed(val multiplier: Float) { OFF(0f), SLOW(0.5f), NORMAL(1f), FAST(2f) }

@Serializable
enum class GestureAction {
    NONE,
    OPEN_DRAWER,
    OPEN_SEARCH,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN,
    EDIT_MODE,
    DEFAULT_PAGE,
    LAUNCHER_SETTINGS,
}
