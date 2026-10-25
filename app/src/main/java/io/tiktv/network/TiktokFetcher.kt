package io.tiktv.network

import io.tiktv.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches a TikTok video page and extracts the direct playable video URL,
 * thumbnail, title and author.
 *
 * TikTok keeps changing their page structure, so parsing is defensive: we first
 * try the embedded `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON payload, then fall
 * back to regex matching on the raw HTML.
 */
class TiktokFetcher(private val client: OkHttpClient = defaultClient()) {

    suspend fun fetchVideo(rawText: String): VideoItem = withContext(Dispatchers.IO) {
        val pageUrl = extractUrl(rawText)
            ?: throw IllegalArgumentException("No TikTok link found in that text.")

        val html = httpGet(pageUrl)
        parseVideoPage(html, pageUrl)
    }

    /** Extracts the first http(s) URL from arbitrary share text. */
    fun extractUrl(rawText: String): String? {
        val match = URL_RE.find(rawText) ?: return null
        return match.value.trimEnd('.', ')', '！', '？', '，', '。', ':', '；', '；')
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TikTok returned HTTP ${response.code}. Try again later.")
            }
            return response.body?.string() ?: throw IOException("Empty response from TikTok.")
        }
    }

    private fun parseVideoPage(html: String, pageUrl: String): VideoItem {
        var videoUrl: String? = null
        var title: String? = null
        var author: String? = null
        var coverUrl: String? = null

        REHYDRATION_RE.find(html)?.let { match ->
            runCatching { JSONObject(match.groupValues[1]) }.getOrNull()?.let { root ->
                videoUrl = findVideoUrl(root)
                title = findString(root, "desc")
                author = findString(root, "nickname") ?: findString(root, "uniqueId")
                coverUrl = findString(root, "originCover") ?: findString(root, "cover")
            }
        }

        if (videoUrl == null) {
            videoUrl = MP4_RE.find(html)?.groupValues?.get(0)?.let { decodeEscapes(it) }
        }
        if (title == null) {
            title = PAGE_TITLE_RE.find(html)?.groupValues?.get(1)?.trim()
        }

        val resolvedUrl = videoUrl?.takeIf { it.startsWith("http") }
        if (resolvedUrl == null) {
            throw IOException(
                "Could not extract a playable video URL from this link. " +
                    "Copy the link from TikTok again and try once more."
            )
        }

        return VideoItem(
            videoUrl = resolvedUrl,
            title = (title ?: "TikTok video").take(140),
            author = author?.take(60),
            coverUrl = coverUrl?.takeIf { it.startsWith("http") },
            sourceUrl = pageUrl,
        )
    }

    private fun findVideoUrl(root: JSONObject): String? {
        val candidateKeys = listOf("playPath", "playAddr", "play_addr", "videoUrl", "playApi")
        return candidateKeys.asSequence()
            .mapNotNull { key -> findString(root, key) }
            .firstOrNull { url ->
                url.startsWith("http") &&
                    (url.contains(".mp4") || url.contains("tiktokcdn") || url.contains("douyin"))
            }
    }

    /** Recursively searches the JSON for the first string value of [key]. */
    private fun findString(json: JSONObject, key: String): String? {
        if (json.has(key)) {
            val value = json.optString(key, "")
            if (value.isNotEmpty()) return value
        }
        for (name in json.keys()) {
            when (val child = json.opt(name)) {
                is JSONObject -> findString(child, key)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until child.length()) {
                        val item = child.opt(i)
                        if (item is JSONObject) {
                            findString(item, key)?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun decodeEscapes(raw: String): String =
        raw.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003c", "<")

    companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val URL_RE = Regex("https?://[^\\s，。！？]+")

        private val REHYDRATION_RE = Regex(
            "id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>\\s*(\\{.*?})\\s*</script>",
            RegexOption.DOT_MATCHES_ALL,
        )

        private val MP4_RE = Regex(
            "https?://[^\"'\\\\<>\\s]+\\.mp4[^\"'\\\\<>\\s]*",
            RegexOption.IGNORE_CASE,
        )

        private val PAGE_TITLE_RE = Regex(
            "<title[^>]*>(.*?)</title>",
            RegexOption.DOT_MATCHES_ALL,
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
