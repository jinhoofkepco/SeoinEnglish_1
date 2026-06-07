package com.seoin.englishstudy.chunk

import com.seoin.englishstudy.model.ManualChunk
import com.seoin.englishstudy.model.WordToken

internal fun wordTokens(value: String): List<WordToken> {
    return Regex("\\S+").findAll(value).map { match ->
        WordToken(match.value, match.range.first, match.range.last + 1)
    }.toList()
}

internal fun chunkText(sentenceText: String, tokens: List<WordToken>, chunk: ManualChunk): String {
    val start = tokens.getOrNull(chunk.startWord)?.startChar ?: return ""
    val end = tokens.getOrNull(chunk.endWord)?.endChar ?: return ""
    return sentenceText.substring(start, end)
}

internal fun normalizeManualChunks(tokens: List<WordToken>, chunks: List<ManualChunk>): List<ManualChunk> {
    if (tokens.isEmpty()) return emptyList()
    val sorted = chunks.sortedBy { it.startWord }
    val normalized = mutableListOf<ManualChunk>()
    var nextStart = 0
    sorted.forEach { chunk ->
        if (chunk.endWord < nextStart) return@forEach
        val end = chunk.endWord.coerceAtMost(tokens.lastIndex)
        if (nextStart <= end) {
            normalized.add(ManualChunk(nextStart, end))
            nextStart = end + 1
        }
    }
    return normalized
}
