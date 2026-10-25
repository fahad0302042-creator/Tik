package io.tiktv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tiktv_watchlist")

private val KEY_VIDEOS = stringPreferencesKey("watchlist_videos")

/**
 * Persists the saved watchlist as a JSON list in DataStore preferences.
 */
class WatchlistStore(private val context: Context) {

    val videos: Flow<List<VideoItem>> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIDEOS]?.let { json -> decode(json) } ?: emptyList()
    }

    suspend fun add(item: VideoItem) {
        context.dataStore.edit { preferences ->
            val current = decode(preferences[KEY_VIDEOS] ?: "[]")
            if (current.any { it.videoUrl == item.videoUrl }) return@edit
            preferences[KEY_VIDEOS] = encode(current + item)
        }
    }

    suspend fun remove(item: VideoItem) {
        context.dataStore.edit { preferences ->
            val current = decode(preferences[KEY_VIDEOS] ?: "[]")
            preferences[KEY_VIDEOS] = encode(current.filterNot { it.videoUrl == item.videoUrl })
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_VIDEOS) }
    }

    private fun encode(videos: List<VideoItem>): String {
        val array = JSONArray()
        videos.forEach { item ->
            array.put(
                JSONObject()
                    .put("videoUrl", item.videoUrl)
                    .put("title", item.title)
                    .put("author", item.author ?: JSONObject.NULL)
                    .put("coverUrl", item.coverUrl ?: JSONObject.NULL)
                    .put("sourceUrl", item.sourceUrl),
            )
        }
        return array.toString()
    }

    private fun decode(json: String): List<VideoItem> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            VideoItem(
                videoUrl = obj.optString("videoUrl"),
                title = obj.optString("title"),
                author = obj.optString("author").ifEmpty { null },
                coverUrl = obj.optString("coverUrl").ifEmpty { null },
                sourceUrl = obj.optString("sourceUrl"),
            )
        }.filter { it.videoUrl.isNotEmpty() }
    }.getOrDefault(emptyList())
}
