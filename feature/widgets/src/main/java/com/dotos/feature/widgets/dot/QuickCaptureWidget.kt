package com.dotos.feature.widgets.dot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetHostServices
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

/** Shared across all Quick Capture widgets: JSON array of {"t": epochMillis, "text": "..."}. */
private const val CAPTURE_KEY = "quick_capture:list"
private const val CAPTURE_KEEP = 50
private const val CAPTURE_MAX_CHARS = 280

private enum class CaptureMode { IDLE, EDITING, SAVED }

/** Pure helper: appends one note to the stored JSON list and keeps the newest [CAPTURE_KEEP]. */
internal fun appendCapture(existing: String?, text: String, now: Long): String {
    val items = ArrayList<JsonElement>()
    if (!existing.isNullOrBlank()) {
        try {
            items.addAll(Json.parseToJsonElement(existing).jsonArray)
        } catch (e: IllegalArgumentException) {
            // Corrupt or foreign data: start a fresh list rather than failing the capture.
        }
    }
    items.add(
        buildJsonObject {
            put("t", now)
            put("text", text)
        },
    )
    val kept = if (items.size > CAPTURE_KEEP) items.subList(items.size - CAPTURE_KEEP, items.size).toList() else items
    return JsonArray(kept).toString()
}

private fun saveCapture(host: WidgetHostServices, text: String) {
    BatchScope.launch {
        val existing = withTimeoutOrNull(1_500L) { host.state(CAPTURE_KEY).firstOrNull() }
        host.putState(CAPTURE_KEY, appendCapture(existing, text, System.currentTimeMillis()))
    }
}

object QuickCaptureWidget : DotWidgetProvider {
    override val kind: String = "quick_capture"
    override val title: String = "Quick Capture"
    override val description: String = "Jot a one-line note, or tap the mic dot for a quick voice memo. Stays on this device."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 1))

    override fun shape(size: WidgetSize): WidgetShape = WidgetShape.PILL

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val app = remember(context) { context.applicationContext }
        val haptics = rememberDotHaptics()
        val focusManager = LocalFocusManager.current
        val resumed = rememberIsResumed()
        val preview = instance.isPreview

        var mode by remember { mutableStateOf(CaptureMode.IDLE) }
        var text by remember { mutableStateOf("") }
        var hadFocus by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        val recorder by RecorderEngine.state.collectAsState()
        val recording = recorder.recording && !preview
        val pulse = rememberPulse(active = recording && resumed, periodMillis = 900, minAlpha = 0.35f)

        fun finish() {
            if (mode != CaptureMode.EDITING) return
            val clean = text.trim()
            hadFocus = false
            text = ""
            focusManager.clearFocus()
            if (clean.isEmpty()) {
                mode = CaptureMode.IDLE
            } else {
                mode = CaptureMode.SAVED
                saveCapture(host, clean)
                haptics.confirm()
            }
        }

        LaunchedEffect(mode) {
            when (mode) {
                CaptureMode.EDITING -> focusRequester.requestFocus()
                CaptureMode.SAVED -> {
                    delay(1_200L)
                    if (mode == CaptureMode.SAVED) mode = CaptureMode.IDLE
                }
                CaptureMode.IDLE -> Unit
            }
        }

        BoxWithConstraints(modifier) {
            val padH = (maxHeight * 0.26f).coerceIn(12.dp, 26.dp)
            val micSize = (maxHeight * 0.5f).coerceIn(36.dp, 52.dp)
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = padH, end = padH * 0.6f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (mode) {
                    CaptureMode.IDLE -> {
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { contentDescription = "Quick capture. Tap to write a note" }
                                .pressClickable(enabled = !preview, pressedScale = 0.97f) {
                                    text = ""
                                    mode = CaptureMode.EDITING
                                    haptics.tick()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DotGlyph(Glyphs.PLUS, style.content, Modifier.size(14.dp))
                            Spacer(Modifier.width(10.dp))
                            FitText(
                                text = "NOTE",
                                color = style.content,
                                modifier = Modifier.weight(1f).fillMaxHeight(0.6f),
                                maxSize = 36f,
                                alignment = Alignment.CenterStart,
                            )
                        }
                    }
                    CaptureMode.EDITING -> {
                        BasicTextField(
                            value = text,
                            onValueChange = { v -> if (v.length <= CAPTURE_MAX_CHARS) text = v.replace('\n', ' ') },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        hadFocus = true
                                    } else if (hadFocus) {
                                        finish()
                                    }
                                },
                            textStyle = DotTheme.type.title.copy(color = style.content),
                            singleLine = true,
                            cursorBrush = SolidColor(style.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { finish() }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (text.isEmpty()) {
                                        Text("Quick note", style = DotTheme.type.title, color = style.muted, maxLines = 1)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    CaptureMode.SAVED -> {
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { contentDescription = "Note saved" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DotGlyph(Glyphs.CHECK, style.content, Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            FitText(
                                text = "SAVED",
                                color = style.content,
                                modifier = Modifier.weight(1f).fillMaxHeight(0.6f),
                                maxSize = 36f,
                                alignment = Alignment.CenterStart,
                            )
                        }
                    }
                }

                if (mode == CaptureMode.EDITING) {
                    RoundButton(onClick = { finish() }, filled = true, size = micSize, label = "Save note") {
                        DotGlyph(Glyphs.CHECK, style.background.copy(alpha = 1f), Modifier.size(16.dp))
                    }
                } else {
                    // The mic dot: a one-tap voice memo through the shared recorder engine.
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = if (recording) "Stop voice memo" else "Record a voice memo"
                            }
                            .pressClickable(enabled = !preview) { toggleRecording(host, app) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(micSize * 0.8f)
                                .graphicsLayer { alpha = pulse.value }
                                .background(if (recording) style.accent else Color.Transparent, CircleShape)
                                .border(1.dp, if (recording) style.accent else style.muted.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            DotGlyph(
                                rows = Glyphs.MIC,
                                color = if (recording) style.background.copy(alpha = 1f) else style.content,
                                modifier = Modifier.size(micSize * 0.36f),
                            )
                        }
                    }
                }
            }
        }
    }
}
