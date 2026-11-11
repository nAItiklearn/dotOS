package com.dotos.feature.icons.glyph

object DotGlyphs {
    val all: Map<String, Glyph> by lazy { SystemGlyphs.list.associateBy { it.id } }

    operator fun get(id: String): Glyph? = all[id]
}
