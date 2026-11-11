# Icon pipeline

Every app icon on the home screen, dock, folders, drawer and App Library goes through `IconEngine` (`feature/icons`). The engine turns an app into a single-colour *mask*, then styles the mask as a line glyph or a dot-matrix glyph. The plate behind the glyph is drawn live by Compose. Nothing here uses the network, and no third-party artwork is shipped: bundled glyphs are original drawings made in code.

## Pieces

| Piece | File | Role |
|---|---|---|
| `IconEngine` | `engine/IconEngine.kt` | Resolution order, caches, background dispatcher |
| `MaskExtractor` | `engine/MaskExtractor.kt` | App icon to normalised alpha mask |
| `IconRenderer` | `engine/IconRenderer.kt` | Mask to line, dot-matrix or original bitmap |
| `IconSpec` and `toIconSpec` | `engine/IconSpec.kt`, `engine/IconSpecs.kt` | Settings to render parameters and cache key |
| `IconPack` | `engine/IconPacks.kt` | `appfilter.xml` icon pack reader |
| `DotGlyphs`, `Glyph`, `GlyphScope` | `glyph/` | Bundled glyph registry and drawing DSL |
| `icon_map.json` | `assets/icon_map.json` | Package to glyph id map |
| `AppIcon` | `AppIcon.kt` | Composable: plate, async bitmap, fade-in |

## Resolution order

`IconEngine.render(app, spec, px)` produces the final bitmap. Which source is used:

1. **Original or icon pack (only if the style asks for it).** If the style is *Original*, or an icon pack is selected, provides a drawable for this component and the style is not *Dot-matrix*, the app's own drawable (or the pack's) is drawn as-is, filling the plate area. Packs are matched by full component name first, then by package. When this branch is taken, steps 2 to 6 do not run (so a per-app override does not apply to an icon that a pack supplies). A pack drawable is still drawn on the plate unless the style is Original.
2. **User override.** A per-app glyph chosen in Settings > Icons > Per-app icons, stored in the `app_overrides` table as `"glyph:<id>"` and held in memory by `IconEngine.setOverrides`.
3. **Bundled DotOS glyph.** `icon_map.json` maps a flattened component name (`ComponentName.flattenToShortString()`) or a package name to a glyph id. The component form is tried first. The glyph is drawn through the glyph DSL into a mask.
4. **The app's monochrome layer.** On Android 13+, if the icon is an `AdaptiveIconDrawable` with a monochrome layer, that layer becomes the mask.
5. **Automatic mask from the foreground.** Otherwise the logo is separated from its background (below).
6. **Generic glyph.** If no mask can be produced, the `app_generic` glyph is used.

Steps 3 to 6 yield a mask. Masks for steps 4 and 5 are cached on disk. The mask is then styled according to the icon style:

| Style | Result |
|---|---|
| Line (default) | Mask tinted with the glyph colour (`SRC_IN`), centred in the plate |
| Dot-matrix | Mask converted to a dot grid (below) |
| Original | The app's own drawable, no plate |
| Themed | Line rendering with a plate and glyph colour derived from one wallpaper tone |

Brand apps are intentionally not in `icon_map.json`: they fall through to steps 4 and 5, so DotOS never ships look-alike brand logos. `IconMapTest` enforces this for a list of well-known packages, and also checks that every mapped glyph exists, keys are unique and package-shaped, and that declared and registered glyphs agree.

## Mask extraction (`MaskExtractor`)

A mask is a 96 by 96 `ARGB_8888` bitmap, white with an alpha channel carrying the shape. All functions are pure and run off the main thread.

- **Adaptive layers** are drawn so the visible 72 of 108 dp window fills the mask.
- **From the monochrome layer** (Android 13+): draw the layer, then normalise.
- **From the foreground** (`fromForeground`):
  - Adaptive icon: draw the foreground layer; the background colour is the average colour of the background layer's opaque pixels (sampled on a coarse grid).
  - Plain drawable: draw it; the background colour is the average of four edge samples, if at least three of them are nearly opaque.
  - For each pixel, `mask = alpha * smoothstep(0.10, 0.28, colourDistance(pixel, background))`, with the distance computed as the root-mean-square difference of the RGB channels in 0..1. If there is no background colour (transparent), only alpha is used.
  - If the result is almost empty (under 2% of pixels lit) or almost full (over 85%), as with full-bleed photo icons, a **luminance mask** is used instead: `alpha = smoothstep(0.12, 0.35, |luminance - mean|)`.
- **Normalisation** (`normalize`, applied to every extracted mask): crop to the bounding box of pixels with alpha above 40, scale so the longest side is 70% of the canvas, and centre. Every glyph therefore has the same optical size, whatever the source icon looked like. Bundled glyphs are drawn directly into a 96 px mask and are not re-normalised, so glyph authors control their optical size.

## Renderer (`IconRenderer`)

`GLYPH_BOX = 0.74`: the mask canvas occupies 74% of the icon bitmap times the user's icon-size scale (`glyphScale`, settings 70 to 130%). Since mask content is about 70% of the mask, the visible glyph is around half of the plate.

**Line:** draw the mask scaled into that box with a `PorterDuffColorFilter(glyphColor, SRC_IN)`.

**Dot-matrix** (`dots`):

1. Grid size `n = dotDensity` clamped to 10..28 (default 18).
2. Divide the mask into `n x n` cells; the cell's coverage is its mean alpha (box filter).
3. Skip cells under 0.14 coverage. Otherwise `t = (coverage - 0.14) / 0.46` (clamped), `f = sqrt(smoothstep(t))`, and the dot radius is `pitch * 0.42 * (0.55 + 0.45 * f)`, so weak edges fade out gradually instead of producing noise.
4. Draw round dots, square dots (about 1.8 times the radius wide) or ring dots (stroke 0.18 of the pitch, radius 0.8 of the dot radius).

**Original:** draw the drawable inside a 2% inset.

## Plates

`AppIcon` draws the plate with `drawPlate(shape, colour)` behind the bitmap: none, circle, squircle (a superellipse with exponent 4.6, cached per size) or rounded square (corner radius 30% of the size). Plate colour is Dark `#1A1A1A`, Black, White `#F2F2F2` (the glyph switches to black on a white plate when the tint is white), the accent, or transparent. The Original style draws no plate. Folders use a `#2D2D2D` plate with a 2 by 2 cluster of mini icons.

## Themed style

`toIconSpec(themedPlate, themedGlyph)` is used when the style is *Themed*. Both colours come from one tone: the user's chosen tone colour (`wallpaper.colors["tone"]`), or the mid-luminance colour of the current wallpaper palette. The plate is the tone mixed 62% toward black; the glyph is the tone mixed 82% toward white. The home screen and the settings previews use the same derivation so previews match.

## Caches and threading

| Cache | Key | Size and lifetime |
|---|---|---|
| Final bitmap (memory) | `appKey \| versionCode \| spec.renderKey \| px \| overridesHash` | `LruCache` sized by bytes: one tenth of the heap, at most 48 MiB |
| Mask (memory) | `appKey \| versionCode \| overridesHash` | `LruCache` of 400 masks |
| Mask (disk) | SHA-1 of `appKey \| versionCode \| v2`, file in `cache/icon_masks/` as PNG | Persists across launches; cleared by Settings > Performance > Clear icon cache. Only masks from steps 4 and 5 are written; glyph masks are cheap to redraw |

`spec.renderKey` hashes the style, glyph colour, dot density, dot shape, glyph scale, icon pack and plate shape. The plate colour is not part of it (the plate is drawn live), which is why changing the plate colour does not re-render icons.

All loading and rendering runs on `Dispatchers.Default.limitedParallelism(3)`, never the main thread. `IconEngine.cached` is a synchronous memory lookup so already-rendered icons appear in the first frame; otherwise `AppIcon` shows the plate and fades the glyph in over 120 ms. `HomeScreen` calls `prewarm` for the dock and the current page before the first frame. Changing overrides evicts the in-memory caches.

## Icon packs

`IconPack` supports the common `appfilter.xml` format used by ADW, Nova and Lawnchair-compatible packs. It reads `xml/appfilter` from the pack's resources, or `assets/appfilter.xml`, and collects `<item component="ComponentInfo{pkg/cls}" drawable="name"/>` entries into a map keyed by the full component and by the package. Installed packs are found by querying the activities that declare one of `org.adw.launcher.THEMES`, `com.novalauncher.THEME`, `com.gau.go.launcherex.theme` or `com.fede.launcher.THEME_ICONPACK`. The pack drawable replaces the DotOS rendering for components it covers, except in the Dot-matrix style, where the mask pipeline still runs. The app works fully without any pack.

## Bundled glyphs

`Glyph(id) { ... }` blocks draw with `GlyphScope` on a 24 by 24 grid (default stroke 2 units, round caps and joins). Primitives: `line`, `polyline`, `circle`, `dot`, `rect`, `arc`, `path`, `clearCircle`, `clearRect`, `dotRow`, `rotated`, plus helpers in `GlyphHelpers.kt` (`fillStroke`, `cloud`, `heart`, `sparkle`, `gear`). Glyphs are grouped in `SystemGlyphs`, `CommunicationGlyphs`, `MediaGlyphs`, `ToolGlyphs`, `LifestyleGlyphs` and `DotMatrixGlyphs` and concatenated in `DotGlyphs.all`, which fails fast on duplicate ids. Glyphs are drawn in one colour into the mask, so they style correctly as line or dot-matrix. See "Adding an icon glyph" in [../CONTRIBUTING.md](../CONTRIBUTING.md).
