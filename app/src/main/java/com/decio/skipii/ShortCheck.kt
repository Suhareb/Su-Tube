package com.decio.skipii

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Whether a video is a Short. YouTube keeps /shorts/<id> for Shorts and sends any other video on from there to
 * its watch page, so a request that doesn't follow redirects tells the two apart: 200 is a Short. Answers are kept.
 */
object ShortCheck {
    private val answers = ConcurrentHashMap<String, Boolean>()
    private val client by lazy { Net.client.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    /** False too when it can't be told (offline, a consent page): the video then opens in the player as usual. */
    suspend fun isShort(id: String): Boolean {
        answers[id]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://www.youtube.com/shorts/$id").head()
                    .header("User-Agent", Net.USER_AGENT).build()
                client.newCall(request).execute().use { it.code == 200 }
            }.getOrNull()
        }
        if (result != null) answers[id] = result
        return result ?: false
    }
}
