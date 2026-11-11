package com.dotos.core.launcher

import java.text.Normalizer

/**
 * Local fuzzy app search. Ranks: exact > prefix > word-initial prefix > initials > substring > subsequence.
 * Accents are folded ("Música" matches "musica").
 */
object AppSearch {
    private val diacritics = Regex("\\p{Mn}+")

    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    fun score(label: String, query: String): Int {
        if (query.isBlank()) return 0
        val l = fold(label)
        val q = fold(query.trim())
        if (l == q) return 1000
        if (l.startsWith(q)) return 900 - (l.length - q.length).coerceAtMost(100)
        val words = l.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 800
        val initials = words.joinToString("") { it.take(1) }
        if (initials.startsWith(q.replace(" ", ""))) return 700
        val compact = l.replace(" ", "")
        val idx = compact.indexOf(q.replace(" ", ""))
        if (idx >= 0) return 600 - idx
        return if (isSubsequence(q.replace(" ", ""), compact)) 300 else 0
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        if (q.length < 2) return false
        var i = 0
        for (c in s) {
            if (i < q.length && q[i] == c) i++
        }
        return i == q.length
    }

    fun <T> search(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.asSequence()
            .map { it to score(label(it), query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }
}
