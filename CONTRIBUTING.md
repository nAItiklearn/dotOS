# Contributing to DotOS Launcher

Thanks for helping. DotOS is a small, strict project: it has two hard rules and a handful of conventions. Please read this before opening a pull request, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

This is an unofficial, fan-made project, not affiliated with Nothing Technology Limited. That is only credible if the rules below are kept.

## Hard rules

1. **Original assets only.** Every wallpaper, icon, glyph, font, image and string in the repository must be original work or come from an open-source asset under a license that allows redistribution (for example SIL OFL for fonts, Apache 2.0 or CC0 for other things).
   - Do not copy, trace, screenshot-and-redraw or "closely recreate" Nothing's wallpapers, icon artwork, logos or the Ndot / NType fonts.
   - Do not draw look-alike logos for third-party brands (WhatsApp, Spotify, YouTube and so on). Brand apps use their own monochrome layer or the automatic mask path. `IconMapTest` fails if well-known brand packages are mapped to a bundled glyph.
   - Do not use the Nothing name or logo in the app, its icon or its strings.
   - Reference screenshots of other operating systems must never be committed. `.gitignore` already excludes `/*.png`, `/*.jpg`, `/*.jpeg`, `/*.webp`, `/reference/` and `/.gen/`; keep local reference material there.
   - A new font or library must be added to [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md), its license text to `licenses/` (fonts) and the in-app list in `feature/settings/.../screens/AboutScreen.kt`.
2. **No network, ever.** Do not add `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, location or advertising-id permissions, and do not add any dependency that needs them: no analytics, crash reporters, ad SDKs, Firebase, Play Services, remote config, update checkers or image loaders that fetch URLs. The app module removes `INTERNET` and `ACCESS_NETWORK_STATE` that libraries try to merge in, and CI inspects every APK and fails on any forbidden permission.

Other expectations:

- New runtime permissions are requested on first use of the feature, never at startup, and the feature must degrade gracefully when denied. Update the permission table in [README.md](README.md) and [PRIVACY.md](PRIVACY.md) in the same pull request.
- Anything new that stores data keeps it in app-private storage. Update [PRIVACY.md](PRIVACY.md).
- No signup, paywall, ads or tracking of any kind.
- Mark unfinished things as such in docs. Do not document features that are not in the code.

## Getting set up

You can contribute without installing anything locally: push a branch and let the **CI** workflow build it, then download the `dotos-apks` artifact. To build on your machine you need JDK 17 and the Android SDK (platform 36); the Gradle wrapper does the rest.

```bash
./gradlew assembleDebug testDebugUnitTest lintRelease
```

Project structure and data flow: [docs/architecture.md](docs/architecture.md). Visual targets and token values: [docs/design-reference.md](docs/design-reference.md).

## Code style

- Kotlin, Jetpack Compose, 4-space indent, 140-column lines, LF line endings, final newline. [.editorconfig](.editorconfig) holds the settings (ktlint `android_studio` style; wildcard imports are allowed; `@Composable` functions may use PascalCase). ktlint and detekt are not wired into the Gradle build or CI yet, so match the surrounding code by hand.
- Dependency injection is a hand-written graph in `app/.../AppGraph.kt`. Do not add a DI framework. Feature modules receive what they need through small interfaces (`HomeDeps`, `SettingsDeps`) implemented in the `app` module.
- Keep declarations `internal` unless another module needs them, as the existing code does.
- Dot rendering uses one `Canvas` or shader per widget or wallpaper, never one composable per dot. Animated values are read in draw or layer blocks, not in composition.
- Anything that touches the main thread on the launch path must be fast. Icon decoding, disk and database work run on background dispatchers.
- Honor reduce-motion (`LocalReduceMotion`) for decorative animation and the haptics setting (`rememberDotHaptics`).
- Most user-visible text is currently an inline English literal (the app name lives in `app/src/main/res/values/strings.xml`); moving text into resources is welcome. Keep accessibility labels (`contentDescription`) on interactive elements.
- Unit tests use JUnit 5 and run on the JVM (`testDebugUnitTest`). Add tests for pure logic (parsers, search ranking, layout math, codecs). Existing examples: `SettingsCodecTest`, `AppSearchTest`, `IconMapTest`.

## How CI works

`.github/workflows/ci.yml` runs on every push to `main`, every pull request and on manual dispatch (older runs of the same ref are cancelled). On Ubuntu with Temurin JDK 17 it:

1. validates the Gradle wrapper,
2. runs `./gradlew --stacktrace assembleDebug assembleRelease testDebugUnitTest lintRelease` (lint aborts on errors),
3. runs the **Privacy check**: `aapt2 dump permissions` on every APK, failing on `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` or `AD_ID`,
4. uploads the APKs as the `dotos-apks` artifact (and build reports as `reports` if the run failed).

If maintainer signing secrets are present the release APK is signed with them; otherwise (forks, pull requests) it is signed with the debug key.

`.github/workflows/screenshots.yml` runs after a successful CI run on `main` (or manually). It installs the release APK on an API 34 emulator, drives the main screens with `.github/scripts/capture.sh` and uploads them as the `screenshots` artifact. Use it to check UI changes visually.

A pull request is ready when CI is green and the checklist in the [pull request template](.github/PULL_REQUEST_TEMPLATE.md) is complete.

## Adding a wallpaper

Wallpapers live in `feature/wallpaper/src/main/java/com/dotos/feature/wallpaper/wallpapers/`. Read [docs/wallpaper-engine.md](docs/wallpaper-engine.md) first; `DotField.kt` is the shortest complete example.

1. Create an `object` that implements `DotWallpaper` (`engine/DotWallpaper.kt`):

   ```kotlin
   object Ripple : DotWallpaper {
       override val id = "ripple"            // unique, lowercase, snake_case; saved in user settings
       override val name = "Ripple"
       override val colorSlots = listOf(ColorSlot("bg", "Background"), ColorSlot("fg", "Dots"), ColorSlot("accent", "Accent"))
       override val params = listOf(WallpaperParam("spacing", "Dot spacing", 10f, 36f, 17f))
       override val presets = listOf(Palettes.midnight.preset(), Palettes.paper.preset())   // first = default

       override val shaderSource = Agsl.COMMON + """
           layout(color) uniform half4 cBg;
           uniform float pS;
           half4 main(float2 frag) { /* ... */ }
       """

       @RequiresApi(33)
       override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
           shader.setColor("cBg", frame.color("bg"))
           shader.setFloatUniform("pS", frame.param("spacing"))
       }

       override fun DrawScope.drawFallback(frame: WallpaperFrame) { /* same picture with Canvas calls */ }
   }
   ```

2. Every wallpaper needs a `bg` colour slot (it decides whether home content is drawn dark or light) and a Canvas fallback that matches the shader. The shader must declare only uniforms that `bindUniforms` sets: setting an undeclared uniform throws, and the wallpaper then drops to the fallback. Do not allocate per frame in `drawFallback`; keep precomputed data in `frame.cache`.
3. Register the object in `wallpapers/AllWallpapers.kt` (list order is picker order).
4. The settings gallery groups wallpapers by id prefix in `gallerySection()` (`feature/settings/.../ui/WallpaperOps.kt`); ids that match no prefix appear under "More".
5. Ship 4 to 6 named presets. Wallpapers must be original designs, with no reference images embedded.

`FlutedBloom.kt` and `GrainTotem.kt` are examples of wallpapers that exist in the source but are not registered yet.

## Adding a built-in widget

Widgets live in `feature/widgets/src/main/java/com/dotos/feature/widgets/dot/`. See `SearchWidget.kt` (small) and `ClockWidget.kt` (several faces).

1. Create an `object` implementing `DotWidgetProvider` (`api/DotWidgetProvider.kt`):

   ```kotlin
   object HelloWidget : DotWidgetProvider {
       override val kind = "hello"                       // unique and stable: stored in the database
       override val title = "Hello"
       override val description = "One line shown in the picker."
       override val sizes = listOf(WidgetSize(2, 2))     // sizes offered in the picker, in grid cells
       override fun shape(size: WidgetSize) = WidgetShape.ROUNDED   // ROUNDED, PILL or CIRCLE

       @Composable
       override fun Content(instance: WidgetInstance, modifier: Modifier) {
           val style = LocalWidgetStyle.current
           Box(modifier.fillMaxSize()) { /* draw with style.content, style.accent ... */ }
       }
   }
   ```

2. Register it in `dot/WidgetBatchA.kt` (everyday glanceables) or `dot/WidgetBatchB.kt` (utilities). List order is picker order. `DotWidgets` throws at startup if two providers share a `kind`.
3. The launcher draws the surface (fill, corner radius, hairline); `Content` draws only what is inside and must fill `modifier`. Use `LocalWidgetStyle` colours, not hard-coded ones.
4. Previews have `instance.isPreview == true` (`itemId < 0`) and must have no side effects: no permission prompts, no writes, no sensors running.
5. Persist state through `LocalWidgetHost.current` (`state` / `putState`, keys prefixed with `instance.stateKey`). Open other apps with `host.startActivity`. If the widget needs a runtime permission, list it in `permissions`, ask through `host.requestPermission`, add the `<uses-permission>` to `feature/widgets/src/main/AndroidManifest.xml`, and update the README and PRIVACY tables.
6. Update on events (`rememberTicker`, broadcasts, observed settings), not on polling timers.
7. Add faces by overriding `faces` and branching on `instance.face`. Give every interactive element a `contentDescription`.

## Adding an icon glyph

Bundled glyphs are original line drawings made with the `GlyphScope` DSL (`feature/icons/.../glyph/GlyphScope.kt`). Glyphs are for generic and system categories (phone, camera, calculator, notes), not for brands.

1. Add a `Glyph` to one of the lists in `glyph/` (`SystemGlyphs`, `CommunicationGlyphs`, `MediaGlyphs`, `ToolGlyphs`, `LifestyleGlyphs`, `DotMatrixGlyphs`):

   ```kotlin
   Glyph("notes") {
       rect(5.5f, 4f, 18.5f, 20f, radius = 2.5f)
       line(9f, 9f, 15f, 9f)
       line(9f, 12.8f, 15f, 12.8f)
   }
   ```

   Coordinates are on a 24 by 24 grid; keep artwork inside 2..22 and optically centred. The default stroke is 2 units with round caps. Draw in a single colour and never rely on transparency for meaning, because the glyph is turned into an alpha mask and then styled as line or dot-matrix. Helpers: `line`, `polyline`, `circle`, `dot`, `rect`, `arc`, `path`, `clearCircle`, `clearRect`, `dotRow`, `rotated`, plus `fillStroke`, `cloud`, `heart`, `sparkle`, `gear` in `GlyphHelpers.kt`.
2. Ids are unique, lowercase snake_case. `DotGlyphs` throws on duplicates.
3. Map apps to it in `feature/icons/src/main/assets/icon_map.json`. Keys are package names (`"com.example.notes": "notes"`). The engine also looks up a flattened component name such as `com.example/.MainActivity` before the package, but `IconMapTest` currently requires every key to look like a package name. Keys must be unique, and every value must be an existing glyph id.
4. Run `./gradlew :feature:icons:testDebugUnitTest`. `IconMapTest` checks glyph ids, duplicate keys, package-name shape, that brand apps are not mapped, and that registry and source agree.

## Reporting problems and proposing features

Use the issue templates. For anything that touches privacy or permissions, say so in the title. Security-sensitive reports can be opened as a minimal public issue asking for a private channel.

## License of contributions

By contributing you agree that your work is licensed under GPL-3.0-or-later, the license of this project.
