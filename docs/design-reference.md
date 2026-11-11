# DotOS Design Reference (Nothing OS 4.1 / 5.0 home-screen *style*, original assets only)

Purpose: concrete visual tokens, screen layouts and shader specs so engineers can build DotOS without guessing.
Nothing's wallpapers, icons and fonts are described only, never copied. Use Doto / Space Mono / Inter and our own art.

## 0. Sources, scale and confidence
- Evidence: 13 reference screenshots in `D:\dotOS` (Phone (4a) settings + home, widget picker, polishot 3-page home on an older Nothing OS build) plus public coverage. `download (5).png` is fully transparent (ignore).
- Public sources actually retrievable: nothing.tech/nothing-os (5.0 page), 9to5Google (5.0 changelog, 4.1 on Phone (4a)), Beebom 5.0 feature list, Android Police and BGR 5.0 hands-on, Nothing support articles, Nothing Playground. They confirm: wallpaper-aware colours, tinted icons, transparent ("Lucent" / Translucent) widgets and folders, Geist font option (alongside Roboto), "Arrange" batch editing, Micrographics and Gooey clocks, Away Time, Collector, Glyph Timer, Essential Apps drawer and builder, Relaxation Hub (Breathing Break: Focus/Relax/Calm), Smart (iOS-like) drawer, Private Space page, "Freeze home screen layout".
- **No retrievable official or press source describes Atmosphere or Glass.** Sections 7-8 are derived from the screenshots only. Treat numbers as starting values to tune by eye.
- Scale convention: reference screen width = 411 dp. In the polishot one screen is ~298 px wide, so 1 px = 1.38 dp (the 108 dp gesture pill measured ~78 px, which cross-checks). Values marked ~ are +/-10%. Unobserved items are marked (proposed).

## 1. Home grid metrics (dp, 411 dp wide)
| Token | 4-col (polishot) | 5-col (Phone (4a)) | Formula / note |
|---|---|---|---|
| Side margin | 30 | 31 | ~7.3% of width |
| Gutter (gap between plates) | 21 | 24 | use 22 for both |
| 1x1 plate (circle) | 72 | 51-52 | (W - 2*30 - (n-1)*22) / n |
| Cell pitch (plate+gutter) | 93 | 74 | rows use the same pitch (square cells) |
| 2x1 widget | 165 x 72 | 126 x 52 | stadium or rounded rect |
| 2x2 widget | 165 x 165 | 126 x 126 | circle or rounded square |
| 4x2 / 5x2 widget | 351 x 165 | 350 x 126 | photo / weather / list |
| First row top | ~59 below screen top | ~58 | status bar + ~32 dp |
| Page dots | in the gutter between last row and dock | | ~8 dp under grid, ~8 dp above dock |
- Corner radii: round widgets and icons = full circle. 2x2 rounded squares = **~10% of side (16-17 dp at 165, 13 dp at 126)**. 4x2 photo / list widgets = 24-26 dp. 2x1 data pills (screen time, steps, REC, battery) = full stadium; some 2x1 text widgets use r ~17-22 dp. Folder tile = ~20 dp (12% of 170 dp). The spec's "28-32 dp widgets" is too round for 2x2; use the above, keep 28-32 dp only for sheets and previews.
- Dock: no backing plate. Plates are the same size as grid plates, 4 (4-col) or 5 (5-col), gap = gutter. Bottom of dock ~30 dp above screen bottom (gesture inset included). Page dots sit between grid and dock.
- Page indicator: dot pitch 12 dp, active 7 dp #FFFFFF, inactive 5 dp white @ 45%. Settings carousels use the same dots.
- Icon plate vs glyph: Nothing-style plate is a **solid circle**, glyph is a white mono outline or filled shape. Glyph = 38-40% of plate on the polishot's pack, **48-54% on the Phone (4a) line-icon pack**; default 48%, user slider 40-60%.
- App labels: OFF by default in every screenshot (toggle "App label - Display app label on home screen"). When on (proposed): 12 sp Medium, 1 line, white, 6 dp under plate, row pitch +18 dp.
- Notification badge on icons: dot diameter ~11% of plate (8 dp on 72, 18 dp on a 2x2 icon), centred on the 45 deg point of the circumference (top-right), colour #E8E8E8. Widgets use a **red brand dot** (#D71921, ~11 dp on 2x2) at top-left inset ~19 dp (Date tile); on analog clock at the 10 o'clock position, radius 0.8R, diameter 0.13R.
- Rearranging ("Arrange"): picked item gains depth/transparency (press scale 1.06, 12 dp soft shadow, 85% alpha: proposed values); multi-select with check dots; batch drag to another page.
- Folder: a lighter tile (#2D2D2D, r 20 dp) with a 3x3 mini-grid of dark circles: padding 7 dp, mini plate 47 dp, gap 7.5 dp on a 170 dp tile; 9th cell shows a 4-dot "more" glyph. Open folder popup not observed (proposed: rounded popup, same plates).

## 2. Colour tokens
| Token | Hex | Where observed |
|---|---|---|
| plate.dark (Nothing OS 3/4 default) | #1A1A1A..#1C1C1C opaque | polishot icons, widgets, dock |
| plate.black (Phone (4a), beige theme) | #0A0A0A..#0B0B0E | REC/battery pills, search pill, dock, widgets |
| folder tile / pressed plate | #2D2D2D | folder, picker cards ~#222222 |
| widget rim | white @ 16-24%, 1 dp (outer hairline) | faint light ring around black widgets on beige wallpaper |
| ink primary | #FFFFFF | glyphs, titles |
| ink secondary | #8A8A8A..#9A9A9A | subtitles, inactive dots |
| signal red | #D71921 | brand dot, "Wed" in Date widget, recorder dot is orange-red #FF4A2B |
| settings background | #000000 (OLED) | all settings screens |
| settings panel / sheet | #0E0E0F..#141414; cards #222222; search field #242424 | wallpaper strip panel, widget picker |
| selected control | fill #F2F2F2 + dark content | "5" grid button, segmented "Basic colours", switch track |
| switch (on) | track #F5F5F5, thumb dark tone-matched to wallpaper (#221F20..#564748), 52 x 28 dp | App label, Colours toggle |
| selected ring | 2 dp white ring, 3 dp gap | icon pack, colour swatch, wallpaper tile |
No gradients on chrome; depth comes only from wallpaper, plates and the optional rim.

## 3. Typography
- UI: neutral grotesk (Inter in DotOS; Nothing uses Roboto and optional Geist). Settings title 16 sp Semibold centred; row title 18 sp Medium; row subtitle 13 sp #9A9A9A; round-button labels 13 sp Medium; sheet section headers 14 sp Medium #B0B0B0; widget card labels 12 sp Medium.
- Dot-matrix (Doto): grid-size digits "4"/"5" (22 sp), clock digits, Away Time "3H 22M" (H/M small), steps "4,170" with caption "TOTAL TODAY  41%" in 9 sp caps wide-tracked, Quick Look 4 stacked lines (THURSDAY / 29 OCT / SUNNY / sun icon 15 deg) ~16 sp caps, Camera "50MM PHOTO", weather "20" ~28 sp.
- Serif accent: Date widget uses a high-contrast bold serif numeral (~80 sp, "26") with "Wed" 14 sp in red; stand-in: Playfair Display or DM Serif Display (OFL). Pill "P3"-style text widget also serif ~22 sp.
- Dot rendering rule: dots nearly touch (pitch = 1.15 x diameter); a 2x2 dot-art glyph (cloud, camera, face) spans ~50% of widget width, dot diameter ~4% of widget side. One Canvas per widget, never one composable per dot.

## 4. Widget catalogue (shape, fill, content)
| Widget | Size/shape | Content |
|---|---|---|
| Weather | 2x2 circle (also 5x2 rounded rect w/ 6-day forecast) | dot-matrix cloud/moon icon; wide: icon + "20" + hi/lo, place + "Mostly clear" right, 6 tiny day columns |
| Date | 2x2 rounded square | red dot TL, "Wed" red serif TR, giant white serif day number |
| Clock analog | 2x2 circle | no ticks; white capsule hour hand (len 0.4R, width 0.14R), thin grey minute line (2 dp), red dot at 10 o'clock |
| Clock digital | 2x2 rounded square | dot digits stacked "22 / 10"; world clock face adds "LONDON" caption |
| Away Time / Screen time | 2x1 stadium; 2x2 face | "3H 22M" dots; or dot-art phone face |
| Pedometer | 2x1 stadium | "4,170", caption "TOTAL TODAY", percent right |
| Recorder | 2x1 stadium | orange-red 24 dp dot + "REC" 16 sp mono caps |
| Compass | 1x1 circle | dot arrow + "W" |
| Battery | 2x1 stadium | segmented bar (dot cells, last cells red when low) / 2x2 circle with device dot icon |
| Quick Settings tile | 1x1 or 2x2 | single toggle (auto-rotate shown), tile follows wallpaper colour option |
| Sound-wave / audio | 2x2 rounded square | white vertical bars forming a disc, red dot TR |
| Calm (Breathing) | 2x2 rounded square | 8 radial 2 dp ticks, label "Calm" 11 sp TL |
| Photo / Collector | 4x2 r25 | image (or photo sticker with emoji overlay) |
| Shortcut icon | 1x1/2x2 circle plate | dot-art glyph (book, chat bubble, camera) |
| Third-party (e.g. task-list widget) | any | keep their own pixels; DotOS only sets plate/clip behind |
Mask shapes seen as sticker/placeholder plates: 6-petal flower and 8-scallop cookie, fill #3A3A3A.

## 5. Widget picker and face carousel
- Picker = bottom sheet over dimmed home: top radius 28 dp, bg ~#0E0E0F, drag handle 32 x 4 dp centred, **search field** 44 dp stadium (#242424), magnifier left, centred placeholder "Search widgets" 14 sp.
- Grid: 2 columns, gutter 12 dp, card ~160 x 160 dp, r 24 dp, fill #222222. Card = small outline app glyph (14 dp) top-left inset 12 dp, live preview anchored bottom-right and **clipped by the card** (preview bleeds off ~30%), black #0A0A0A preview shape. Label below card, centred, 12 sp Medium. Alphabetical list seen: Battery, Camera, ChatGPT, Clock, Compass, Countdown, (a note entry and an audio-wave entry), Pedometer, Photos, Quick Look, Quick Settings, Recorder, Screen Time, Weather.
- Face carousel (tap a card): sheet title (e.g. "Clock") 16 sp centred, close X right (24 dp). Horizontal snap carousel of 2x2 face previews (130 dp, black, r 20 dp), centred card scale 1.0, neighbours 0.8 peeking from both sides. Under centred card: name 15 sp Semibold ("Digital"), size "2x2" 12 sp #8A8A8A, one-line description ("Display the current time") 12 sp. Behind: home with blur + dim. Add on tap of centred card / drag.

## 6. Settings screens (all #000 background, 24 dp horizontal padding)
**Customisation entry**: long-press empty home -> sheet: Wallpaper & style, Widgets, Home settings; "Arrange" starts batch edit.
**Home screen (customiser)**: top bar back arrow, title "Home screen", trailing support icon. Preview carousel of two cards (Lock screen / Home screen), card ~160 x 355 dp, r 24 dp, active card centred, neighbour peeks ~40 dp; home card shows wallpaper + mini dock (5 circles) + mini search pill. Below: **three round buttons** Colours (split pie icon), Icon Pack (donut), Layout (#), 52 dp circles #1A1A1A, 24 dp white glyph, 13 sp label, evenly spaced thirds. Then 2 page dots. Then a **wallpaper panel** (margin 16 dp, r 28 dp, fill ~#111, padding 12 dp): horizontal strip of portrait thumbnails 83 x 142 dp r 14 dp gap 8 dp (4th peeks, selected has 2 dp ring), footer centred "More wallpapers" with ring-ellipsis icon, 48 dp tall.
**Home screen layout**: title "Home screen layout"; preview card 160 x 340 dp (wallpaper + dock + search only). Rows: *App Grid / "5 columns"* with two 48 dp circle buttons "4" "5" in Doto digits (selected = #F2F2F2 fill + black digit, other #1A1A1A + white); *App label / "Display app label on home screen"* switch; *Search bar / "Essential Search"* (value as subtitle, opens chooser). Row height ~72 dp.
**Colours**: preview carousel (left: home preview where every plate type is a black shape on the wallpaper; right: palette preview, dark card over a 5-col grid of tonal circles, one white = selected tone). **Segmented control** full width, 40 dp stadium, track #101010: "Wallpaper colours" | "Basic colours" (selected segment #E8E8E8, dark text, 13 sp Medium). **Swatch row**, 48 dp circles gap 14 dp, scrolls: first = "None" (quadrant white/grey/black monochrome icon) with ring; pastel set e.g. #FCFAD8 cream, #FDF9AF butter, #FDFD98 lemon, #D9FED3 mint, then more. Last row: label (occluded; the icon-tint / "Themed icons" toggle) + switch on right.
**Icon Pack**: preview card then a single row of 64 dp circles, pitch 76 dp: Add (white fill, thin +), Default (selected ring, grey donut), Nothing, Dots Iconpack, Nothing Icons; label 12 sp, up to 2 lines. Selecting live-updates the preview. (DotOS: Default, Dot-matrix, Line, Original, + Add pack.)
**Wallpaper gallery** (sheet, top r 28 dp, bg ~#0A0A0A): title "Wallpaper gallery" 14 sp centred, close X. Sections 14 sp #B0B0B0: *Studio* (a "Create" tile with sparkle + 2 generated art thumbs), *Brand set* (3 thumbs + "More" tile), *Colours* (3x2 solid tiles: #010204, #4E4247, #EAECE2, #141416, #797072, #FCFEFE). Picker grid: 3 columns, gap 8 dp, tiles ~110 x 185 dp r 16 dp, selected = 2 dp white ring with inset. DotOS: "Create" = procedural generator (no AI, no network).
**Wallpaper target screen**: two preview cards side by side (Lock screen, Home screen), 2 dp white outline, r 28 dp, labels 14 sp below. Below: two 60 dp round feature buttons **Atmosphere** (three wavy lines icon) and **Glass** (dot-hexagon flower icon), each with label + state ("Off"/"On" 12 sp #8A8A8A). On = white-filled circle with dark icon; off = bare icon. Helper text when Atmosphere on: "Atmosphere will use the same wallpaper for both Lock & Home Screen for a seamless transition." DotOS: same two buttons, helper text rewritten (home only; lock export optional).

## 7. Atmosphere effect
**Observed**: with Atmosphere on, the home preview changes from the sharp wallpaper (flat off-white #EEF4E9 with dark taupe circle, dithered rectangle, grey pill and crosshair marks) to a **diffuse brushed-titanium greige**: huge feathered tonal blobs (each ~40-60% of screen width, edge falloff ~20-25% of width), a bright cream bloom upper-centre, a dark taupe pool upper-left and lower-left, nothing sharper than ~15% screen width. Surface has fine **monochrome film grain** (~4-6% amplitude) and a very faint **vertical brushed fibre** texture (1 px wide, 40-200 px tall streaks, ~2%). Colours come straight from the source wallpaper palette (lightest cream -> darkest taupe), so lock and home look like one object at two focus levels ("seamless transition"). The same treatment on a dark source gives espresso browns (#674F43 .. #1A1918). Motion is not visible in stills: implement as slow drift.
**AGSL spec** (API 33+, Canvas radial-gradient fallback for 29-32; one full-screen pass, `RuntimeShader`):
```
// helpers hash/noise1/snoise: write our own (value + 2D simplex noise); no data-dependent array indexing in SkSL
uniform float2 uSize; uniform float uTime;           // seconds
uniform half3  uBase;                                // mid tone of the source palette
uniform half3  uBlobCol[6];                          // per-blob colour, picked from the 5-colour palette on the CPU
uniform float4 uBlob[6];                             // xy centre 0..1, z radius 0.22-0.55 (of width), w opacity 0.6-0.9
uniform float  uGrain;      // 0.05  (0..0.12)       uniform float uFibre; // 0.02
half4 main(float2 p){
  float2 uv = p/uSize; uv.y *= uSize.y/uSize.x;                  // aspect-correct
  uv += 0.025*float2(snoise(uv*1.3+uTime*0.010), snoise(uv*1.3+9.7-uTime*0.010)); // domain warp
  half3 c = uBase;
  for (int i=0;i<6;i++){                                          // drift: Lissajous, 45-90 s period
    float2 q = uBlob[i].xy + 0.07*float2(sin(uTime*(.07+.013*float(i))+float(i)*1.7), cos(uTime*(.05+.011*float(i))+float(i)*2.3));
    float d = length(uv - q*float2(1,uSize.y/uSize.x)) / uBlob[i].z;
    c = mix(c, uBlobCol[i], half(exp(-d*d*2.2)*uBlob[i].w));      // gaussian blob over base
  }
  float fibre = (hash(floor(p.x)) - .5) * smoothstep(0.,1.,noise1(p.y*0.012 + floor(p.x)*3.1)); // vertical streaks
  float grain = hash2(p + floor(uTime*24.)) - .5;                 // 24 fps grain, or static if reduce-motion
  c += (fibre*uFibre + grain*uGrain);
  return half4(clamp(c,0.,1.),1.);                                // vignette optional: *= 1-0.06*dot(uv-.5,uv-.5)
}
```
Rules: 6 blobs (2 light, 2 mid, 2 dark) coloured from the 5-entry palette (section 10), palette sorted by luminance, light variant keeps L 62-93%, dark variant L 6-35% (remap on the CPU). Cap 30 fps when idle, 60 fps while scrolling, 0 when launcher hidden / screen off / battery saver. Scroll parallax: blob offset = 0.12 x page scroll; optional gyro <= 12 dp. Reduce-motion freezes time and grain. Home icons need no scrim because plates are opaque. In DotOS the lock screen cannot be styled; optional export of the same frame as a static PNG via `WallpaperManager` FLAG_LOCK.

## 8. Glass effect (fluted / reeded glass)
**Observed** (wallpaper thumbs, customiser preview): the wallpaper is viewed through **vertical ribbed glass**. Every rib acts as a cylindrical lens: the image behind is smeared horizontally and shifted rib to rib, so shapes become stretched "drips" with ragged vertical edges (blue flower on orange, purple anemone on white, black bird silhouette on white). Rib boundaries show a thin dark line with a faint highlight beside it; a second slower alternation every ~2 ribs. FFT of the thumbnails gives a rib pitch of ~7-10 dp (about 45 ribs across 411 dp) plus an ~18 dp coarse modulation. Some wallpapers (selected orange tile and the home preview) show the same effect **rotated: horizontal ribs**, so orientation is a parameter. Plain gradients stay almost unchanged except for the rib shading.
**AGSL spec** (`RuntimeShader` with `uniform shader image` = wallpaper bitmap or the Atmosphere pass rendered to a layer):
```
uniform shader image; uniform float2 uSize;
uniform float uPitch;   // px, 9 dp * density (range 6-14 dp)
uniform float uMag;     // 0.18 (lens compression: each rib shows 18% of its width => ~5x stretch), range 0.1-0.6
uniform float uJitter;  // 0.35 per-rib phase offset in ribs (the "smeared drip" look), range 0-1
uniform float uShade;   // 0.22 edge darkening, range 0-0.4
uniform float uVert;    // 1 = vertical ribs, 0 = horizontal
half4 main(float2 p){
  float2 q = uVert>0.5 ? p : p.yx;                   // work in rib space
  float cell = floor(q.x/uPitch), u = fract(q.x/uPitch)*2.-1.;       // u in -1..1 across rib
  float cx = (cell+.5)*uPitch + (hash(cell)-.5)*uJitter*uPitch;      // rib centre + jitter
  float sx = cx + u*uPitch*.5*uMag/(1.-.15*u*u);                     // cylinder-lens sample position
  float2 s = uVert>0.5 ? float2(sx,p.y) : float2(p.x,sx);
  half4 c = (image.eval(s) + image.eval(s+(uVert>.5?float2(0,1.5):float2(1.5,0))*uPitch*.1) + image.eval(s-(uVert>.5?float2(0,1.5):float2(1.5,0))*uPitch*.1))/3.; // short blur along the rib
  float edge = smoothstep(.78,1.,abs(u));                            // dark seam
  float hl = (1.-smoothstep(0.,.15,abs(u+.62)))*.06;                // specular highlight stripe
  c.rgb = c.rgb*(1.-uShade*edge) + hl;
  c.r = mix(c.r, image.eval(s+float2(.6,0)).r, .5);                  // optional 0.6 px chroma split
  return c;
}
```
Rules: Glass is applied to the wallpaper layer only (never to icons or widgets), toggles independently of Atmosphere, and when both are on Atmosphere renders first into an offscreen layer that Glass samples. Static unless gyro tilt is enabled (tilt shifts `uJitter` phase by +/-0.1 for a subtle shimmer). Precompute nothing per frame beyond uniforms; for wallpaper-picker thumbnails render once to a bitmap. Fallback below API 33: render once into a Bitmap with a per-column loop (cache by wallpaper id + params hash).

## 9. Features worth matching (ranked by user impact)
| # | Feature | Third-party launcher note |
|---|---|---|
| 1 | Black circle-plate icons + icon packs (Default / dot / line / Add pack) | Icon engine pipeline in spec 4.1; `AdaptiveIconDrawable.monochrome` for tint; appfilter.xml packs |
| 2 | Dark-plate widget family (circle, rounded square, stadium) with dot-matrix content and one red dot | Bundled Glance/Compose widgets; third-party widgets only get plate/clip behind (cannot restyle RemoteViews) |
| 3 | Wallpaper-aware colours + Basic colours + "Themed icons" tint toggle | Palette from wallpaper in launcher; user-selectable swatches; works without Material You |
| 4 | Atmosphere + Glass wallpaper modes | In-launcher AGSL layer (sections 7-8); optional live wallpaper service for system-wide home; cannot restyle the real lock screen |
| 5 | Customiser screen (preview cards, Colours / Icon Pack / Layout buttons, wallpaper strip) | Pure Compose; live preview = scaled-down real home composable |
| 6 | App Grid 4/5 columns, App label toggle, dock without backing, dots in gutter | Trivial; extend range to 4-7 per spec |
| 7 | Bottom "Search" pill (Essential Search) under dock | Local search only; pill #0A0A0A r full 48 dp |
| 8 | Widget picker: search, 2-col preview cards, face carousel with size + description | `AppWidgetProviderInfo.loadPreviewImage` / previewLayout (API 31+); bundled faces rendered natively |
| 9 | Long-press menu: Customisation / Widgets / Home settings + "Arrange" batch edit | Fully doable; multi-select drag across pages |
| 10 | 3x3 mini-plate folders | Doable; popup style free |
| 11 | Smart (auto-category) drawer + classic list, Private Space page, hidden-icons page | `ApplicationInfo.category`; Private Space via `LauncherApps` on API 35+ (needs default launcher); hidden apps in Room |
| 12 | Freeze home layout | Single flag |
| 13 | Translucent ("Lucent") folders / widget plates | `RenderEffect` backdrop blur (API 31+), tint #000 @ 40%, 1 dp hairline white @ 16% (proposed); cannot blur inside third-party widgets |
| 14 | Lift-and-depth when rearranging | graphicsLayer scale + shadow |
| 15 | Away Time, pedometer, breathing (Calm/Focus/Relax), photo sticker widget | Away Time via screen on/off broadcasts; pedometer needs ACTIVITY_RECOGNITION; sticker needs bundled segmentation or manual cut-out (heavy, optional) |
**Impossible or out of scope for a third-party launcher**: lock-screen clocks, Depth Effect, lock widgets, lock/home Atmosphere sync (only static export); Glyph Interface, Glyph Timer, Essential Key (hardware, Nothing SDK); Essential Space, Essential Voice, Essential Apps builder, Wallpaper Studio "Create" (proprietary AI / cloud, violates zero-network); separate Notifications and Quick Settings panels, Quick Settings redesign, Geist/system font, status-bar icon and animation styling, Camera/Gallery restyling (system level); full custom Recents (needs privileged integration, fall back to system recents).

## 10. Default palettes (start values)
Atmosphere palette entries are darkest -> lightest `uPal[0..4]`. Plates stay #0A0A0A or #1A1A1A in every look (as in the references).
| Look | uPal0 | uPal1 | uPal2 | uPal3 | uPal4 | Chrome |
|---|---|---|---|---|---|---|
| **Atmosphere warm-grey / beige** (default) | #685B5A | #928782 | #C2B8AF | #D5CFC4 | #E5E7DC | plate #0A0A0A, ink #FFFFFF, grain 5%, fibre 2% |
| **Dark / espresso** | #121010 | #453735 | #5C473E | #674F43 | #847167 | bg #000000, plate #1A1A1A, ink #FFFFFF, grain 4% |
| **Light / paper** | #7B6F6C | #B0A59C | #CDC5BB | #E1E0D5 | #F2F2F2 | bg #F2F2F2, plate #0A0A0A (stay black) or optional #FFFFFF with #111 glyph, ink #111111, grain 3% |
Extra source colours seen: sharp-wallpaper off-white #EEF4E9, deep taupe #483C3C / #594D4D, mid warm grey #C8C0B6, orange-red art #E85630 / #CC4525 / #661A0B (use only for a "Signal" preset). Accent default signal red #D71921; swatch accents as in section 6.
Presets to ship per wallpaper: "Greige" (default), "Espresso", "Paper", "Ember" (orange ramp #661A0B..#EA8358), "Frost" (blue ramp), "Mono".

## 11. Open questions (verify on a real Phone (4a) before polishing)
1. Exact Atmosphere drift speed and whether grain animates; 2. whether Glass ribs react to tilt or scroll; 3. label occluded on the Colours toggle row; 4. folder-open popup, drawer and search-results visuals (not in references); 5. app label typography when enabled; 6. exact blur radius of Translucent theme; 7. whether plates in light mode invert.
