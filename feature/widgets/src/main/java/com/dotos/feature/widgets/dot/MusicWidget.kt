package com.dotos.feature.widgets.dot

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
private class MusicInfo(
    val hasSession: Boolean,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
)

private val NoMusic = MusicInfo(hasSession = false, title = "", artist = "", art = null, playing = false)
private val PreviewMusic = MusicInfo(hasSession = true, title = "Midnight City", artist = "M83", art = null, playing = true)

private const val VIZ_COLUMNS = 22
private const val VIZ_ROWS = 6

private fun readMusicInfo(controller: MediaController?): MusicInfo {
    if (controller == null) return NoMusic
    val md = controller.metadata
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ?: ""
    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        ?: ""
    val bitmap = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
    return MusicInfo(true, title, artist, bitmap?.asImageBitmap(), playing)
}

/** Decorative equaliser: smooth pseudo-random column heights. Not real audio analysis. */
private fun vizLevel(column: Int, t: Float): Float {
    val a = sin(t * (2.1f + (column % 5) * 0.55f) + column * 1.7f)
    val b = sin(t * (3.3f + (column % 3) * 0.9f) + column * 0.6f)
    return (0.5f + 0.25f * a + 0.25f * b).coerceIn(0.08f, 1f)
}

private fun DrawScope.drawViz(t: Float, on: Color, off: Color) {
    val px = size.width / VIZ_COLUMNS
    val py = size.height / VIZ_ROWS
    val radius = min(px, py) * 0.34f
    for (c in 0 until VIZ_COLUMNS) {
        val lit = (vizLevel(c, t) * VIZ_ROWS).roundToInt().coerceIn(1, VIZ_ROWS)
        for (r in 0 until VIZ_ROWS) {
            val fromBottom = VIZ_ROWS - 1 - r
            drawCircle(if (fromBottom < lit) on else off, radius, Offset(px * (c + 0.5f), py * (r + 0.5f)))
        }
    }
}

private fun openSession(controller: MediaController?) {
    try {
        controller?.sessionActivity?.send()
    } catch (e: PendingIntent.CanceledException) {
        // The media app removed its launch intent; nothing to open.
    } catch (e: RuntimeException) {
        // Background launch restrictions or similar: ignore.
    }
}

@SuppressLint("MissingPermission") // Access comes from the notification listener; SecurityException is handled.
object MusicWidget : DotWidgetProvider {
    override val kind: String = "music"
    override val title: String = "Music"
    override val description: String = "Now playing with controls and a dot visualiser. Needs notification access."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(4, 2))

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val app = remember(context) { context.applicationContext }
        val resumed = rememberIsResumed()
        val reduce = LocalReduceMotion.current
        val preview = instance.isPreview

        var hasAccess by remember { mutableStateOf(preview || host.hasNotificationAccess()) }
        LaunchedEffect(resumed, preview) {
            if (resumed && !preview) hasAccess = host.hasNotificationAccess()
        }

        var controller by remember { mutableStateOf<MediaController?>(null) }
        var info by remember { mutableStateOf(if (preview) PreviewMusic else NoMusic) }

        // Media sessions: registered while composed, always released on dispose.
        DisposableEffect(hasAccess, preview, app) {
            val component: ComponentName? = host.notificationListener
            val manager = app.getSystemService(MediaSessionManager::class.java)
            if (preview || !hasAccess || component == null || manager == null) {
                return@DisposableEffect onDispose { }
            }

            var tracked: MediaController? = null
            var reselect: () -> Unit = { }
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    info = readMusicInfo(tracked)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    info = readMusicInfo(tracked)
                }

                override fun onSessionDestroyed() {
                    reselect()
                }
            }

            fun select(list: List<MediaController>?) {
                val sessions = list.orEmpty()
                val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: sessions.firstOrNull()
                if (best?.sessionToken != tracked?.sessionToken) {
                    try {
                        tracked?.unregisterCallback(callback)
                    } catch (e: IllegalArgumentException) {
                        // Already unregistered.
                    }
                    tracked = best
                    best?.registerCallback(callback)
                    controller = best
                }
                info = readMusicInfo(tracked)
            }

            val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> select(list) }
            reselect = {
                try {
                    select(manager.getActiveSessions(component))
                } catch (e: SecurityException) {
                    hasAccess = false
                }
            }
            try {
                manager.addOnActiveSessionsChangedListener(sessionsListener, component)
                select(manager.getActiveSessions(component))
            } catch (e: SecurityException) {
                // Notification access was revoked: fall back to the "allow access" prompt.
                hasAccess = false
            }

            onDispose {
                try {
                    manager.removeOnActiveSessionsChangedListener(sessionsListener)
                } catch (e: RuntimeException) {
                    // Nothing registered.
                }
                try {
                    tracked?.unregisterCallback(callback)
                } catch (e: IllegalArgumentException) {
                    // Already unregistered.
                }
                tracked = null
                controller = null
            }
        }

        // Decorative animation: frame loop only while audio is playing and the launcher is visible.
        var vizTime by remember { mutableFloatStateOf(1.3f) }
        LaunchedEffect(info.playing, resumed, reduce, preview) {
            if (!info.playing || !resumed || reduce || preview) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    vizTime += (now - last) / 1_000_000_000f
                    last = now
                }
            }
        }

        val transport = controller?.transportControls
        val active = info.hasSession
        val a11y = when {
            !hasAccess -> "Music. Allow notification access to show what is playing"
            !active -> "Music. Nothing playing"
            else -> "Music. ${info.title} by ${info.artist}. ${if (info.playing) "Playing" else "Paused"}"
        }
        val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        val onFilled = style.background.copy(alpha = 1f)

        BoxWithConstraints(modifier.semantics(mergeDescendants = false) { contentDescription = a11y }) {
            if (!hasAccess) {
                AccessPrompt(Modifier.fillMaxSize(), onClick = { host.requestNotificationAccess() })
            } else {
                val artSize = (maxHeight * 0.34f).coerceIn(48.dp, 84.dp)
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .size(artSize)
                                .clip(RoundedCornerShape(18.dp))
                                .background(style.track),
                            contentAlignment = Alignment.Center,
                        ) {
                            val art = info.art
                            if (art != null) {
                                Image(
                                    bitmap = art,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = gray,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                DotGlyph(Glyphs.NOTE, style.muted, Modifier.size(artSize * 0.42f))
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .height(artSize)
                                .pressClickable(enabled = active && !preview, pressedScale = 0.98f) { openSession(controller) },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (active) info.title.ifEmpty { "Unknown track" } else "Nothing playing",
                                style = DotTheme.type.title,
                                color = if (active) style.content else style.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (active) info.artist else "Start a song in your music app",
                                style = DotTheme.type.bodySmall,
                                color = style.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                    ) {
                        drawViz(vizTime, if (info.playing) style.content else style.muted, style.track)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(
                            onClick = { transport?.skipToPrevious() },
                            enabled = active && !preview,
                            label = "Previous track",
                        ) { DotGlyph(Glyphs.PREV, style.content, Modifier.size(18.dp)) }
                        RoundButton(
                            onClick = { if (info.playing) transport?.pause() else transport?.play() },
                            enabled = active && !preview,
                            filled = true,
                            size = 56.dp,
                            label = if (info.playing) "Pause" else "Play",
                        ) {
                            DotGlyph(if (info.playing) Glyphs.PAUSE else Glyphs.PLAY, onFilled, Modifier.size(22.dp))
                        }
                        RoundButton(
                            onClick = { transport?.skipToNext() },
                            enabled = active && !preview,
                            label = "Next track",
                        ) { DotGlyph(Glyphs.NEXT, style.content, Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessPrompt(modifier: Modifier, onClick: () -> Unit) {
    val style = LocalWidgetStyle.current
    Column(
        modifier
            .pressClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotGlyph(Glyphs.NOTE, style.content, Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Allow access to show music",
            style = DotTheme.type.title,
            color = style.content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        FitText(
            text = "TAP TO OPEN SETTINGS",
            color = style.muted,
            modifier = Modifier.fillMaxWidth().height(14.dp),
            role = TextRole.LABEL,
            maxSize = 10f,
        )
    }
}
