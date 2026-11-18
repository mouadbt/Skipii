package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object NavBarSettings {
    enum class Mode(val label: String) {
        Follow("Follow theme"),
        Opposite("Opposite theme"),
        Primary("Primary color"),
    }

    var mode by mutableStateOf(Mode.Follow)
        private set
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("nav_bar", Context.MODE_PRIVATE).also { prefs = it }
        mode = Mode.entries.firstOrNull { it.name == p.getString("mode", Mode.Follow.name) } ?: Mode.Follow
    }

    fun choose(newMode: Mode) {
        mode = newMode
        prefs?.edit()?.putString("mode", newMode.name)?.apply()
    }

    fun color(surface: Color, primary: Color, background: Color): Color = when (mode) {
        Mode.Follow -> surface
        Mode.Opposite -> if (background.luminance() < 0.5f) Color(0xFFF2F2F2) else Color(0xFF1C1C1E)
        Mode.Primary -> primary
    }
}