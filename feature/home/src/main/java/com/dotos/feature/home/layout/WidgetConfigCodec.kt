package com.dotos.feature.home.layout

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal object WidgetConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(map: Map<String, String>): String = json.encodeToString(serializer, map)

    fun decode(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
}
