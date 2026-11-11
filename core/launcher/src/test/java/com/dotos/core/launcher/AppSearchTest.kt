package com.dotos.core.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppSearchTest {
    private val apps = listOf("Google Maps", "Gmail", "Messages", "Música", "WhatsApp", "Settings")

    @Test
    fun prefixWins() = assertEquals("Gmail", AppSearch.search(apps, "gm") { it }.first())

    @Test
    fun initials() = assertTrue(AppSearch.search(apps, "gm") { it }.contains("Google Maps"))

    @Test
    fun accentsFold() = assertTrue(AppSearch.search(apps, "musica") { it }.contains("Música"))

    @Test
    fun wordPrefix() = assertEquals("Google Maps", AppSearch.search(apps, "maps") { it }.first())

    @Test
    fun noMatch() = assertTrue(AppSearch.search(apps, "zzz") { it }.isEmpty())
}
