# DotOS Launcher — Build Spec for Claude Code

> Open-source Android launcher that recreates the Nothing Phone (4a) / Nothing OS 4.1–5.0 home-screen look and feel.
> Everything ships inside the APK. No downloads, no signup, no ads, no internet, no tracking.

You are building this project end to end. Read this whole file first, then work through the phases in order. After each phase, build, run lint, and commit.

---

## 0. Non-negotiable rules

1. **Android only.** Kotlin + Jetpack Compose. No Flutter, no React Native.
2. **Zero network.** Do NOT declare `INTERNET` or `ACCESS_NETWORK_STATE`. No analytics, crash reporters, ads SDKs, Firebase, or Play Services dependencies. Verify with `./gradlew :app:dependencies` and a merged-manifest check in CI.
3. **No signup, no account, no paywall, no ads.** Everything is unlocked.
4. **Everything bundled.** Wallpapers, icons, fonts, widgets all live in the APK. The user installs and is done.
5. **Original assets only.** Nothing's own wallpapers, icon artwork, and the proprietary Ndot / NType fonts are copyrighted. Do NOT copy, trace, or embed them. Instead recreate the *style* with original work:
   - Wallpapers: generated in code (see section 5). They look like the Nothing dot-matrix aesthetic but are 100% original.
   - Icons: original monochrome dot-style icons drawn as vector drawables / generated in code (section 4).
   - Font: use **Doto** (SIL OFL, open source dot-matrix font) as the Ndot stand-in, plus **Space Mono** / **JetBrains Mono** (OFL) for body text. Bundle the font files and their license files.
   - Do not use the Nothing name or logo in the app name or icon. App name: **DotOS Launcher** (placeholder, easy to rename in one `strings.xml` entry). Add a README note that it is an unofficial, fan-made project not affiliated with Nothing Technology Limited.
6. **License:** GPL-3.0 (or Apache-2.0 if the maintainer prefers). Include `LICENSE`, `THIRD_PARTY_LICENSES.md`, and an in-app "Open source licenses" screen.
7. **Performance is a feature.** Target 120 Hz, no jank, cold start under 400 ms to first home frame, baseline profiles on.
8. **Lock screen is OUT OF SCOPE.** Third-party launchers cannot customize it. Do not build lock-screen widgets or a lock-screen replacement. (A launcher can optionally set the system lock-screen *wallpaper* via `WallpaperManager.FLAG_LOCK`; this is an optional toggle in settings, off by default.)

---

## 1. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose (Material3 as base, heavily custom-themed), Compose Foundation pager/gestures |
| Min / target SDK | minSdk 29, targetSdk 36 (bump to 37 when stable), compileSdk latest |
| DI | Hilt (or Koin if lighter is wanted) |
| Storage | Room (layout, folders, hidden apps), DataStore Proto/Preferences (settings) |
| Graphics | AGSL `RuntimeShader` on API 33+, Canvas/`DrawScope` fallback below |
| Widgets | `AppWidgetHost` for third-party widgets + custom in-app widgets (Compose via Glance for real app widgets) |
| Build | Gradle Kotlin DSL, version catalog, R8 full mode, baseline + startup profiles |
| Tests | JUnit5, Turbine, Compose UI tests, Macrobenchmark |
| CI | GitHub Actions: build, lint, detekt, ktlint, tests, merged-manifest permission check |
| Release | F-Droid compatible (reproducible build, no proprietary deps), GitHub Releases APKs |

Module layout:
```
:app                  launcher entry, navigation, DI wiring
:core:designsystem    theme, colors, typography, dot components, motion
:core:data            Room, DataStore, repositories
:core:launcher        LauncherApps, app list, shortcuts, badges, hidden apps
:feature:home         home pager, grid, dock, folders, drag and drop
:feature:drawer       app drawer, search, alphabet scroller, private space
:feature:widgets      widget picker, host, custom dot widgets
:feature:wallpaper    wallpaper engine, customizer, live wallpaper service
:feature:icons        icon engine, packs, adaptive/monochrome processing
:feature:settings     settings screens, backup/restore
```

---

## 2. Design language (the "Nothing 4a look")

Recreate these traits exactly in spirit; tune values by eye against reference screenshots on a real device.

**Color**
- Dark theme default: pure black `#000000` surfaces (OLED), white `#FFFFFF` primary content, greys `#1A1A1A / #2A2A2A / #8A8A8A` for layers and secondary text.
- Light theme: off-white `#F2F2F2` background, black content.
- Single accent: signal red `#D71921` used sparingly (notification dots, active indicators, one-dot highlights, toggles in "on" state optional). Accent is user-changeable (red default, plus white, green, blue, amber, none).
- No gradients on UI chrome. No shadows except extremely subtle elevation on widgets in light mode.

**Typography**
- Display / clock / numerals: Doto (dot-matrix).
- UI labels: a clean grotesk-like sans (bundle an OFL font, e.g. "Inter" or "Space Grotesk"); small caps-style labels with wide letter-spacing for section headers.
- Monospace for data readouts (Space Mono).

**Shapes & layout**
- Widgets: large corner radius (28–32 dp), pure black or near-black fill, 1 dp hairline border `#FFFFFF1F` on dark.
- Icons: monochrome, dot-grid style, uniform stroke, same optical size; circular or squircle plates optional.
- Generous whitespace, tight grid, everything aligned to a 4 dp baseline.
- Dot motifs everywhere: page indicators are dots, loading is dots, toggles have a dot thumb, dividers can be dotted lines.

**Motion**
- Springs, not tweens: `spring(dampingRatio = 0.8f, stiffness = 380f)` as default.
- Icon press: scale to 0.94 with 120 ms ease-out, release with spring.
- Drawer open: shared fade + 24 dp translate, wallpaper blur ramps 0 → 24 dp.
- Dot animations: staggered per-dot alpha/scale (use a single `Canvas` or shader, never one composable per dot).
- Honor "Remove animations" system setting and a launcher-level "Reduce motion" toggle.

**Haptics**
- Light tick on page snap, icon pick-up, toggle change. Use `HapticFeedbackConstants` (CLOCK_TICK, CONFIRM, REJECT). User toggle to disable.

---

## 3. Core launcher features

### 3.1 Home screen
- Horizontal pager of home pages, user-defined grid per page (default 5 columns x 6 rows, selectable 4–7 x 4–8).
- Items: apps, shortcuts, folders, widgets. Free placement on grid with snapping and collision handling.
- Long-press empty area → edit mode with: Wallpaper, Widgets, Icons, Settings quick actions in a bottom sheet.
- Drag and drop with magnet snap, ghost preview, page-edge auto-scroll, drop onto another icon to create a folder, drop on "Remove" / "App info" / "Uninstall" targets.
- Page indicators: dots, with an optional line for scroll position. Add / delete / reorder pages.
- Infinite scroll toggle (wrap pages).
- Lock layout toggle.
- Dock: 4–6 slots, optionally hidden, background style (none / blur / solid), supports folders.
- Home screen rotation toggle (follow system / portrait only).
- "Show status bar" and "Show navigation bar" options, edge-to-edge by default.
- Double-tap to lock screen (via `AccessibilityService` or, preferably, `DevicePolicyManager.lockNow()`; offer both, explain trade-offs in the setup screen; default off).

### 3.2 Folders
- Dot-style folder preview (2x2 mini icons or monochrome dot cluster), open into a rounded popup, rename inline, drag out to remove.
- Folder styles: grid popup, full-screen, stacked.
- Optional folder colors.

### 3.3 App drawer
- Swipe up from home (or dock handle) to open, swipe down to close.
- Layouts: vertical grid, vertical list, horizontal paged grid.
- Search bar pinned at top: instant local search over apps, contacts (optional permission), shortcuts, settings entries. Fuzzy + initials + pinyin/transliteration friendly matching. Auto-keyboard toggle.
- Alphabet fast scroller on the edge with dot-style letter bubbles.
- Sort: A–Z, most used, recently installed, custom categories.
- Categories/tabs (user-defined, optional auto-categories from `ApplicationInfo.category`).
- Hidden apps: hide from home and drawer (PIN/biometric to view the hidden list), searchable only when unlocked.
- **Private Space** support (Android 15+): integrate the system Private Space via `LauncherApps` private profile APIs; show a locked-section entry at the bottom of the drawer.
- Work profile apps shown in a separate tab with badge.
- App context menu on long press: App info, Shortcuts (static + dynamic via `LauncherApps.getShortcuts`), Widgets of this app, Hide, Uninstall, Rename (local label override), Change icon.
- Notification badges: dot (default), count, or off. Use `NotificationListenerService` (opt-in, local only).

### 3.4 Gestures
Configurable actions for each gesture (none, open drawer, open search, open notification shade, open quick settings, lock screen, open app, open recents, toggle flashlight, launch custom shortcut):
- Swipe up / down / left / right on home
- Double tap
- Two-finger swipe up / down
- Pinch in (overview of pages) / pinch out
- Long-press on empty area
- Swipe on dock
- Home button press when already at home

### 3.5 Search & shortcuts
- Universal search (global swipe-down or dedicated bar widget): apps, contacts, calendar events (optional), calculator expressions, unit conversion, quick toggles. All local.
- Recents row optional.

### 3.6 Essential Apps / Essential Space-style widgets (local only)
Nothing OS has "Essential Apps" widgets (tiny single-purpose widgets). Provide a built-in set of small utility widgets rendered by the launcher (no network, no AI cloud):
- Sticky Note (one short note, tap to edit)
- Quick Counter / Habit dots
- Timer / Stopwatch (dot display)
- Quick Capture (opens a local note composer, stores in Room)

### 3.7 Hide / lock / misc
- Lock individual apps behind biometrics (launcher-level only, honest warning that it does not block other launchers).
- Folder lock optional.
- "Recents" integration: if the launcher is the default home app, provide a proper recents provider (use `RecentsActivity` approach as in Lawnchair/Launcher3) behind a settings toggle; fall back to the system recents screen.

---

## 4. Icon system (all bundled)

### 4.1 Pipeline (highest priority to get right)
Resolve an icon for each app in this order:
1. **User override** (per-app custom icon from gallery, picker, or an installed icon pack).
2. **Bundled DotOS icon set** (hand-built vector icons keyed by package name, section 4.2).
3. **App's own monochrome layer** (`AdaptiveIconDrawable.monochrome`, Android 13+), tinted to the theme color on a dot plate.
4. **Auto dot-ify fallback:** take the app's foreground, convert to single color, render through a dot-matrix mask (see 4.3) so even unknown apps match the aesthetic.

### 4.2 Bundled icon set
- Author as clean 108 dp adaptive-style vectors, one monochrome foreground per icon, using a consistent 2 dp stroke or dot-grid construction.
- Cover the top ~300 popular packages (Google apps, WhatsApp, Instagram, Telegram, Spotify, YouTube, Chrome, Maps, Gmail, banking and payment apps, camera, gallery, phone, messages, settings, calculator, clock, files, etc.). Store a `package → drawable` map in a JSON asset, with `ComponentName`-level overrides where needed.
- Generate the set programmatically where possible (script in `/tools/iconsmith` that renders simple glyphs through the dot-grid renderer) to keep the repo small and original.
- Do NOT trace or copy Nothing's icon artwork or any paid/third-party pack artwork. Draw original icons in the same minimalist spirit.

### 4.3 Dot-ify renderer
- Input: any `Drawable` → rasterize to 64x64 → luminance/alpha threshold → downsample to a dot grid (default 12x12 to 20x20) → draw circles whose radius reflects coverage.
- Cache results as small PNG/WebP in app storage keyed by package + version + settings hash.
- Run on a background dispatcher, never on the main thread; show a placeholder dot grid until ready.

### 4.4 Icon styling options (Settings → Icons)
- Style: **Dot-matrix**, **Line (monochrome)**, **Original colors**, **Themed (Material You)**.
- Plate shape: none, circle, squircle, rounded square, teardrop, custom corner radius slider.
- Plate color: transparent, black, white, accent, per-theme.
- Icon size scale 70–130 %, label size, label on/off, label lines 1–2, label color.
- Dot density slider, dot shape (round, square, ring), dot gap.
- Monochrome tint: white / black / accent / custom color.
- Per-app override screen.

### 4.5 Icon pack compatibility
Support the standard `appfilter.xml` icon pack format (ADW/Nova/Lawnchair compatible) so users *may* optionally use third-party packs, but the app must look complete with none installed.

---

## 5. Wallpaper engine (all bundled, procedural)

All wallpapers are rendered live from code. This gives infinite resolution, tiny APK size, full customization, and avoids copyright issues. Implement a `WallpaperEngine` interface:

```kotlin
interface DotWallpaper {
    val id: String
    val name: String
    val params: List<WallpaperParam>   // sliders, color pickers, toggles
    fun DrawScope.render(state: WallpaperState, time: Float)
}
```
Back it with an AGSL shader per wallpaper on API 33+, and a Canvas fallback implementation for API 29–32. Keep every wallpaper to one draw pass; precompute dot positions once.

### 5.1 The 10 bundled wallpapers
Original designs in the dot-matrix / Nothing-inspired spirit:

1. **Dot Field**: a uniform dot grid with a soft radial falloff and a single red dot.
2. **Halftone Orb**: a sphere built from variable-radius dots, light source adjustable.
3. **Dot Wave**: sinusoidal ripple moving through the grid (speed, amplitude, frequency).
4. **Orbit**: concentric dot rings with slow independent rotation.
5. **Gradient Dither**: smooth two-tone gradient rendered as dithered dots (Bayer / blue-noise option).
6. **Matrix Rain**: sparse falling dot columns, user-set density and speed.
7. **Contour Map**: topographic contour lines drawn from dots via simplex noise.
8. **Pulse**: dots that pulse outward from touch points or battery level.
9. **Glyph Burst**: radial dot burst/starfield with depth.
10. **Atmosphere**: soft blurred-glow dots reacting to time of day, weather-free (use local clock only) and optional gyroscope parallax.

Each ships with 4–6 named presets (e.g., "Midnight", "Paper", "Signal Red", "Mono", "Ember", "Frost") so the picker feels rich out of the box.

### 5.2 Customizer (per wallpaper)
- Dot size, dot spacing, dot shape, dot jitter.
- Foreground color, background color, accent dot color, gradient direction.
- Animation: off / slow / normal / fast, plus "animate only while charging" and "pause in battery saver".
- Parallax (gyroscope) amount; scroll parallax across home pages.
- Blur and dim sliders, separately for home and drawer.
- **Atmosphere** controls: glow intensity, grain/noise, vignette, time-of-day tint auto-shift.
- Randomize button + save own preset (stored locally in Room).
- Live preview on a phone mock at the top of the editor.

### 5.3 Applying
- Primary mode: **in-launcher wallpaper** (rendered behind the home pager; zero system permissions, smoothest).
- Optional mode: **live wallpaper service** (`WallpaperService`) so the same engine runs system-wide (home screen with this launcher or any other).
- Optional: export a static PNG of the current wallpaper to the system via `WallpaperManager` for lock screen (toggle off by default, honest about what it does).
- Support user-picked images too (gallery picker via Photo Picker, no storage permission) with the same blur/dim/parallax controls.

---

## 6. Widgets

### 6.1 Hosting
- Full `AppWidgetHost` support for any third-party widget: add, resize (handles), reconfigure, remove, per-widget corner radius clipping on Android 12+ (`android.R.dimen.system_app_widget_background_radius`).
- Widget picker: bottom sheet grouped by app, search, preview cards, shows size in grid cells.

### 6.2 Bundled DotOS widgets (render with Glance as real app widgets AND in-launcher preview)
Modeled on categories Nothing OS offers; all original code, all offline:

| Widget | Sizes | Notes |
|---|---|---|
| **Dot Clock** | 2x2, 4x2, 4x4 | Doto digits, several faces (digital dot, analog dot ring, "micrographics"-style small-type layout, binary dots) |
| **Date & Calendar** | 2x2, 4x2 | Next events from local calendar (optional permission), dot month grid |
| **Battery** | 2x1, 2x2 | Dot-bar battery of phone; optionally Bluetooth device batteries where the system exposes them |
| **Quick Look** | 4x2 | Next event, reminders; no weather unless user supplies local API-free data (omit weather) |
| **Pedometer** | 2x2 | Local step counter via `Sensor.TYPE_STEP_COUNTER` with `ACTIVITY_RECOGNITION`, daily/weekly goal dots, streak |
| **Away Time** | 2x2, 4x2 | Time since the screen was last used, longest break today, weekly trend (screen on/off broadcasts, stored locally) |
| **Life in Dots** | 4x2, 4x4 | Year / month / day / hour progress as dot grids |
| **Quick Settings** | 4x1, 4x2 | Toggles for Wi-Fi panel, Bluetooth panel, flashlight, DND, rotation, hotspot panel, QR scanner shortcut |
| **Recorder** | 2x1 | One-tap start/stop mic recording to app storage (optional, RECORD_AUDIO) |
| **Sticky Note** | 2x2 | Single short note |
| **Breathing Break** | 2x2, 4x2 | Guided inhale/exhale with expanding dot circle, adjustable pattern (4-7-8, box, custom), haptics |
| **Timer / Stopwatch** | 2x2 | Dot display, multiple timers |
| **Snake** | 2x2 | Dot-matrix Snake, swipe to steer, local high score |
| **LED Sign** | 4x1, 4x2 | Scrolling or static text in big dot-LED letters, color and speed |
| **Music** | 4x2 | Now-playing via `MediaSessionManager` (needs notification listener access), dot visualizer |
| **Search Bar** | 4x1 | Launches the local search |

- Every widget: user-adjustable colors (monochrome / accent), background (black, white, transparent, blur), corner radius, dot density.
- Widgets are stateless where possible and update on events, not timers, to protect battery.
- **No lock-screen widgets** (out of scope).

---

## 7. Settings (single, searchable, dot-styled screen)

Top-level sections, each a clean page, with a settings search box:

1. **Home screen**: grid size, icon size, labels, dock, pages, infinite scroll, lock layout, rotation, status bar, double-tap lock.
2. **App drawer**: layout, sorting, categories, search behavior, fast scroller, background style and blur.
3. **Icons**: style, shape, size, dot options, tint, per-app overrides, icon packs.
4. **Wallpaper & atmosphere**: library, customizer, live wallpaper toggle, blur/dim, parallax.
5. **Widgets**: manage, default styles, reset.
6. **Folders**: style, background, animation.
7. **Gestures**: per-gesture action mapping.
8. **Theme**: dark / light / auto, accent color, font choice (Doto / system), corner radius, Material You toggle.
9. **Notifications & badges**: badge style, listener access status.
10. **Hidden apps & privacy**: hidden list, app lock, Private Space.
11. **Backup & restore**: export / import everything (layout, settings, custom presets, icon overrides) as a single `.dotbackup` zip file via the Storage Access Framework, with a version header and migration support.
12. **Performance**: animation speed, reduce motion, disable blur, preload drawer.
13. **About**: version, licenses, source link, "unofficial fan project" notice, reset all.

Rules: every toggle has a one-line description, changes apply instantly with live preview, no confirmation dialogs except destructive ones, all settings default to sensible Nothing-like values.

**First-run setup** (3 screens, skippable): choose default launcher → pick wallpaper → pick icon style. Then land on a ready, beautiful home screen with a default layout (clock widget, a few dock apps).

---

## 8. Permissions policy

Only request when the feature is first used, with a plain-language rationale screen:
- `QUERY_ALL_PACKAGES` (launcher declaration; justify in README and for F-Droid metadata)
- `BIND_APPWIDGET` (host)
- `EXPAND_STATUS_BAR` / `USE_BIOMETRIC` / `VIBRATE` (normal)
- Optional, runtime: `READ_CONTACTS`, `READ_CALENDAR`, `ACTIVITY_RECOGNITION`, `RECORD_AUDIO`, notification-listener special access, device-admin (lock), accessibility (alternative lock method).
- Absolutely none of: `INTERNET`, `ACCESS_NETWORK_STATE`, ad IDs, location.

---

## 9. Performance & quality checklist

- Home and drawer are lazy; icons decoded on a background pool with LRU memory cache + disk cache.
- Avoid recomposition storms: stable models, `derivedStateOf`, `key()` for grid items, hoist state.
- Use `Modifier.graphicsLayer` for animated transforms; avoid layout animation on large lists.
- Wallpaper shaders: cap at 60 fps when idle, 120 fps only during interaction; pause when the screen is off or launcher is not visible.
- Macrobenchmark suite: cold start, drawer open, page swipe, drag. Fail CI on regression > 10 %.
- Accessibility: TalkBack labels on every control, minimum 48 dp targets, contrast checks, font scale up to 200 %.
- Localization-ready (all strings in resources); ship English plus structure for contributors.
- Edge cases: foldables, tablets (adaptive grid), RTL, multi-user, work profiles, large display / font scales.
- Crash resilience: corrupted layout falls back to default; never block the home screen on a failed widget.

---

## 10. Build phases (do in order, commit after each)

**Phase 1: Skeleton.** Gradle setup, modules, version catalog, theme tokens (colors, Doto font, typography), launcher manifest (`HOME` + `DEFAULT` intent filters, `singleTask`), empty home that boots as the default launcher, CI.

**Phase 2: Core data.** `LauncherApps` app list with live updates, Room schema (items, pages, folders, hidden), DataStore settings.

**Phase 3: Home grid.** Pager, grid placement, dock, drag and drop, folders, edit mode, page management.

**Phase 4: Drawer & search.** Layouts, fast scroller, search, hidden apps, shortcuts menu, badges, Private Space.

**Phase 5: Icon engine.** Resolution pipeline, dot-ify renderer, bundled icon set + generator tool, styling options, icon pack support.

**Phase 6: Wallpaper engine.** Engine interface, 10 wallpapers (AGSL + Canvas fallback), customizer UI, in-launcher render, live wallpaper service, user image import.

**Phase 7: Widgets.** Host + picker, then bundled widgets in this order: Dot Clock, Battery, Date, Quick Settings, Life in Dots, Pedometer, Away Time, Breathing Break, Timer, Sticky Note, LED Sign, Snake, Music, Recorder, Search Bar.

**Phase 8: Gestures & settings.** Gesture mapper, full settings tree, backup/restore, first-run flow.

**Phase 9: Polish.** Haptics, motion tuning, baseline profiles, accessibility pass, benchmarks, README with screenshots, F-Droid metadata, licenses screen, release signing doc.

At the end of each phase: run `./gradlew build lint detekt test`, install on an emulator (API 34/36) and, if available, a physical device, and write a short `docs/phase-N.md` note of what was done and what is next.

---

## 11. Repository deliverables

- `README.md` (what it is, screenshots, feature list, install, build, contribute, unofficial-project disclaimer)
- `LICENSE`, `THIRD_PARTY_LICENSES.md`, `PRIVACY.md` ("collects nothing, sends nothing")
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
- `docs/architecture.md`, `docs/wallpaper-engine.md`, `docs/icon-pipeline.md`
- `fastlane/metadata/android/en-US/` for F-Droid
- `.github/workflows/ci.yml`, issue and PR templates

---

## 12. Definition of done

- Installs from a single APK, sets as default launcher in under 30 seconds, looks finished with zero extra downloads.
- All 10 wallpapers, the bundled icon set, the dot-ify fallback, and all listed widgets work offline.
- Merged manifest contains no `INTERNET` permission.
- Settings cover every option in section 7, with backup/restore round-tripping cleanly.
- Smooth at 120 Hz on a mid-range device; macrobenchmarks pass thresholds.
- No Nothing-owned assets anywhere in the repo.

---

## Notes for the maintainer

- This spec is based on public information about Nothing OS 4.1 / 5.0 features and widgets, not a pixel-level scrape. After Phase 3, compare screenshots side by side with a real Nothing Phone (4a) and tune spacing, radii, and colors in `:core:designsystem`; that is how to reach the closest possible match.
- "Same look" is achievable through original dot-matrix artwork. Copying Nothing's actual wallpaper files or icon art into a public repo is what would put the project at risk of takedown.
