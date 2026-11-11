package com.dotos.core.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Doto's ':' glyph renders as two stacked fragments at display sizes. Use this for any time or
 * ratio shown in the dot face: the colon is drawn with bold Space Mono, which sits on the digit
 * centre line and keeps the dot-matrix rhythm.
 */
fun dotTime(text: String): AnnotatedString {
    if (':' !in text) return AnnotatedString(text)
    return buildAnnotatedString {
        for (c in text) {
            if (c == ':') {
                pushStyle(SpanStyle(fontFamily = DotFonts.Mono, fontWeight = FontWeight.Bold))
                append(c)
                pop()
            } else {
                append(c)
            }
        }
    }
}
