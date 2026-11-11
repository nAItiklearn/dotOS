package com.dotos.feature.settings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.db.AppOverrideEntity
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.icons.AppIcon
import com.dotos.feature.icons.engine.IconEngine
import com.dotos.feature.icons.engine.IconSpec
import com.dotos.feature.icons.engine.LocalIconSpec
import com.dotos.feature.icons.glyph.DotGlyphs
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ChevronIcon
import com.dotos.feature.settings.ui.PillButton
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SearchField
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.bottomInsetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun glyphLabel(id: String): String = id.replace('_', ' ')

/** Re-pushes the full override map into the icon engine so every icon re-resolves. */
private suspend fun syncOverrides(env: SettingsEnv) {
    val map = HashMap<String, String>()
    for (e in env.deps.database.appMeta().allOverrides()) {
        val icon = e.icon
        if (icon != null) map[e.componentKey] = icon
    }
    env.deps.icons.setOverrides(map)
}

private fun setGlyphOverride(env: SettingsEnv, key: String, glyphId: String?) {
    env.launchIO {
        val dao = env.deps.database.appMeta()
        val existing = dao.allOverrides().firstOrNull { it.componentKey == key }
        if (glyphId != null) {
            dao.upsertOverride((existing ?: AppOverrideEntity(componentKey = key)).copy(icon = "glyph:$glyphId"))
        } else if (existing != null && existing.label != null) {
            dao.upsertOverride(existing.copy(icon = null))
        } else {
            dao.clearOverride(key)
        }
        syncOverrides(env)
    }
}

/** Per-app overrides: pick an app, then one of the bundled glyphs. */
@Composable
internal fun IconOverridesScreen(env: SettingsEnv, s: LauncherSettings) {
    val apps by env.deps.apps.apps.collectAsStateWithLifecycle()
    val dao = remember(env.deps) { env.deps.database.appMeta() }
    val overrides by remember(dao) { dao.observeOverrides() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val byKey = remember(overrides) { overrides.associateBy { it.componentKey } }
    val filtered = remember(apps, query) {
        val q = query.trim()
        apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    }

    SettingsScaffold(title = "Per-app icons", onBack = env.back, scroll = false) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search apps",
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
            items(filtered, key = { it.key }) { app ->
                val glyph = byKey[app.key]?.icon?.removePrefix("glyph:")
                SettingsRow(
                    title = app.label,
                    subtitle = if (glyph != null) glyphLabel(glyph) else "Default",
                    onClick = { env.navigate(Routes.withArg(Routes.ICON_PICK, app.key)) },
                    leading = { key(glyph) { AppIcon(app, size = 44.dp) } },
                    trailing = { ChevronIcon() },
                )
            }
        }
    }
}

/** Grid of every bundled glyph for one app. */
@Composable
internal fun IconPickScreen(env: SettingsEnv, s: LauncherSettings, appKey: String?) {
    val apps by env.deps.apps.apps.collectAsStateWithLifecycle()
    val dao = remember(env.deps) { env.deps.database.appMeta() }
    val overrides by remember(dao) { dao.observeOverrides() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val app = remember(apps, appKey) { apps.orEmpty().firstOrNull { it.key == appKey } }
    val current = overrides.firstOrNull { it.componentKey == appKey }?.icon?.removePrefix("glyph:")
    val spec = LocalIconSpec.current
    val glyphIds = remember { DotGlyphs.all.keys.toList() }

    SettingsScaffold(title = app?.label ?: "Choose icon", onBack = env.back, scroll = false) {
        if (app != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                key(current) { AppIcon(app, size = 56.dp) }
                Text(
                    if (current != null) glyphLabel(current) else "Default icon",
                    style = DotTheme.type.body,
                    color = DotTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = "Reset",
                    onClick = { setGlyphOverride(env, app.key, null) },
                    filled = false,
                    enabled = current != null,
                )
            }
        }
        if (glyphIds.isEmpty()) {
            Caption("No bundled glyphs available.")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = bottomInsetPadding().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(glyphIds, key = { it }) { id ->
                GlyphCell(
                    id = id,
                    selected = id == current,
                    spec = spec,
                    icons = env.deps.icons,
                    onClick = { if (appKey != null) setGlyphOverride(env, appKey, id) },
                )
            }
        }
    }
}

@Composable
private fun GlyphCell(id: String, selected: Boolean, spec: IconSpec, icons: IconEngine, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, id, spec, px) {
        value = withContext(Dispatchers.Default) {
            try {
                icons.glyphPreview(id, spec, px)
            } catch (e: Exception) {
                null
            }
        }
    }
    val plate = if (spec.plateColor.alpha > 0.05f) spec.plateColor else DotTheme.colors.surface
    RoundOption(label = glyphLabel(id), selected = selected, onClick = onClick, fill = plate) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.size(44.dp))
        }
    }
}
