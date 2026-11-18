package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which player chrome is shown and in what order. Shared by every watch screen, persisted so a
 * rebuild keeps the user's arrangement, and observed by Compose so a switch takes effect at once.
 */
object PlayerUiSettings {
    /** One movable action in the row under the channel bar. */
    enum class Action(val id: String, val label: String) {
        Reactions("reactions", "Like / dislike"),
        Save("save", "Save to Watch later"),
        Library("library", "Add to library"),
        Share("share", "Share"),
        Download("download", "Download"),
        AudioOnly("audio", "Audio only"),
    }

    private val defaults = Action.entries.map { it.id }
    private var prefs: SharedPreferences? = null

    var order by mutableStateOf(defaults)
        private set

    private val hidden = mutableStateMapOf<String, Boolean>().apply {
        Action.entries.forEach { put(it.id, false) }
    }

    var hideRelated by mutableStateOf(false)
        private set
    var hideComments by mutableStateOf(false)
        private set
    var hideChannelBar by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("player_ui", Context.MODE_PRIVATE).also { prefs = it }
        // Unknown/removed ids are dropped; new actions are appended so an upgrade never hides one.
        val saved = p.getString("order", null)?.split(',')?.filter { id -> Action.entries.any { it.id == id } }.orEmpty()
        order = saved.distinct().let { it + defaults.filterNot(it::contains) }.ifEmpty { defaults }
        Action.entries.forEach { hidden[it.id] = p.getBoolean("hide_${it.id}", false) }
        hideRelated = p.getBoolean("hide_related", false)
        hideComments = p.getBoolean("hide_comments", false)
        hideChannelBar = p.getBoolean("hide_channel", false)
    }

    fun visible(action: Action): Boolean = hidden[action.id] != true

    fun show(action: Action, show: Boolean) {
        hidden[action.id] = !show
        prefs?.edit()?.putBoolean("hide_${action.id}", !show)?.apply()
    }

    fun move(action: Action, delta: Int) {
        val list = order.toMutableList()
        val from = list.indexOf(action.id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.removeAt(from)
        list.add(to, action.id)
        order = list
        prefs?.edit()?.putString("order", list.joinToString(","))?.apply()
    }

    fun updateHideRelated(value: Boolean) {
        hideRelated = value
        prefs?.edit()?.putBoolean("hide_related", value)?.apply()
    }

    fun updateHideComments(value: Boolean) {
        hideComments = value
        prefs?.edit()?.putBoolean("hide_comments", value)?.apply()
    }

    fun updateHideChannelBar(value: Boolean) {
        hideChannelBar = value
        prefs?.edit()?.putBoolean("hide_channel", value)?.apply()
    }

    fun reset() {
        order = defaults
        Action.entries.forEach { hidden[it.id] = false }
        hideRelated = false
        hideComments = false
        hideChannelBar = false
        prefs?.edit()?.clear()?.apply()
    }

    /** Actions in the user's order, visible ones only. */
    fun actions(): List<Action> = order.mapNotNull { id -> Action.entries.firstOrNull { it.id == id } }.filter(::visible)
}
