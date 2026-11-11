# DotOS Launcher

An open-source Android home-screen launcher written in Kotlin and Jetpack Compose. It recreates the *style* of a modern dot-matrix phone UI (black plates, monochrome dot icons, procedural wallpapers, dot-matrix widgets) using only original or open-source assets. It is fully offline: the APK has no `INTERNET` permission, and CI fails the build if one appears.

> **Unofficial, fan-made project. Not affiliated with, endorsed by, or connected to Nothing Technology Limited. All artwork, icons, wallpapers and fonts in this repository are original or open-source (SIL OFL).**

Status: early development (version 0.1.0). Things marked *planned* below are not implemented yet.

## Features

**Home screen**
- Paged grid (4 columns by 6 rows by default, up to 7 columns by 8 rows), free placement with snapping and collision handling, a dock, and a dot page indicator.
- Apps, folders, built-in widgets and third-party widgets. Long-press an item to drag it; drop it on another app to make a folder, on the dock, or on the Remove / App info / Uninstall targets. Dragging to a screen edge changes page, and dragging past the last page creates one.
- Folders open as a rounded popup with inline rename; a folder left with one app dissolves back into it.
- Long-press an empty area for the edit sheet (wallpaper, widgets, icons, layout, settings, add or delete a page). Lock-layout, rotation and status-bar toggles.
- Item menu with the app's static and dynamic shortcuts (needs DotOS to be the default home app), resize for built-in widgets, change icon, hide, remove, uninstall.
- Configurable gestures for swipe up, swipe down, double tap, long-press on empty space and home-button-while-at-home. Actions: app drawer, search, notifications, quick settings, lock screen, edit mode, default page, DotOS settings.

**App Library and drawer**
- App Library: the page after your last home page. Apps are grouped into tiles (Suggestions from recently launched apps, Recently added in the last 14 days, then categories from `ApplicationInfo.category` plus a curated package list). Tap a tile to open it; it has its own search.
- Drawer: swipe up from home. A-Z grid, edge fast-scroller, notification badges (dot or count), blurred and dimmed wallpaper behind it.
- Local search: ranks exact, prefix, word prefix, initials, substring, then subsequence matches, with accent folding. Typing an arithmetic expression (`+ - * / % ^`, parentheses) shows the result inline.
- Work-profile tab when work apps exist. Private Space row on Android 15 and later (lock and unlock from the drawer; the launcher must be the default home app).
- Hidden apps: removed from the drawer and App Library (icons you already placed on a home page stay there). The list is managed behind the device fingerprint or screen lock. This is a convenience, not security: other launchers and system Settings still show the app.

**Icons**
- Resolution order: per-app override, bundled DotOS glyph, the app's own Android 13 monochrome layer, then an automatic single-colour mask extracted from the app's icon. Details in [docs/icon-pipeline.md](docs/icon-pipeline.md).
- Styles: Line (default), Dot-matrix, Original, Themed. Plate shape and colour, glyph tint, size, dot density and dot shape are adjustable.
- Original vector glyphs are drawn in code (a small drawing DSL) and mapped by package name. Brand apps are intentionally not given look-alike logos; they go through the monochrome / auto-mask path.
- Icon pack support for the common `appfilter.xml` format (ADW / Nova style packs).

**Wallpapers**
- Ten procedural wallpapers rendered live from code (AGSL shaders on Android 13+, Canvas fallback below): Atmosphere (default), Dot Field, Halftone Orb, Dot Wave, Orbit, Gradient Dither, Matrix Rain, Contour Map, Pulse, Glyph Burst. Each has named presets and sliders, colour slots, animation speed, "only while charging" and "pause in battery saver", scroll parallax, randomize and saved presets.
- Glass: a fluted (ribbed) glass effect over the wallpaper (Android 13+).
- Separate blur and dim for the home screen and the drawer. Your own photo can be used (system Photo Picker, no storage permission).
- A live wallpaper service runs the same engine system-wide (without the Glass effect, blur or photo mode). See [docs/wallpaper-engine.md](docs/wallpaper-engine.md).
- Gyroscope (tilt) parallax is *planned*: the setting exists, no sensor is wired to it yet.

**Widgets**
- 19 built-in widgets drawn with Compose: Clock (analog, digital, world, binary, micro faces), Date & Calendar, Battery, Compass, Life in Dots, Quick Settings, Search, Photo, Recorder, Pedometer, Away Time, Breathing Break, Timer, Sticky Note, LED Sign, Snake, Music, Counter, Quick Capture.
- Widget picker with search and previews, plus a face carousel for widgets with several faces.
- Hosting for third-party widgets (`AppWidgetHost`), clipped to rounded corners. Their own pixels are not restyled.
- Real home-screen widgets for other launchers, built with Glance: Dot Clock, Dot Battery, Life in Dots.

**Settings and data**
- One searchable settings app: home layout, drawer, icons, wallpaper, widgets, folders, gestures, theme (dark / light / auto, accent colour, dot display font), badges, hidden apps, performance (reduce motion, disable blur, haptics, clear icon cache), backup, licenses, about.
- Backup and restore as a single `.dotbackup` zip through the Storage Access Framework (settings, home layout, hidden apps, icon overrides, saved wallpaper presets). Third-party widgets are not restored because their ids are tied to the old install.
- Three-step first-run flow (default home app, wallpaper, icon style) that can be skipped.
- Crash guard: after repeated start-up crashes the launcher starts in safe mode, where built-in widgets show a placeholder so the home screen still works.

**Not implemented yet (planned):** drawer list layout and alternative sort orders (the settings exist but the drawer is always an A-Z grid), full-screen folders, infinite page scroll, dock background styles, tilt parallax, a static lock-screen wallpaper export. The lock screen itself cannot be customised by any third-party launcher and is out of scope.

## Privacy

DotOS collects nothing and sends nothing. There is no analytics, crash reporting, advertising, account or update check. `INTERNET` and `ACCESS_NETWORK_STATE` are explicitly removed from the merged manifest, and the CI "Privacy check" step fails the build if `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, fine or coarse location, or an advertising id permission appears in the release or debug APK. See [PRIVACY.md](PRIVACY.md).

| Permission | Why it exists |
|---|---|
| `QUERY_ALL_PACKAGES` | A launcher has to list every launchable app on the device (all profiles) and find installed icon packs. |
| `BIND_APPWIDGET` | Host third-party widgets on the home screen. |
| `EXPAND_STATUS_BAR` | Swipe-down gesture opens the notification shade or quick settings. |
| `REQUEST_DELETE_PACKAGES` | The Uninstall action in the item menu. |
| `USE_BIOMETRIC` | Fingerprint or screen-lock prompt before the hidden apps list is shown. |
| `VIBRATE` | Haptic feedback (can be switched off). |
| `SET_WALLPAPER` | Declared for handing the wallpaper to the system. Today the only code path opens the system live-wallpaper chooser. |
| `RECORD_AUDIO` | Optional, asked on first use: Recorder widget and the voice memo in Quick Capture. Audio stays in app-private storage. |
| `ACTIVITY_RECOGNITION` | Optional, asked on first use: Pedometer widget (step counter sensor). |
| `READ_CALENDAR` | Optional, asked on first use: upcoming events in the Date & Calendar widget. |
| Notification access | Optional special access. Counts notifications per app for badges, and lets the Music widget read the current media session. Nothing is stored or sent. |
| Accessibility service | Optional. Used only to lock the screen from a gesture (`GLOBAL_ACTION_LOCK_SCREEN`); it does not read screen content. |

Libraries can add permissions of their own during manifest merging (for example WorkManager, which Glance depends on). The authoritative list for a given build is the `aapt2 dump permissions` output in the CI "Privacy check" step.

## Requirements

Android 10 (API 29) or newer. Some features need more: blur needs Android 12, AGSL wallpaper shaders and the Glass effect need Android 13, monochrome app icon layers need Android 13, and Private Space needs Android 15. On older versions DotOS falls back (Canvas wallpapers) or hides the feature.

## Install

1. Open the repository's **Actions** tab on GitHub, pick a successful **CI** run, and download the **dotos-apks** artifact. (Artifacts need a signed-in GitHub account.) A **Releases** page build is the alternative once one is published.
2. Unzip it. `release` is the normal build; the `debug` build uses the application id `com.dotos.launcher.debug` and can be installed next to it. Unless the maintainers' signing secrets were available to the run, the release APK is signed with the debug key; updates only work between APKs signed with the same key.
3. Allow your file manager or browser to install unknown apps, then install the APK.
4. Press Home and choose **DotOS Launcher** as the default home app (or Settings > Apps > Default apps > Home app).

## Build

You do not need any local tooling: the **CI** workflow builds debug and release APKs on every push and pull request.

To build locally you need JDK 17 and the Android SDK (platform 36). The Gradle wrapper (8.14.1) downloads everything else.

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/
./gradlew assembleDebug
./gradlew testDebugUnitTest lintRelease
```

Release signing reads `DOTOS_KEYSTORE`, `DOTOS_KEYSTORE_PASSWORD`, `DOTOS_KEY_ALIAS` and `DOTOS_KEY_PASSWORD` from the environment; without them the release build falls back to the debug key. Never commit keystores.

## Screenshots

Screenshots are produced by the **Screenshots** GitHub Actions workflow, which installs the release APK on an emulator, drives the main screens and uploads them as the `screenshots` artifact. They are not committed to the repository yet.

## Project layout

| Module | Contents |
|---|---|
| `app` | Entry point: `MainActivity` (home), `SettingsActivity`, manual dependency graph, notification listener, accessibility service |
| `core:designsystem` | Theme, colours, typography (Doto, Space Grotesk, Space Mono), motion, haptics, dot components |
| `core:data` | Room database, DataStore settings, settings model |
| `core:launcher` | `LauncherApps` app list, shortcuts, categories, fuzzy search |
| `feature:home` | Pager, grid, dock, folders, drag and drop, widget host and picker |
| `feature:drawer` | App drawer, App Library, calculator |
| `feature:widgets` | Built-in widget API and the 19 widgets |
| `feature:wallpaper` | Wallpaper engine, 10 wallpapers, Glass effect, live wallpaper service |
| `feature:icons` | Icon engine, mask extraction, dot renderer, glyph DSL and glyphs, icon packs |
| `feature:settings` | Settings screens, backup and restore, first-run flow |
| `feature:glance` | Glance widgets for use on other launchers |

More: [docs/architecture.md](docs/architecture.md), [docs/wallpaper-engine.md](docs/wallpaper-engine.md), [docs/icon-pipeline.md](docs/icon-pipeline.md), [docs/design-reference.md](docs/design-reference.md).

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first: the two hard rules are original assets only and no network access. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Bundled fonts are under the SIL Open Font License 1.1 and libraries under their own licenses; see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
