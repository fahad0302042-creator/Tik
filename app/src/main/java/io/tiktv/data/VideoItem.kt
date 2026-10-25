package io.tiktv.data

/**
 * One TikTok video: the direct playable URL plus the metadata used in the UI.
 */
data class VideoItem(
    val videoUrl: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val sourceUrl: String = "",
)
