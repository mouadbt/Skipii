package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object HiddenVideos {
    var ids by mutableStateOf(emptySet<String>())
        private set
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("hidden_videos", Context.MODE_PRIVATE).also { prefs = it }
        ids = p.getStringSet("ids", emptySet()).orEmpty()
    }

    fun hide(id: String) {
        ids = ids + id
        prefs?.edit()?.putStringSet("ids", ids)?.apply()
    }

    fun isHidden(id: String): Boolean = id in ids
}