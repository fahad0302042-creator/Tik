package io.tiktv.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Tiny thumbnail loader built on OkHttp + BitmapFactory (no image library needed).
 * Downloads the image on the calling (IO) dispatcher and downscales it to keep
 * memory usage sane.
 */
object ImageLoader {

    private val client: OkHttpClient by lazy { OkHttpClient() }

    fun loadBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 480
            if (decoded.width > maxDim || decoded.height > maxDim) {
                val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                return Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt(),
                    (decoded.height * scale).toInt(),
                    true,
                )
            }
            return decoded
        }
    }
}
