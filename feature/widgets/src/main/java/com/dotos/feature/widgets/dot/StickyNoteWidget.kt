package com.dotos.feature.widgets.dot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize

private const val NOTE_MAX_CHARS = 240
private const val NOTE_MAX_LINES = 6
private const val NOTE_PREVIEW = "Pick up the parcel\nCall Sam at 6\nWater the plants"

object StickyNoteWidget : DotWidgetProvider {
    override val kind: String = "sticky_note"
    override val title: String = "Sticky Note"
    override val description: String = "One short note on your home screen. Tap to edit; stored on this device."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 2), WidgetSize(4, 2))

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val haptics = rememberDotHaptics()
        val focusManager = LocalFocusManager.current
        val preview = instance.isPreview
        val wide = instance.spanX >= 4
        val textStyle = if (wide) DotTheme.type.title else DotTheme.type.body

        val key = "${instance.stateKey}:note"
        val persisted by rememberWidgetState(key)
        var saved by remember(key) { mutableStateOf<String?>(null) }
        val note = if (preview) NOTE_PREVIEW else (saved ?: persisted.orEmpty())

        var editing by remember { mutableStateOf(false) }
        var field by remember { mutableStateOf(TextFieldValue("")) }
        var hadFocus by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        fun commit() {
            if (!editing) return
            editing = false
            hadFocus = false
            val text = field.text.trimEnd()
            if (text != note) {
                saved = text
                host.save(key, text)
            }
            focusManager.clearFocus()
        }

        fun begin() {
            if (preview || editing) return
            field = TextFieldValue(note, TextRange(note.length))
            editing = true
            haptics.tick()
        }

        // Never lose text if the widget leaves composition while editing.
        DisposableEffect(key) {
            onDispose {
                if (editing) host.save(key, field.text.trimEnd())
            }
        }
        LaunchedEffect(editing) {
            if (editing) focusRequester.requestFocus()
        }

        val a11y = if (note.isEmpty()) "Sticky note, empty. Tap to write" else "Sticky note: $note. Tap to edit"
        val root = modifier
            .semantics { if (!editing) contentDescription = a11y }
            .pressClickable(enabled = !editing && !preview, pressedScale = 0.97f, onClick = { begin() })

        Box(root) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(style.accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    FitText(
                        text = "NOTE",
                        color = style.muted,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        role = TextRole.LABEL,
                        maxSize = 11f,
                        alignment = Alignment.CenterStart,
                    )
                    if (editing) {
                        FitText(
                            text = "${field.text.length}/$NOTE_MAX_CHARS",
                            color = style.muted,
                            modifier = Modifier.fillMaxHeight(),
                            role = TextRole.LABEL,
                            maxSize = 10f,
                            sizeTemplate = "000/000",
                            alignment = Alignment.CenterEnd,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val density = LocalDensity.current
                    val lineDp = with(density) { textStyle.lineHeight.toDp() }
                    val lines = (maxHeight / lineDp).toInt().coerceIn(1, NOTE_MAX_LINES)
                    if (editing) {
                        BasicTextField(
                            value = field,
                            onValueChange = { v -> if (v.text.length <= NOTE_MAX_CHARS) field = v },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        hadFocus = true
                                    } else if (hadFocus) {
                                        commit()
                                    }
                                },
                            textStyle = textStyle.copy(color = style.content),
                            cursorBrush = SolidColor(style.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { commit() }),
                            maxLines = lines,
                            decorationBox = { inner ->
                                Box {
                                    if (field.text.isEmpty()) {
                                        Text("Write a note", style = textStyle, color = style.muted, maxLines = 1)
                                    }
                                    inner()
                                }
                            },
                        )
                    } else if (note.isEmpty()) {
                        Text("Tap to write a note", style = textStyle, color = style.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(
                            text = note,
                            style = textStyle,
                            color = style.content,
                            maxLines = lines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (editing) {
                PillButton(
                    label = "DONE",
                    onClick = { commit() },
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-6).dp),
                    filled = true,
                )
            }
        }
    }
}
