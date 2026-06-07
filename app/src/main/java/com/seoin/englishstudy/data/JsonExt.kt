package com.seoin.englishstudy.data

import com.seoin.englishstudy.model.PixelBox
import com.seoin.englishstudy.model.SourceBox
import com.seoin.englishstudy.model.VocabSourceRef
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
    if (this == null) return
    for (i in 0 until length()) block(getJSONObject(i))
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { getString(it) }
}

internal fun JSONArray?.toPanelNarrations(): List<String> {
    if (this == null) return emptyList()
    val result = mutableListOf<String>()
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        val text = item.optString("tts", "")
            .ifBlank { item.optString("narration", "") }
            .ifBlank { item.optString("text", "") }
        if (text.isNotBlank()) result.add(text)
    }
    return result
}

internal fun JSONArray?.toVocabSourceRefs(): List<VocabSourceRef> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = getJSONObject(index)
        VocabSourceRef(
            sourceId = item.optString("sourceId", ""),
            rowId = item.optString("rowId", ""),
            rowIndex = item.optInt("rowIndex", 0),
            box = item.optSourceBox("box")
        )
    }
}

internal fun JSONObject.optSourceBox(name: String): SourceBox? {
    val box = optJSONObject(name) ?: return null
    return SourceBox(
        x = box.optDouble("x", 0.0).toFloat(),
        y = box.optDouble("y", 0.0).toFloat(),
        w = box.optDouble("w", 0.0).toFloat(),
        h = box.optDouble("h", 0.0).toFloat()
    )
}

internal fun JSONObject.optPixelBox(name: String): PixelBox? {
    val box = optJSONObject(name) ?: return null
    return PixelBox(
        x = box.optDouble("x", 0.0).toFloat(),
        y = box.optDouble("y", 0.0).toFloat(),
        w = box.optDouble("w", 0.0).toFloat(),
        h = box.optDouble("h", 0.0).toFloat()
    )
}

internal fun JSONObject.optChunkActivityMode(): String {
    val raw = when {
        has("chunkActivity") -> opt("chunkActivity")
        has("chunkActivityMode") -> opt("chunkActivityMode")
        has("manualChunkActivity") -> opt("manualChunkActivity")
        else -> null
    }
    return when (raw) {
        is Boolean -> if (raw) "on" else "off"
        is String -> when (raw.trim().lowercase()) {
            "on", "true", "yes", "include", "included", "manual" -> "on"
            "off", "false", "no", "exclude", "excluded", "skip" -> "off"
            else -> "auto"
        }
        else -> "auto"
    }
}
