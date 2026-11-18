package com.decio.skipii

import androidx.compose.runtime.mutableStateMapOf

/**
 * Library edits made while another screen is showing. The playlist page can be open under the
 * player; removing a video from that library must make it disappear at once, not after a pull
 * to refresh. Additions are tracked too, so a failed add/remove can be rolled back cleanly.
 */
object LibraryLive {
    private val removed = mutableStateMapOf<String, Set<String>>()
    private val added = mutableStateMapOf<String, Set<String>>()

    fun removed(playlistId: String, videoId: String) {
        added[playlistId] = added[playlistId].orEmpty() - videoId
        removed[playlistId] = removed[playlistId].orEmpty() + videoId
    }

    fun added(playlistId: String, videoId: String) {
        removed[playlistId] = removed[playlistId].orEmpty() - videoId
        added[playlistId] = added[playlistId].orEmpty() + videoId
    }

    fun isRemoved(playlistId: String, videoId: String): Boolean =
        removed[playlistId]?.contains(videoId) == true
}
