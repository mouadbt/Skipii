package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** Channel notification choices made in Skipii, persisted between launches. */
object ChannelNotifications {
    private var prefs: SharedPreferences? = null
    private var enabled by mutableStateOf<Set<String>>(emptySet())

    fun init(context: Context) {
        prefs = context.getSharedPreferences("channel_notifications", Context.MODE_PRIVATE)
        enabled = prefs?.getStringSet("enabled", emptySet()).orEmpty()
    }

    fun isOn(channelId: String): Boolean = channelId in enabled

    fun set(channelId: String, on: Boolean) {
        enabled = if (on) enabled + channelId else enabled - channelId
        prefs?.edit()?.putStringSet("enabled", enabled)?.apply()
    }
}

/**
 * The full subscriptions list behind the subscriptions rail's "See all": every channel,
 * with notification/unsubscribe controls and bulk unsubscribe.
 */
@Composable
fun ChannelsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val openChannel = LocalChannelOpener.current
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var working by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ChannelRef?>(null) }
    val allIds = lib.channels.mapTo(HashSet()) { it.id }

    LaunchedEffect(Account.signedIn) {
        if (Account.signedIn && lib.channels.isEmpty() && !lib.channelsLoading) lib.loadChannels()
    }
    LaunchedEffect(lib.channels) {
        selected = selected.intersect(allIds)
        if (selecting && allIds.isEmpty()) selecting = false
    }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    fun unsubscribe(ids: Set<String>) {
        if (ids.isEmpty() || working) return
        working = true
        scope.launch {
            var failed = 0
            ids.forEach { id ->
                runCatching { InnerTube.setSubscribed(id, false) }.onFailure { failed++ }
            }
            val removed = ids.size - failed
            lib.removeChannels(ids)
            working = false
            exitSelection()
            onMessage(
                if (failed == 0) "Unsubscribed from $removed channel${if (removed == 1) "" else "s"}"
                else "Unsubscribed from $removed; $failed failed",
            )
        }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    "All channels",
                    modifier = Modifier.weight(1f),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lib.channels.isNotEmpty() && !selecting) {
                    CircleAction(
                        ImageVector.vectorResource(R.drawable.solar_check),
                        "Select channels",
                    ) { selecting = true }
                } else if (selecting) {
                    Text(
                        "Done",
                        color = c.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        modifier = Modifier.pressable(onClick = ::exitSelection).padding(10.dp),
                    )
                }
            }
            if (selecting) {
                BulkSelectionBar(
                    count = selected.size,
                    allSelected = allIds.isNotEmpty() && selected.size == allIds.size,
                    working = working,
                    onClose = ::exitSelection,
                    onAll = { selected = if (selected.size == allIds.size) emptySet() else allIds },
                ) {
                    BulkAction(
                        ImageVector.vectorResource(R.drawable.solar_close),
                        "Unsubscribe",
                        tint = c.error,
                        enabled = selected.isNotEmpty() && !working,
                    ) { unsubscribe(selected) }
                }
            }
            when {
                !Account.signedIn -> EmptyState(
                    ImageVector.vectorResource(R.drawable.solar_clapperboardplay),
                    "Your subscriptions",
                    "Sign in to manage the channels you follow.",
                )
                lib.channelsLoading && lib.channels.isEmpty() -> LazyColumn(contentPadding = contentPadding) {
                    items(8) { VideoRowSkeleton(Modifier.padding(horizontal = 18.dp)) }
                }
                lib.channelsError != null && lib.channels.isEmpty() -> EmptyState(
                    ImageVector.vectorResource(R.drawable.solar_offline),
                    "Couldn't load your channels",
                    lib.channelsError.orEmpty(),
                    action = { PillButton("Try again", lib::loadChannels) },
                )
                lib.channels.isEmpty() -> EmptyState(
                    ImageVector.vectorResource(R.drawable.solar_clapperboardplay),
                    "No channels yet",
                    "Subscribe to a channel and it will show up here.",
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    items(lib.channels, key = { it.id }) { channel ->
                        ChannelListRow(
                            channel = channel,
                            selected = if (selecting) channel.id in selected else null,
                            onClick = {
                                if (selecting) selected = if (channel.id in selected) selected - channel.id else selected + channel.id
                                else onOpenChannel(channel)
                            },
                            onMenu = { menuFor = channel },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    menuFor?.let { channel ->
        ChannelOptionsSheet(
            channel = channel,
            onDismiss = { menuFor = null },
            onOpen = { menuFor = null; openChannel("https://www.youtube.com/channel/${channel.id}") },
            onUnsubscribed = {
                menuFor = null
                lib.removeChannels(setOf(channel.id))
                onMessage("Unsubscribed from ${channel.name}")
            },
            onMessage = onMessage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelOptionsSheet(
    channel: ChannelRef,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUnsubscribed: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var bunking by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 22.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(channel.avatar, channel.name, size = 54)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Channel", color = c.onSurfaceVariant, fontSize = 12.5.sp)
            }
        }
        val notifications = ChannelNotifications.isOn(channel.id)
        OptionRow(
            if (notifications) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            if (notifications) "Turn notifications off" else "Turn notifications on",
        ) {
            val next = !notifications
            ChannelNotifications.set(channel.id, next)
            scope.launch {
                runCatching { InnerTube.setChannelNotifications(channel.id, next) }
                    .onSuccess { onMessage(if (next) "Notifications on for ${channel.name}" else "Notifications off for ${channel.name}") }
                    .onFailure {
                        ChannelNotifications.set(channel.id, !next)
                        onMessage("Couldn't change notifications.")
                    }
            }
            onDismiss()
        }
        OptionRow(ImageVector.vectorResource(R.drawable.solar_clapperboardplay), "Open channel") { onOpen() }
        OptionRow(
            ImageVector.vectorResource(R.drawable.solar_close),
            if (bunking) "Unsubscribing…" else "Unsubscribe",
            tint = c.error,
        ) {
            if (bunking) return@OptionRow
            bunking = true
            scope.launch {
                runCatching { InnerTube.setSubscribed(channel.id, false) }
                    .onSuccess { onUnsubscribed() }
                    .onFailure { bunking = false; onMessage("Couldn't unsubscribe.") }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}
