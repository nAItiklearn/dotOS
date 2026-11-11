package com.dotos.feature.drawer.library

import androidx.compose.runtime.Immutable
import com.dotos.core.launcher.AppCategory
import com.dotos.core.launcher.AppInfo
import com.dotos.core.launcher.AppSearch

/** One App Library tile: a titled, ordered bucket of apps. [id] is stable across list updates. */
@Immutable
internal data class LibraryGroup(
    val id: String,
    val title: String,
    val apps: List<AppInfo>,
)

internal const val ID_SUGGESTIONS = "suggestions"
internal const val ID_RECENT = "recent"
private const val SUGGESTION_LIMIT = 4
private const val RECENT_LIMIT = 4
private const val RECENT_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1000L

/** Alphabetical (case-insensitive) order with the app key as a deterministic tie-break. */
internal val AppOrder: Comparator<AppInfo> =
    compareBy<AppInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.key }

/**
 * Builds the tile list:
 *  1. "Suggestions" from [recentlyUsed] (max 4, only apps that are still in [apps], so hidden apps never leak in),
 *  2. "Recently added" (top 4 installed within the last 14 days),
 *  3. every real [AppCategory] that has apps, largest first, "Other" always last.
 * Empty groups are omitted. Pure function: [nowMillis] is injected so it stays testable.
 */
internal fun buildLibraryGroups(
    apps: List<AppInfo>,
    recentlyUsed: List<AppInfo>,
    nowMillis: Long,
): List<LibraryGroup> {
    if (apps.isEmpty()) return emptyList()

    val byKey = HashMap<String, AppInfo>(apps.size * 2)
    for (app in apps) byKey[app.key] = app

    val groups = ArrayList<LibraryGroup>(AppCategory.entries.size + 2)

    val suggestions = ArrayList<AppInfo>(SUGGESTION_LIMIT)
    val seen = HashSet<String>()
    for (candidate in recentlyUsed) {
        if (suggestions.size >= SUGGESTION_LIMIT) break
        val app = byKey[candidate.key] ?: continue
        if (seen.add(app.key)) suggestions += app
    }
    if (suggestions.isNotEmpty()) {
        groups += LibraryGroup(ID_SUGGESTIONS, AppCategory.SUGGESTIONS.title, suggestions)
    }

    val cutoff = nowMillis - RECENT_WINDOW_MILLIS
    val recent = apps.asSequence()
        .filter { it.installTime >= cutoff }
        .sortedByDescending { it.installTime }
        .take(RECENT_LIMIT)
        .toList()
    if (recent.isNotEmpty()) {
        groups += LibraryGroup(ID_RECENT, AppCategory.RECENT.title, recent)
    }

    val byCategory = apps.groupBy { app ->
        when (app.category) {
            AppCategory.SUGGESTIONS, AppCategory.RECENT -> AppCategory.OTHER
            else -> app.category
        }
    }
    val categoryGroups = AppCategory.entries.mapNotNull { category ->
        byCategory[category]?.let { LibraryGroup(category.name, category.title, it.sortedWith(AppOrder)) }
    }.sortedWith(
        compareBy<LibraryGroup> { it.id == AppCategory.OTHER.name }
            .thenByDescending { it.apps.size }
            .thenBy { it.title },
    )
    groups += categoryGroups
    return groups
}

/**
 * Filters [alphabetical] (already sorted with [AppOrder]) by relevance using [AppSearch]. The sort inside
 * AppSearch is stable, so equally ranked matches stay alphabetical.
 */
internal fun searchApps(alphabetical: List<AppInfo>, query: String): List<AppInfo> =
    AppSearch.search(alphabetical, query) { it.label }
