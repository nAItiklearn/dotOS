package com.dotos.feature.icons

import com.dotos.feature.icons.glyph.DotGlyphs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pure-JVM sanity checks for the bundled icon map. Constructing [com.dotos.feature.icons.glyph.Glyph] objects does
 * not execute any android.graphics code (the drawing lambdas are only invoked when a glyph is rendered), so the whole
 * registry can be inspected without a device.
 */
class IconMapTest {
    private val raw: String = File("src/main/assets/icon_map.json").readText()
    private val map = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun everyMappedGlyphIdExists() {
        val missing = map.entries
            .filter { (_, glyphId) -> DotGlyphs.all[glyphId.jsonPrimitive.content] == null }
            .map { (pkg, glyphId) -> "$pkg -> ${glyphId.jsonPrimitive.content}" }
        assertTrue(missing.isEmpty()) { "icon_map.json references unknown glyph ids: $missing" }
    }

    @Test
    fun hasAtLeast350Entries() {
        assertTrue(map.size >= 350) { "icon_map.json only has ${map.size} entries" }
    }

    @Test
    fun jsonKeysAreUnique() {
        val keys = Regex("\"([^\"]+)\"\\s*:").findAll(raw).map { it.groupValues[1] }.toList()
        assertEquals(keys.size, keys.toSet().size, "icon_map.json contains duplicate package keys")
        assertEquals(keys.size, map.size)
    }

    @Test
    fun keysLookLikePackageNames() {
        val pattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val bad = map.keys.filterNot { pattern.matches(it) }
        assertTrue(bad.isEmpty()) { "Not valid package names: $bad" }
    }

    @Test
    fun brandAppsAreNotMapped() {
        val brands = listOf(
            "com.whatsapp",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.spotify.music",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.twitter.android",
            "com.snapchat.android",
            "com.netflix.mediaclient",
        )
        val mapped = brands.filter { map.containsKey(it) }
        assertTrue(mapped.isEmpty()) { "Brand apps must use their own system icon, but are mapped: $mapped" }
    }

    @Test
    fun glyphIdsAreUniqueAndWellFormed() {
        // DotGlyphs.all throws on duplicate ids while it is being built, so reaching the assertions proves uniqueness
        // of the registry; the source scan additionally catches a glyph that is declared but never registered.
        val registered = DotGlyphs.all.keys
        val idPattern = Regex("[a-z][a-z0-9_]*")
        assertTrue(registered.all { idPattern.matches(it) }) { "Glyph ids must be lowercase snake_case" }

        val declared = File("src/main/java/com/dotos/feature/icons/glyph")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> Regex("Glyph\\(\"([a-z0-9_]+)\"\\)").findAll(file.readText()).map { it.groupValues[1] } }
            .toList()
        assertEquals(declared.size, declared.toSet().size, "A glyph id is declared more than once")
        assertEquals(declared.toSet(), registered, "Declared glyphs and the DotGlyphs registry differ")
        assertTrue(registered.size >= 70) { "Expected at least 70 glyphs, found ${registered.size}" }
    }
}
