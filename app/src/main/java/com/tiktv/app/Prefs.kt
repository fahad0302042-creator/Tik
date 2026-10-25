package com.tiktv.app

import android.content.Context

/**
 * Small SharedPreferences wrapper. Everything the app remembers lives here.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("tik_tv", Context.MODE_PRIVATE)

    var homeUrl: String
        get() = sp.getString(KEY_HOME, DEFAULT_HOME) ?: DEFAULT_HOME
        set(value) = sp.edit().putString(KEY_HOME, value).apply()

    /** Use the mobile web layout instead of the desktop one. */
    var mobileSite: Boolean
        get() = sp.getBoolean(KEY_MOBILE, false)
        set(value) = sp.edit().putBoolean(KEY_MOBILE, value).apply()

    /** Full-screen video overlay that hides TikTok's chrome. */
    var cinema: Boolean
        get() = sp.getBoolean(KEY_CINEMA, false)
        set(value) = sp.edit().putBoolean(KEY_CINEMA, value).apply()

    /** Skip to the next video when the current one ends. */
    var autoplayNext: Boolean
        get() = sp.getBoolean(KEY_AUTOPLAY, true)
        set(value) = sp.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /** Start every launch with the virtual mouse cursor enabled. */
    var startWithCursor: Boolean
        get() = sp.getBoolean(KEY_START_CURSOR, false)
        set(value) = sp.edit().putBoolean(KEY_START_CURSOR, value).apply()

    /** 0 = slow, 1 = normal, 2 = fast. */
    var cursorSpeed: Int
        get() = sp.getInt(KEY_CURSOR_SPEED, 1)
        set(value) = sp.edit().putInt(KEY_CURSOR_SPEED, value).apply()

    var seenHelp: Boolean
        get() = sp.getBoolean(KEY_SEEN_HELP, false)
        set(value) = sp.edit().putBoolean(KEY_SEEN_HELP, value).apply()

    fun reset() = sp.edit().clear().apply()

    companion object {
        const val DEFAULT_HOME = "https://www.tiktok.com/"

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private const val KEY_HOME = "home_url"
        private const val KEY_MOBILE = "mobile_site"
        private const val KEY_CINEMA = "cinema"
        private const val KEY_AUTOPLAY = "autoplay_next"
        private const val KEY_START_CURSOR = "start_cursor"
        private const val KEY_CURSOR_SPEED = "cursor_speed"
        private const val KEY_SEEN_HELP = "seen_help"
    }
}
