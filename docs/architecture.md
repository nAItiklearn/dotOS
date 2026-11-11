# Architecture

DotOS is a single-activity-per-surface Compose app split into Gradle modules. This document describes what the code does today. Anything not implemented is called out as planned.

## Toolchain

| Item | Value |
|---|---|
| Language | Kotlin 2.1.21 (JVM target 17), Jetpack Compose (BOM 2025.05.01), Material 3 as a base theme |
| Android | minSdk 29, targetSdk 36, compileSdk 36 |
| Build | Gradle 8.14.1 wrapper, Android Gradle Plugin 8.10.1, version catalog in `gradle/libs.versions.toml`, KSP 2.1.21-2.0.1 |
| Storage | Room 2.7.1, DataStore Preferences 1.1.7, kotlinx.serialization 1.8.1 |
| Release | R8 minification and resource shrinking, baseline profile (`app/src/main/baseline-prof.txt` via ProfileInstaller), no dependency-info block in the APK |
| DI | None. A hand-written object graph (`AppGraph`) |
| Tests | JUnit 5 on the JVM: `SettingsCodecTest`, `AppSearchTest`, `IconMapTest` |

Shared Android and Kotlin configuration for every module is in the root `build.gradle.kts`, so module build files only list plugins, namespace and dependencies. Lint runs with `abortOnError = true`.

## Module graph

Arrows read "depends on"; all are `api` dependencies unless noted.

```
app
 |-- feature:home --------> core:designsystem, core:data, core:launcher,
 |                          feature:icons, feature:wallpaper, feature:widgets, feature:drawer
 |-- feature:settings ----> core:designsystem, core:data, core:launcher,
 |                          feature:icons, feature:wallpaper, feature:widgets
 |-- feature:drawer ------> core:designsystem, core:launcher, feature:icons
 |-- feature:icons -------> core:designsystem, core:launcher
 |-- feature:widgets -----> core:designsystem, core:data, core:launcher
 |-- feature:wallpaper ---> core:designsystem, core:data
 |-- feature:glance ------> core:designsystem (implementation; font resources only)
 |-- core:launcher -------> core:data
 |-- core:data
 '-- core:designsystem
```

`app` depends on every module. Feature modules never depend on `app`; the `app` module hands them what they need through small interfaces (`HomeDeps`, `SettingsDeps`, `WallpaperConfigSource`) implemented as anonymous objects in `MainActivity`, `SettingsActivity` and `DotApplication`. `feature:glance` is independent of the rest: it renders its own bitmaps and does not touch the database or settings.

| Module | Responsibility |
|---|---|
| `app` | Manifest, `DotApplication`, `AppGraph`, `MainActivity` (home), `SettingsActivity`, `CrashGuard`, `DotNotificationListener`, `DotAccessibilityService` |
| `core:designsystem` | `DotTheme` (colours, typography, reduce-motion and haptics locals), `DotMotion` (spring 0.8 / 380, press scale 0.94), `DotHaptics`, shared components (page indicator, switch, surfaces, press scale, launcher gesture detector), fonts |
| `core:data` | `LauncherSettings` and codec, `SettingsRepository`, Room entities, DAOs and `DotDatabase` |
| `core:launcher` | `AppsRepository` over `LauncherApps`, `AppInfo`, `AppCategory`, `ShortcutsRepository`, `AppSearch` |
| `feature:home` | `HomeScreen`, grid metrics, drag and drop, folders, edit sheet, item menu, `LayoutRepository`, `AppWidgetController` (third-party widget host), widget picker |
| `feature:drawer` | `AppDrawer`, `DrawerController`, `Calculator`, App Library (`library/`) |
| `feature:widgets` | `DotWidgetProvider` API, `DotWidgets` registry, 19 widgets in `dot/` |
| `feature:wallpaper` | `DotWallpaper` engine, `WallpaperLayer`, `GlassEffect`, 10 registered wallpapers, `DotWallpaperService` |
| `feature:icons` | `IconEngine`, `MaskExtractor`, `IconRenderer`, icon packs, glyph DSL and glyphs, `AppIcon` composable |
| `feature:settings` | Settings screens, search index, backup and restore, first-run flow |
| `feature:glance` | Glance app widgets (Dot Clock, Dot Battery, Life in Dots) |

## Process start and dependency graph

`DotApplication.onCreate`:

1. `CrashGuard.install` registers an uncaught-exception handler that counts crashes happening within 60 seconds of start (SharedPreferences `crash_guard`). If two such crashes were recorded, this start is *safe mode*. A scheduled call 60 seconds later resets the counter.
2. Builds `AppGraph(app, safeMode)`.
3. Touches `graph.settings.settingsOrNull` (starts the DataStore read) and calls `graph.apps.start()` so the first frame has data.

`AppGraph` is a plain class:

| Member | Notes |
|---|---|
| `scope` | `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` for the process |
| `settings` | `SettingsRepository`, created eagerly |
| `database` | `DotDatabase`, `lazy` |
| `apps` | `AppsRepository`, created eagerly |
| `icons` | `IconEngine` |
| `layout` | `LayoutRepository`, `lazy` (needs the database) |
| `homePresses` | `MutableSharedFlow<Unit>`; emitted when HOME is pressed while the launcher is already in front |
| `notificationListener` | `ComponentName` of `DotNotificationListener`, passed to the Music widget |

There is no reflection and no code generation for DI, which keeps cold start cheap. `Context.graph` is an extension that casts the application context. `DotApplication` also implements `WallpaperConfigSource`, so the live wallpaper service can read the current wallpaper config from the application object without any wiring.

`MainActivity` is `singleTask`, excluded from recents, handles `HOME` and `LAUNCHER_APP`, enables edge-to-edge and builds a `HomeDeps` object. Until the first settings read completes it draws a black box (so defaults never flash). `onNewIntent` turns a HOME intent that arrives while the activity is `RESUMED` into a `homePresses` emission. `SettingsActivity` has its own task affinity and also answers `APPLICATION_PREFERENCES`; screens are deep-linked with the `dotos.settings.screen` extra.

## Settings

All user settings are one data class, `LauncherSettings`, stored as a single JSON string under key `settings_json` in the DataStore Preferences file `launcher_settings`.

- `SettingsCodec` uses kotlinx.serialization with `ignoreUnknownKeys`, `encodeDefaults`, `coerceInputValues` and `explicitNulls = false`. Adding a field with a default needs no migration; unknown fields are ignored; an unreadable document decodes to defaults instead of throwing.
- `SettingsRepository.settingsOrNull` is a `StateFlow` started `Eagerly`; it is `null` until the first read. `update { }` decodes, transforms and re-encodes inside one DataStore edit.
- The settings app batches writes through `SettingsController`, a channel that drains everything queued into one DataStore edit, so dragging a slider does not write once per frame.
- Wallpaper configuration (`WallpaperConfig`) is nested inside the same document, so backup and the live wallpaper service read the same data.
- `version` (currently 1) is stored but there is no settings migration step yet.

Some settings are stored and shown in the UI but not consumed by the launcher yet: drawer list layout and drawer sort order, full-screen folder style, infinite page scroll, dock background style, and tilt parallax strength. They are planned.

## Database (Room)

`dotos.db`, `DotDatabase` version 1, `exportSchema = true` (KSP argument `room.schemaLocation` points to `core/data/schemas`, written at build time). The builder uses `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`: an unknown future schema resets instead of crashing the launcher. There are no upgrade migrations yet; any schema change must add a `Migration`.

| Table | Key columns | Purpose |
|---|---|---|
| `items` | `id` (auto), `type`, `container`, `screen`, `cellX`, `cellY`, `spanX`, `spanY`, `rank`, `packageName`, `className`, `userSerial`, `shortcutId`, `title`, `appWidgetId`, `widgetKind`, `widgetConfig`; indexes on `container` and `screen` | Everything placed on home, the dock or in a folder |
| `hidden_apps` | `componentKey` | Hidden apps |
| `app_overrides` | `componentKey`, `label`, `icon` (`"glyph:<id>"`) | Per-app icon override. A label column exists; no UI sets it yet |
| `app_usage` | `componentKey`, `launchCount`, `lastLaunched` | Feeds the App Library "Suggestions" tile |
| `notes` | `id`, `text`, `createdAt`, `updatedAt`, `widgetItemId` | Schema and DAO exist; the current note widgets store their text in `widget_state` instead |
| `wallpaper_presets` | `id`, `name`, `configJson`, `createdAt` | User-saved wallpaper looks (`WallpaperConfig` JSON) |
| `widget_state` | `key`, `value` | Key/value store for widget state: counters, timers, high scores, sticky note text, Quick Capture notes, Away Time history |

Conventions:

- `items.container`: `-100` is a home page (`Container.HOME`, the page is `screen`), `-101` is the dock (`Container.DOCK`, position is `rank`), any positive value is the id of a folder (position is `rank`).
- `items.type`: 0 app, 1 shortcut, 2 folder, 3 built-in widget (`widgetKind` and `widgetConfig`), 4 third-party AppWidget (`appWidgetId`).
- `componentKey` is `ComponentName.flattenToShortString() + "#" + userSerial`, which identifies an activity per user profile (work profile and Private Space apps get distinct keys).
- `widgetConfig` is a small JSON object (for example the clock face).

## Apps, layout and the home screen

**Apps.** `AppsRepository` loads every `LauncherActivityInfo` for every profile returned by `LauncherApps.profiles`, on `Dispatchers.IO`, into `StateFlow<List<AppInfo>?>` (null until the first load; sorted A-Z with a primary-strength `Collator`). It reloads, coalescing bursts with a 150 ms debounce, on `LauncherApps.Callback` events and on profile broadcasts (managed profile availability changes; on Android 12 and 15 the newer profile actions). Profile kind (main, work, private) comes from `LauncherApps.getLauncherUserInfo` on Android 15+, otherwise any non-main profile is treated as work. Quiet (paused) profiles are tracked in `quietProfiles`; a quiet Private Space is skipped entirely. DotOS's own launcher activity is hidden from the list. `AppCategory` buckets come from `ApplicationInfo.category` plus a curated package-prefix list.

**Layout.** `LayoutRepository` exposes the `items` table as a `StateFlow` and performs every mutation under one `Mutex` on `Dispatchers.IO`: move, add, resize, remove, create folder, add to folder, rename, delete page, `reflow` after a grid-size change (items that no longer fit move to the nearest free cell or a new page), and `seedIfEmpty` for the first run (clock, date and battery widgets, a few common apps, a dock resolved from default intents). Folder cleanup is automatic: an empty folder is deleted and a one-app folder dissolves into that app in place. `buildLayout` joins the rows with the live app map into `HomeLayout(pages, dock)`; apps that are not currently installed are skipped (their rows stay in the database).

**Home surface.** `HomeScreen` is one `Box`, bottom to top:

1. `WallpaperLayer` (or the system wallpaper if "use system wallpaper" is on).
2. The home column: status-bar spacer, a `HorizontalPager` with `homePages + 1` pages (the last one is the App Library), the page indicator, the dock, the search pill, navigation-bar spacer. Vertical gestures on this column drive the drawer.
3. `AppDrawer`, composed only while its progress is above zero or a drawer drag is in flight.
4. Overlays: `FolderOverlay`, `DropTargetsBar` (Remove, App info, Uninstall), `DragGhost`, `ItemMenu`, `EditSheet`, `WidgetPickerSheet`.

Grid geometry is in `GridMetrics`: cells are `width / cols` by `height / rows`, content uses a square `pitch = min(cellW, cellH)`, with `gutter = 0.226 * pitch` and `plate = pitch - gutter`, so 2x2 widgets stay square.

Drag and drop: long-press creates a pending `DragSession`; after 10 dp of travel it becomes a drag. `commitDrop` resolves the drop: a drop zone (remove, info, uninstall), the dock, folder creation or insertion (drop on an app or folder), or a free cell (the nearest free cell if the target is taken; a rejection haptic if the page is full). The dragged ghost glides to its cell before the database write. Hovering at a screen edge for about half a second changes page; dragging past the last page adds one.

**Drawer.** `DrawerController` owns an `Animatable` progress 0..1 (spring damping 0.86, stiffness 420). Progress drives the home column's alpha and scale, and the wallpaper's extra blur and dim. The drawer itself is an A-Z grid with an edge fast scroller, a search field (fuzzy ranking in `core:launcher/AppSearch`, plus the `Calculator` evaluator), a work tab when work apps exist, and a Private Space row on Android 15+. Hidden apps are filtered out before the drawer and App Library see the list.

**Built-in widgets** are `DotWidgetProvider` objects. `DotWidgetTile` draws the surface (fill, radius, hairline, clip shape) and gives the provider a `Content` composable. Providers talk to the launcher through `WidgetHostServices` (state, permissions, intents, photo picker, search, options), implemented by `HomeWidgetServices`. In safe mode the tile shows a placeholder instead of calling the provider. **Third-party widgets** use `AppWidgetController` (an `AppWidgetHost`), `AppWidgetBinder` (bind consent and configure activity) and `AppWidgetHostItem` (clipped host view).

## Icons, wallpapers and widgets at a glance

- Icons: see [icon-pipeline.md](icon-pipeline.md). `AppIcon` asks `IconEngine` for a bitmap off the main thread and draws the plate live in Compose.
- Wallpapers: see [wallpaper-engine.md](wallpaper-engine.md).
- `feature:glance` widgets are standard AppWidget receivers (`DotClockReceiver`, `DotBatteryReceiver`, `LifeInDotsReceiver` plus small refresh receivers for plug/unplug and date/time changes). They paint dot art into bitmaps and are available to any launcher.

## Threading

| Work | Where |
|---|---|
| Compose, `AppGraph.scope`, settings flows | Main (`Dispatchers.Main.immediate`) |
| App list load, layout writes, database reads | `Dispatchers.IO` |
| Icon mask extraction and rendering | `Dispatchers.Default.limitedParallelism(3)` |
| Photo wallpaper decode | `Dispatchers.IO` |
| Backup export and import | `Dispatchers.IO` |

## Resilience

- Corrupt settings decode to defaults; a newer database schema is reset rather than crashing.
- Repeated start-up crashes enter safe mode (built-in widgets become placeholders).
- Every `AppWidgetController` call is wrapped in `runCatching`, because widget providers are third-party code. A missing provider shows an "unavailable" placeholder.
- A wallpaper shader that fails to compile or bind falls back to its Canvas path.
- Backup restore validates every file before it changes anything.

## Privacy enforcement

The app manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"` (WorkManager, pulled in by Glance, would otherwise merge them in). CI dumps the permissions of every built APK with `aapt2` and fails on network, Wi-Fi state, location or ad-id permissions. See [../PRIVACY.md](../PRIVACY.md).
