// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun parseVectorLayerIds(jsonMetadata: String?): List<String> {
    if (jsonMetadata.isNullOrBlank()) return emptyList()
    return try {
        val jsonElement = Json.parseToJsonElement(jsonMetadata)
        val vectorLayers = jsonElement.jsonObject["vector_layers"]?.jsonArray
        vectorLayers?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
