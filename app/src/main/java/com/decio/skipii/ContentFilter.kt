package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ContentFilter {
    enum class Scope(val label: String) {
        Title("Title"),
        Channel("Channel"),
        Description("Description"),
        Anywhere("Anywhere"),
    }

    data class Rule(val term: String, val scopes: Set<Scope>)

    var rules by mutableStateOf(emptyList<Rule>())
        private set
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("content_filter", Context.MODE_PRIVATE).also { prefs = it }
        rules = p.getStringSet("rules", emptySet()).orEmpty().mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 2) null else Rule(parts[0], parts[1].split(',').mapNotNull { s -> Scope.entries.firstOrNull { it.name == s } }.toSet().ifEmpty { setOf(Scope.Anywhere) })
        }
    }

    fun add(term: String, scopes: Set<Scope>) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        rules = rules + Rule(clean, scopes.ifEmpty { setOf(Scope.Anywhere) })
        persist()
    }

    fun remove(index: Int) {
        if (index !in rules.indices) return
        rules = rules.toMutableList().also { it.removeAt(index) }
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putStringSet("rules", rules.map { "${it.term}\u001f${it.scopes.joinToString(",") { s -> s.name }}" }.toSet())?.apply()
    }

    fun hideVideo(video: VideoItem): Boolean = rules.any { rule ->
        val t = video.title
        val ch = video.channel
        rule.scopes.any { scope ->
            when (scope) {
                Scope.Title -> t.contains(rule.term, true)
                Scope.Channel -> ch.contains(rule.term, true)
                Scope.Description -> false
                Scope.Anywhere -> t.contains(rule.term, true) || ch.contains(rule.term, true)
            }
        }
    }

    fun hideChannel(details: ChannelDetails): Boolean = rules.any { rule ->
        val name = details.name
        val desc = details.description
        rule.scopes.any { scope ->
            when (scope) {
                Scope.Title -> false
                Scope.Channel -> name.contains(rule.term, true)
                Scope.Description -> desc.contains(rule.term, true)
                Scope.Anywhere -> name.contains(rule.term, true) || desc.contains(rule.term, true)
            }
        }
    }
}