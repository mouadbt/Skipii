package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Where the app opens and which tabs its floating bar shows. A channel or playlist can be
 * pinned as the start screen, so the app can open straight into the place you actually use.
 */
object NavigationSettings {
    private const val KEY_START_KIND = "start_kind"
    private const val KEY_START_TAB = "start_tab"
    private const val KEY_CHANNEL_URL = "start_channel_url"
    private const val KEY_CHANNEL_NAME = "start_channel_name"
    private const val KEY_PLAYLIST_ID = "start_playlist_id"
    private const val KEY_PLAYLIST_TITLE = "start_playlist_title"
    private const val KEY_PLAYLIST_THUMB = "start_playlist_thumb"
    private const val KEY_HIDDEN = "hidden_tabs"

    private var prefs: SharedPreferences? = null
    private var startKind by mutableStateOf("tab")
    private var startTab by mutableStateOf(Tab.Home)
    private var channelUrl by mutableStateOf<String?>(null)
    private var channelName by mutableStateOf<String?>(null)
    private var playlistId by mutableStateOf<String?>(null)
    private var playlistTitle by mutableStateOf<String?>(null)
    private var playlistThumb by mutableStateOf<String?>(null)

    var hiddenTabs by mutableStateOf<Set<Tab>>(emptySet())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("navigation", Context.MODE_PRIVATE).also { prefs = it }
        startKind = p.getString(KEY_START_KIND, "tab").orEmpty().ifBlank { "tab" }
        startTab = Tab.entries.firstOrNull { it.name == p.getString(KEY_START_TAB, Tab.Home.name) } ?: Tab.Home
        channelUrl = p.getString(KEY_CHANNEL_URL, null)
        channelName = p.getString(KEY_CHANNEL_NAME, null)
        playlistId = p.getString(KEY_PLAYLIST_ID, null)
        playlistTitle = p.getString(KEY_PLAYLIST_TITLE, null)
        playlistThumb = p.getString(KEY_PLAYLIST_THUMB, null)
        hiddenTabs = p.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
            .mapNotNull { name -> Tab.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    val visibleTabs: List<Tab>
        get() = Tab.entries.filterNot { it in hiddenTabs }.ifEmpty { listOf(Tab.Home) }

    fun fallbackTab(): Tab = visibleTabs.first()

    /** The tab that should be selected at launch: the chosen one, or the first still visible. */
    fun startupTab(): Tab =
        if (startKind == "tab" && startTab !in hiddenTabs) startTab else fallbackTab()

    fun startupChannelUrl(): String? = if (startKind == "channel") channelUrl else null

    fun startupPlaylist(): PlaylistRef? =
        if (startKind == "playlist" && playlistId != null) {
            PlaylistRef(playlistId.orEmpty(), playlistTitle ?: "Playlist", playlistThumb, null)
        } else null

    fun startLabel(): String = when (startKind) {
        "channel" -> channelName ?: "Channel"
        "playlist" -> playlistTitle ?: "Playlist"
        else -> if (startTab !in hiddenTabs) startTab.label else fallbackTab().label
    }

    fun startIsChannel(): Boolean = startKind == "channel"

    fun setTab(tab: Tab) {
        if (tab in hiddenTabs) hiddenTabs = hiddenTabs - tab
        startKind = "tab"
        startTab = tab
        persistStart()
    }

    fun setChannel(url: String, name: String?) {
        startKind = "channel"
        channelUrl = url
        channelName = name
        persistStart()
    }

    fun setPlaylist(playlist: PlaylistRef) {
        startKind = "playlist"
        playlistId = playlist.id
        playlistTitle = playlist.title
        playlistThumb = playlist.thumbnail
        persistStart()
    }

    /** Returns false when this is the last visible screen: one tab must remain. */
    fun setHidden(tab: Tab, hidden: Boolean): Boolean {
        if (hidden && visibleTabs.size <= 1 && tab !in hiddenTabs) return false
        hiddenTabs = if (hidden) hiddenTabs + tab else hiddenTabs - tab
        prefs?.edit()?.putStringSet(KEY_HIDDEN, hiddenTabs.map { it.name }.toSet())?.apply()
        return true
    }

    private fun persistStart() {
        prefs?.edit()
            ?.putString(KEY_START_KIND, startKind)
            ?.putString(KEY_START_TAB, startTab.name)
            ?.putString(KEY_CHANNEL_URL, channelUrl)
            ?.putString(KEY_CHANNEL_NAME, channelName)
            ?.putString(KEY_PLAYLIST_ID, playlistId)
            ?.putString(KEY_PLAYLIST_TITLE, playlistTitle)
            ?.putString(KEY_PLAYLIST_THUMB, playlistThumb)
            ?.apply()
    }
}

/** Start screen and per-screen visibility, as a settings dropdown body. */
@Composable
fun NavigationSettingsCard() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text("Starting screen", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(
            "Open Skipii exactly where you want. Pin a channel or playlist from its page too.",
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        val pinnedChannel = NavigationSettings.startIsChannel()
        NavigationSettings.visibleTabs.forEach { tab ->
            ChoiceRow(
                label = tab.label,
                selected = !pinnedChannel && NavigationSettings.startupPlaylist() == null && NavigationSettings.startLabel() == tab.label,
                icon = ImageVector.vectorResource(tab.iconRes),
            ) { NavigationSettings.setTab(tab) }
        }
        // Pinned destinations are shown even when they are not a tab.
        if (pinnedChannel) {
            ChoiceRow(
                NavigationSettings.startLabel(),
                selected = true,
                icon = ImageVector.vectorResource(R.drawable.solar_home),
            ) {}
        }
        val playlist = NavigationSettings.startupPlaylist()
        if (playlist != null) {
            ChoiceRow(playlist.title, selected = true, icon = ImageVector.vectorResource(R.drawable.solar_playlist)) {}
        }
        Spacer(Modifier.height(16.dp))
        Text("Visible screens", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(
            "Hide a screen from the navigation bar. At least one stays visible.",
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Tab.entries.forEach { tab ->
            val visible = tab !in NavigationSettings.hiddenTabs
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ImageVector.vectorResource(tab.iconRes), null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(tab.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Switch(
                    checked = visible,
                    onCheckedChange = { on -> NavigationSettings.setHidden(tab, !on) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "If the start screen is hidden, Skipii opens the first visible screen instead.",
            color = c.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = if (selected) c.onPrimaryContainer else c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (selected) c.onPrimaryContainer else c.onSurface,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
        }
    }
    Spacer(Modifier.height(3.dp))
}
