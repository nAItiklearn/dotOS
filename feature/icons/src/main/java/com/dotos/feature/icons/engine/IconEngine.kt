package com.dotos.feature.icons.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.glyph.DotGlyphs
import com.dotos.feature.icons.glyph.renderGlyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest

/**
 * Icon resolution pipeline (CLAUDE.md §4.1):
 *  1. user override (glyph id)          2. bundled DotOS glyph (package map)
 *  3. app's own monochrome layer (13+)  4. auto dot-ify of the foreground
 * Masks are cached in memory and on disk; final styled bitmaps in an LRU keyed by spec + size.
 * Never touches the main thread for loading or rendering.
 */
class IconEngine(private val context: Context, private val scope: CoroutineScope) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(3)
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val diskDir = File(context.cacheDir, "icon_masks").apply { mkdirs() }

    private val bundledMap: Map<String, String> by lazy {
        runCatching {
            val text = context.assets.open("icon_map.json").bufferedReader().use { it.readText() }
            Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    @Volatile
    var overrides: Map<String, String> = emptyMap()
        private set
    private var overridesHash = 0

    private val maskCache = LruCache<String, Bitmap>(400)
    private val finalCache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 10).coerceAtMost(48L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val packMutex = Mutex()
    private var pack: IconPack? = null

    fun setOverrides(map: Map<String, String>) {
        overrides = map
        overridesHash = map.hashCode()
        maskCache.evictAll()
        finalCache.evictAll()
    }

    /** Clears rendered bitmaps (e.g. after a style change the keys differ anyway; this frees memory). */
    fun trimRendered() = finalCache.evictAll()

    private fun finalKey(app: AppInfo, spec: IconSpec, px: Int) = "${app.key}|${app.versionCode}|${spec.renderKey}|$px|$overridesHash"

    /** Synchronous memory-cache lookup: lets cached icons appear in the very first frame. */
    fun cached(app: AppInfo, spec: IconSpec, px: Int): ImageBitmap? = finalCache.get(finalKey(app, spec, px))

    suspend fun render(app: AppInfo, spec: IconSpec, px: Int): ImageBitmap? = withContext(dispatcher) {
        val key = finalKey(app, spec, px)
        finalCache.get(key)?.let { return@withContext it }
        val bitmap = runCatching { renderUncached(app, spec, px) }.getOrNull() ?: return@withContext null
        val image = bitmap.asImageBitmap().also { it.prepareToDraw() }
        finalCache.put(key, image)
        image
    }

    /** Warm the cache for icons that will appear on the first frame (home + dock). */
    fun prewarm(apps: List<AppInfo>, spec: IconSpec, px: Int) {
        scope.launch { apps.forEach { render(it, spec, px) } }
    }

    private suspend fun renderUncached(app: AppInfo, spec: IconSpec, px: Int): Bitmap {
        val packDrawable = spec.iconPack?.let { pkg -> iconPack(pkg)?.drawableFor(app.component) }
        if (spec.style == IconStyle.ORIGINAL || (packDrawable != null && spec.style != IconStyle.DOT)) {
            val d = packDrawable ?: app.activityInfo.getIcon(densityDpi)
            return IconRenderer.original(d, px)
        }
        val mask = mask(app) ?: fallbackMask()
        return when (spec.style) {
            IconStyle.DOT -> IconRenderer.dots(mask, spec, px)
            else -> IconRenderer.line(mask, spec, px)
        }
    }

    private suspend fun iconPack(pkg: String): IconPack? = packMutex.withLock {
        if (pack?.packageName != pkg) pack = IconPack(context, pkg)
        pack
    }

    /** Resolves the single-colour mask for [app] via the pipeline. */
    fun mask(app: AppInfo): Bitmap? {
        val memKey = "${app.key}|${app.versionCode}|$overridesHash"
        maskCache.get(memKey)?.let { return it }
        val glyphId = overrides[app.key]?.removePrefix("glyph:")
            ?: bundledMap[app.component.flattenToShortString()]
            ?: bundledMap[app.packageName]
        val glyph = glyphId?.let { DotGlyphs[it] }
        val result: Bitmap? = if (glyph != null) {
            Bitmap.createBitmap(MaskExtractor.SIZE, MaskExtractor.SIZE, Bitmap.Config.ARGB_8888).also { b ->
                renderGlyph(glyph, Canvas(b), MaskExtractor.SIZE.toFloat())
            }
        } else {
            loadOrExtract(app)
        }
        if (result != null) maskCache.put(memKey, result)
        return result
    }

    private fun loadOrExtract(app: AppInfo): Bitmap? {
        val file = File(diskDir, hash("${app.key}|${app.versionCode}|v2") + ".png")
        if (file.exists()) {
            android.graphics.BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        val icon = runCatching { app.activityInfo.getIcon(densityDpi) }.getOrNull() ?: return null
        val mask = (if (Build.VERSION.SDK_INT >= 33) MaskExtractor.fromMonochrome(icon) else null)
            ?: MaskExtractor.fromForeground(icon)
            ?: return null
        runCatching { file.outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return mask
    }

    private fun fallbackMask(): Bitmap {
        val glyph = DotGlyphs["app_generic"] ?: DotGlyphs.all.values.first()
        return Bitmap.createBitmap(MaskExtractor.SIZE, MaskExtractor.SIZE, Bitmap.Config.ARGB_8888).also { b ->
            renderGlyph(glyph, Canvas(b), MaskExtractor.SIZE.toFloat())
        }
    }

    /** Glyph preview for pickers (override screen). */
    fun glyphPreview(id: String, spec: IconSpec, px: Int): ImageBitmap? {
        val glyph = DotGlyphs[id] ?: return null
        val mask = Bitmap.createBitmap(MaskExtractor.SIZE, MaskExtractor.SIZE, Bitmap.Config.ARGB_8888)
        renderGlyph(glyph, Canvas(mask), MaskExtractor.SIZE.toFloat())
        val out = if (spec.style == IconStyle.DOT) IconRenderer.dots(mask, spec, px) else IconRenderer.line(mask, spec, px)
        return out.asImageBitmap()
    }

    fun installedIconPacks(): List<IconPackInfo> = IconPack.installed(context)

    fun clearDiskCache() {
        diskDir.listFiles()?.forEach { it.delete() }
        maskCache.evictAll()
        finalCache.evictAll()
    }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
