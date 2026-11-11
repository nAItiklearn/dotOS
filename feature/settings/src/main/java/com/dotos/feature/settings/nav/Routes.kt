package com.dotos.feature.settings.nav

import com.dotos.feature.settings.SettingsScreens

/** Screen ids. Public deep-link ids come from [SettingsScreens]; the rest are internal. */
internal object Routes {
    const val MAIN = "main"
    const val HOME = SettingsScreens.HOME_CUSTOMIZER
    const val WALLPAPERS = SettingsScreens.WALLPAPERS
    const val WALLPAPER_EDITOR = "wallpaper_editor"
    const val COLOURS = SettingsScreens.COLOURS
    const val ICONS = SettingsScreens.ICONS
    const val ICON_OVERRIDES = "icon_overrides"
    const val ICON_PICK = "icon_pick"
    const val LAYOUT = SettingsScreens.LAYOUT
    const val DRAWER = "drawer"
    const val FOLDERS = "folders"
    const val GESTURES = "gestures"
    const val THEME = "theme"
    const val BADGES = "badges"
    const val PRIVACY = "privacy"
    const val BACKUP = "backup"
    const val PERFORMANCE = "performance"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val WIDGETS = SettingsScreens.WIDGETS
    const val FIRST_RUN = SettingsScreens.FIRST_RUN

    private val known = setOf(
        MAIN, HOME, WALLPAPERS, WALLPAPER_EDITOR, COLOURS, ICONS, ICON_OVERRIDES, LAYOUT, DRAWER, FOLDERS, GESTURES,
        THEME, BADGES, PRIVACY, BACKUP, PERFORMANCE, ABOUT, LICENSES, WIDGETS, FIRST_RUN,
    )

    /** Maps the deep-link id from the intent to a route; unknown ids open the main screen. */
    fun fromDeepLink(id: String?): String = if (id != null && id in known && id != ICON_PICK) id else MAIN

    fun withArg(route: String, arg: String): String = "$route|$arg"

    fun idOf(encoded: String): String = encoded.substringBefore('|')

    fun argOf(encoded: String): String? = if ('|' in encoded) encoded.substringAfter('|') else null
}
