package com.lightreader.core.format

import com.lightreader.core.model.SearchHit
import com.lightreader.core.util.TextUtil
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Shared search implementation for reflowable documents: chapters are streamed one at a time,
 * so searching a 300 MB TXT file behaves exactly like searching a short one.
 */
object ChapterSearch {

    private const val MAX_HITS_PER_CHAPTER = 8

    /**
     * @param query raw user query
     */
    suspend fun find(
        query: String,
        chapterCount: Int,
        limit: Int,
        titleOf: (Int) -> String,
        textLoader: suspend (Int) -> String,
    ): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val needle = TextUtil.normaliseForSearch(trimmed)
        val hits = ArrayList<SearchHit>()
        for (chapterIndex in 0 until chapterCount) {
            coroutineContext.ensureActive()
            val text = try {
                textLoader(chapterIndex)
            } catch (_: Throwable) {
                ""
            }
            if (text.isEmpty()) continue
            val haystack = TextUtil.normaliseForSearch(text)
            var from = 0
            var perChapter = 0
            while (perChapter < MAX_HITS_PER_CHAPTER) {
                val found = haystack.indexOf(needle, from)
                if (found < 0) break
                hits.add(
                    SearchHit(
                        chapterIndex = chapterIndex,
                        charOffset = found,
                        snippet = TextUtil.snippet(text, found),
                        title = titleOf(chapterIndex),
                    ),
                )
                perChapter++
                if (hits.size >= limit) return hits
                from = found + needle.length
            }
        }
        return hits
    }
}
