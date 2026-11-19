package com.decio.skipii

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * YouTube no longer exposes public dislike counts. When the user turns the optional
 * "Dislike count" setting on, the estimate comes from the community Return YouTube Dislike
 * API; nothing is fetched otherwise.
 */
object DislikeCount {
    suspend fun fetch(videoId: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
                .header("User-Agent", Net.USER_AGENT)
                .build()
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                JSONObject(body).optLong("dislikes", -1L).takeIf { it >= 0L }
            }
        }.getOrNull()
    }
}
