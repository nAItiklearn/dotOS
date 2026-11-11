package com.dotos.feature.settings.screens

import com.dotos.feature.settings.nav.Routes

/** One searchable setting. [route] is where it lives. */
internal class SettingEntry(
    val title: String,
    val description: String,
    val route: String,
    val section: String,
    keywords: String = "",
) {
    val haystack: String = "$title $description $section $keywords".lowercase()
    val titleLower: String = title.lowercase()
}

/** Static index of every setting, used by the search box on the main screen. */
internal object SettingsIndex {
    private const val HOME = "Home screen"
    private const val ICONS = "Icons"
    private const val WALL = "Wallpaper"
    private const val DRAWER = "App drawer"
    private const val GEST = "Gestures"

    val entries: List<SettingEntry> = listOf(
        // Home screen
        SettingEntry("Home screen", "Preview, colours, icon pack, layout and wallpaper", Routes.HOME, HOME, "customise customize"),
        SettingEntry("App grid", "Columns and rows on the home screen", Routes.LAYOUT, HOME, "columns rows grid size 4 5 6 7"),
        SettingEntry("App label", "Display app label on home screen", Routes.LAYOUT, HOME, "names text labels"),
        SettingEntry("Search bar", "Show the search pill under the dock", Routes.LAYOUT, HOME, "search pill"),
        SettingEntry("Dock", "Show the dock at the bottom of the home screen", Routes.LAYOUT, HOME, "favourites favorites bar"),
        SettingEntry("Dock icons", "Number of icons in the dock", Routes.LAYOUT, HOME, "slots count 4 5 6"),
        SettingEntry("Dock background", "None, blur or solid backing behind the dock", Routes.LAYOUT, HOME, "style blur solid"),
        SettingEntry("Page indicator", "Show dots for the current home page", Routes.LAYOUT, HOME, "dots pages"),
        SettingEntry("Infinite scroll", "Wrap around from the last page to the first", Routes.LAYOUT, HOME, "loop wrap pages"),
        SettingEntry("Lock layout", "Prevent icons and widgets from being moved", Routes.LAYOUT, HOME, "freeze arrange"),
        SettingEntry("Allow rotation", "Let the home screen rotate with the device", Routes.LAYOUT, HOME, "landscape portrait"),
        SettingEntry("Status bar", "Show the status bar on the home screen", Routes.LAYOUT, HOME, "notification bar clock"),
        // Colours / theme
        SettingEntry("Wallpaper colours", "Tint icons with colours from your wallpaper", Routes.COLOURS, HOME, "tone material you palette"),
        SettingEntry("Basic colours", "Pick the accent colour", Routes.COLOURS, HOME, "accent red green blue amber"),
        SettingEntry("Themed icons", "Colour icon plates with a tone from the wallpaper", Routes.COLOURS, ICONS, "tint monochrome"),
        SettingEntry("Dark, light or auto", "Choose the appearance of DotOS", Routes.THEME, "Theme", "mode night day appearance"),
        SettingEntry("Accent colour", "Colour of small highlights and dots", Routes.THEME, "Theme", "red signal"),
        SettingEntry("Dot display font", "Use the dot-matrix font for clocks and numbers", Routes.THEME, "Theme", "doto typeface ndot"),
        // Icons
        SettingEntry("Icon style", "Line, dot-matrix, original or themed icons", Routes.ICONS, ICONS, "dot line original themed"),
        SettingEntry("Icon pack", "Use an installed third-party icon pack", Routes.ICONS, ICONS, "appfilter adw nova"),
        SettingEntry("Plate shape", "None, circle, squircle or rounded plate", Routes.ICONS, ICONS, "background shape"),
        SettingEntry("Plate colour", "Colour behind each icon", Routes.ICONS, ICONS, "background dark black white"),
        SettingEntry("Glyph tint", "White, black or accent icon symbols", Routes.ICONS, ICONS, "colour monochrome"),
        SettingEntry("Icon size", "Scale icon symbols from 70 to 130 percent", Routes.ICONS, ICONS, "scale glyph"),
        SettingEntry("Dot density", "How many dots build a dot-matrix icon", Routes.ICONS, ICONS, "resolution"),
        SettingEntry("Dot shape", "Round, square or ring dots", Routes.ICONS, ICONS, "circle square ring"),
        SettingEntry("Per-app icons", "Choose a different icon for a single app", Routes.ICON_OVERRIDES, ICONS, "override change custom"),
        // Wallpaper
        SettingEntry("Wallpaper gallery", "Pick a bundled wallpaper or your own photo", Routes.WALLPAPERS, WALL, "background library photo"),
        SettingEntry("Atmosphere", "Soft, drifting light behind the home screen", Routes.HOME, WALL, "glow gradient"),
        SettingEntry("Glass", "Ribbed-glass finish on the wallpaper", Routes.HOME, WALL, "flute reeded stripes"),
        SettingEntry("Customise wallpaper", "Presets, colours, sliders and randomise", Routes.WALLPAPER_EDITOR, WALL, "editor presets colours randomize"),
        SettingEntry("Wallpaper animation", "Off, slow, normal or fast; charging and battery saver", Routes.WALLPAPER_EDITOR, WALL, "speed charging battery"),
        SettingEntry("Parallax", "Tilt and scroll movement of the wallpaper", Routes.WALLPAPER_EDITOR, WALL, "gyroscope scroll depth"),
        SettingEntry("Home blur and dim", "Blur and darken the wallpaper on the home screen", Routes.WALLPAPER_EDITOR, WALL, "dim darken"),
        SettingEntry("Saved wallpaper presets", "Your own saved wallpaper looks", Routes.WALLPAPER_EDITOR, WALL, "my preset save"),
        SettingEntry("Live wallpaper", "Use the DotOS wallpaper system-wide", Routes.WALLPAPER_EDITOR, WALL, "system apply service"),
        // Drawer
        SettingEntry("Drawer layout", "Grid or list", Routes.DRAWER, DRAWER, "apps list grid"),
        SettingEntry("Drawer columns", "Number of columns in the grid", Routes.DRAWER, DRAWER, "grid"),
        SettingEntry("Sort apps", "A to Z, most used or recently installed", Routes.DRAWER, DRAWER, "order alphabetical"),
        SettingEntry("Drawer labels", "Show app names in the drawer", Routes.DRAWER, DRAWER, "names"),
        SettingEntry("Auto keyboard", "Open the keyboard when the drawer opens", Routes.DRAWER, DRAWER, "search focus"),
        SettingEntry("Fast scroller", "Alphabet scroller on the edge of the drawer", Routes.DRAWER, DRAWER, "letters index"),
        SettingEntry("Drawer blur and dim", "Blur and darken the wallpaper behind the drawer", Routes.DRAWER, DRAWER, "background"),
        // Folders
        SettingEntry("Folder style", "Popup or full-screen folders", Routes.FOLDERS, "Folders", "popup fullscreen"),
        // Gestures
        SettingEntry("Swipe up", "Action for swiping up on the home screen", Routes.GESTURES, GEST, "drawer"),
        SettingEntry("Swipe down", "Action for swiping down on the home screen", Routes.GESTURES, GEST, "notifications shade"),
        SettingEntry("Double tap", "Action for double-tapping the home screen", Routes.GESTURES, GEST, "lock screen"),
        SettingEntry("Long press", "Action for long-pressing an empty area", Routes.GESTURES, GEST, "edit mode"),
        SettingEntry("Home button", "Action for pressing home while already at home", Routes.GESTURES, GEST, "default page"),
        // Notifications
        SettingEntry("Badge style", "Dot, count or off for notification badges", Routes.BADGES, "Notifications", "dots counts"),
        SettingEntry("Notification access", "Allow DotOS to read notifications for badges", Routes.BADGES, "Notifications", "listener permission"),
        // Privacy
        SettingEntry("Hidden apps", "Hide apps from the home screen and drawer", Routes.PRIVACY, "Privacy", "hide lock private"),
        // Widgets
        SettingEntry("Widgets", "Bundled DotOS widgets", Routes.WIDGETS, "Widgets", "clock battery"),
        // Backup
        SettingEntry("Export backup", "Save layout, settings and presets to a file", Routes.BACKUP, "Backup", "dotbackup save"),
        SettingEntry("Restore backup", "Load a .dotbackup file", Routes.BACKUP, "Backup", "import"),
        // Performance
        SettingEntry("Reduce motion", "Replace springs and slides with simple fades", Routes.PERFORMANCE, "Performance", "animations accessibility"),
        SettingEntry("Disable blur", "Skip blur effects for smoother scrolling", Routes.PERFORMANCE, "Performance", "speed"),
        SettingEntry("Haptic feedback", "Light vibration on taps and snaps", Routes.PERFORMANCE, "Performance", "vibration"),
        SettingEntry("Clear icon cache", "Rebuild icons from scratch", Routes.PERFORMANCE, "Performance", "reset memory"),
        // About
        SettingEntry("About DotOS", "Version, licenses and reset", Routes.ABOUT, "About", "version unofficial"),
        SettingEntry("Open-source licenses", "Fonts and libraries used by DotOS", Routes.LICENSES, "About", "ofl apache"),
        SettingEntry("Reset everything", "Restore default settings and layout", Routes.ABOUT, "About", "factory wipe"),
    )

    fun search(query: String): List<SettingEntry> {
        val words = query.lowercase().trim().split(' ', '-').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { entry -> words.all { it in entry.haystack } }
            .sortedBy { entry -> if (words.all { it in entry.titleLower }) 0 else 1 }
    }
}
