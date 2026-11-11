package com.dotos.feature.icons.glyph

/**
 * Registry of every bundled glyph. Glyph sets live in separate files (SystemGlyphs, ...)
 * and are concatenated here. Ids must be unique, lowercase, snake_case.
 */
object DotGlyphs {
    val all: Map<String, Glyph> by lazy {
        val list = SystemGlyphs.list +
            CommunicationGlyphs.list +
            MediaGlyphs.list +
            ToolGlyphs.list +
            LifestyleGlyphs.list +
            DotMatrixGlyphs.list
        val map = LinkedHashMap<String, Glyph>(list.size)
        for (g in list) {
            check(map.put(g.id, g) == null) { "Duplicate glyph id ${g.id}" }
        }
        map
    }

    operator fun get(id: String): Glyph? = all[id]
}
