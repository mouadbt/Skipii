package com.decio.skipii

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp

// ── Long-press options ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(video: VideoItem, player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // "Add to library" swaps the options for the picker in place, like the app's other overlays.
    var picking by remember { mutableStateOf(false) }
    // "Download" swaps the options for the quality list when the user wants to choose.
    var downloading by remember(video.id) { mutableStateOf(false) }
    // Whether this video is already saved, so the row offers Remove rather than a duplicate add.
    var watchLater by remember(video.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(video.id) {
        if (Account.signedIn) watchLater = player.watchLaterFor(video.id)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        if (picking) {
            LibraryPicker(video.id, player, onDone = onDismiss)
            Spacer(Modifier.height(24.dp))
            return@ModalBottomSheet
        }
        if (downloading) {
            DownloadQualityList(video, onStarted = {
                player.actionMessage = "Download started"
                onDismiss()
            })
            Spacer(Modifier.height(24.dp))
            return@ModalBottomSheet
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnail, Modifier.width(112.dp).aspectRatio(16f / 9f), radius = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        OptionRow(ImageVector.vectorResource(R.drawable.solar_playlist), "Play next") { player.playNext(video); onDismiss() }
        OptionRow(ImageVector.vectorResource(R.drawable.solar_playlist), "Add to queue") { player.addToQueue(video); onDismiss() }
        if (Account.signedIn) {
            val saved = watchLater == true
            OptionRow(
                ImageVector.vectorResource(R.drawable.solar_bookmark),
                when (watchLater) {
                    null -> "Save to Watch later"
                    true -> "Remove from Watch later"
                    false -> "Save to Watch later"
                },
            ) { player.setWatchLater(video, !saved); onDismiss() }
            OptionRow(ImageVector.vectorResource(R.drawable.solar_playlist), "Add to library") { picking = true }
        }
        OptionRow(ImageVector.vectorResource(R.drawable.solar_share), "Share") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
            context.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        val download = DownloadStore.entry(video.id)
        OptionRow(
            ImageVector.vectorResource(
                if (download != null) R.drawable.solar_check else R.drawable.solar_download,
            ),
            if (download != null) "Downloaded · ${download.height}p" else "Download",
            tint = if (download != null) c.primary else null,
        ) {
            when {
                download != null -> DownloadStore.remove(video.id)
                !DownloadSettings.askEachTime -> {
                    DownloadStore.download(video, DownloadSettings.qualityHeight)
                    player.actionMessage = "Download started"
                }
                else -> downloading = true
            }
            if (download != null || !DownloadSettings.askEachTime) onDismiss()
        }
        OptionRow(ImageVector.vectorResource(R.drawable.solar_close), "Not interested") {
            HiddenVideos.hide(video.id)
            scope.launch { InnerTube.notInterested(video.feedbackToken) }
            onDismiss()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A sheet row: icon, label, tap. Used by the long-press options and a library's own options. */
@Composable
internal fun OptionRow(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: c.onSurface)
        Spacer(Modifier.width(18.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = tint ?: Color.Unspecified)
    }
}

// ── Queue ──────────────────────────────────────────────────────────────────────

/** Card under the player when something is queued; opens the queue sheet. */
@Composable
fun QueueCard(player: PlayerState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val next = player.queue.firstOrNull() ?: return
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(c.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(next.thumbnail, Modifier.width(88.dp).aspectRatio(16f / 9f), radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Queue · ${player.queue.size}",
                color = c.onSecondaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(next.title, color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(ImageVector.vectorResource(R.drawable.solar_playlist), null, tint = c.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            if (player.queue.isNotEmpty()) {
                Text(
                    "Clear", color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.pressable { player.clearQueue() }.padding(8.dp),
                )
            }
        }
        if (player.queue.isEmpty()) {
            Text(
                "Nothing queued. Press and hold a video to add it.",
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                itemsIndexed(player.queue, key = { i, v -> "$i-${v.id}" }) { index, video ->
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        VideoRow(video, onClick = { player.playFromQueue(index); onDismiss() }) {
                            Box(Modifier.size(40.dp).pressable { player.removeFromQueue(index) }, contentAlignment = Alignment.Center) {
                                Icon(ImageVector.vectorResource(R.drawable.solar_close), "Remove from queue", tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── SponsorBlock settings (inside the account sheet) ───────────────────────────

@Composable
fun SponsorBlockSettings(showHeader: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showHeader) {
                    Text("SponsorBlock", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Skip sponsor reads and other segments", color = c.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    Text("Enabled", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Switch(
                checked = SponsorBlock.enabled,
                onCheckedChange = SponsorBlock::updateEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        if (SponsorBlock.enabled) {
            Spacer(Modifier.height(10.dp))
            SponsorBlock.Category.entries.forEach { category -> CategoryRow(category) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Segments from SponsorBlock (sponsor.ajay.app), a community project. Data licensed CC BY-NC-SA 4.0.",
            color = c.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CategoryRow(category: SponsorBlock.Category) {
    val c = MaterialTheme.colorScheme
    val current = SponsorBlock.actionFor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
            Spacer(Modifier.width(8.dp))
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SponsorBlock.Action.entries.forEach { action ->
                val on = action == current
                Box(
                    Modifier
                        .pressable { SponsorBlock.setAction(category, action) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(action.label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Player chrome (inside the account sheet) ───────────────────────────────────

/**
 * Which parts of the watch page appear, plus the order of the pill row under the channel bar.
 * Every control writes through [PlayerUiSettings] so the open player updates on the spot.
 */
@Composable
fun PlayerSettings(showHeader: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text("Player", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Choose what the watch page shows", color = c.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }
        ChromeSwitch("Channel bar", "Avatar, name and Subscribe", !PlayerUiSettings.hideChannelBar) {
            PlayerUiSettings.updateHideChannelBar(!it)
        }
        ChromeSwitch("Comments", "The comments card under the player", !PlayerUiSettings.hideComments) {
            PlayerUiSettings.updateHideComments(!it)
        }
        ChromeSwitch("Related videos", "Videos related to this one", !PlayerUiSettings.hideRelated) {
            PlayerUiSettings.updateHideRelated(!it)
        }
        ChromeSwitch("Dislike count", "Optional estimate from Return YouTube Dislike", PlayerUiSettings.showDislikes) {
            PlayerUiSettings.updateShowDislikes(it)
        }
        Spacer(Modifier.height(14.dp))
        Text("Buttons under the channel bar", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text("Move them up or down and switch off the ones you don't use.", color = c.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        PlayerUiSettings.order.forEachIndexed { index, id ->
            val action = PlayerUiSettings.Action.entries.firstOrNull { it.id == id } ?: return@forEachIndexed
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                val atTop = index == 0
                val atBottom = index == PlayerUiSettings.order.lastIndex
                ReorderButton(ImageVector.vectorResource(R.drawable.solar_up), "Move up", enabled = !atTop) {
                    PlayerUiSettings.move(action, -1)
                }
                ReorderButton(ImageVector.vectorResource(R.drawable.solar_down), "Move down", enabled = !atBottom) {
                    PlayerUiSettings.move(action, 1)
                }
                Switch(
                    checked = PlayerUiSettings.visible(action),
                    onCheckedChange = { PlayerUiSettings.show(action, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Reset player layout",
            color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            modifier = Modifier.pressable { PlayerUiSettings.reset() }.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun ChromeSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = c.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
        )
    }
}

@Composable
private fun ReorderButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(32.dp)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description,
            tint = if (enabled) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Settings sections (inside the account sheet) ───────────────────────────────

/**
 * One titled card in the account sheet. The sheet keeps scrolling as a whole, so the section
 * itself is not scrollable — it only groups a settings body under a shared heading.
 */
@Composable
fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(subtitle, color = c.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Nav-bar / mini-player background choice, shared by Appearance and Navigation. */
@Composable
fun AppearanceNavOptions() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text("Navigation bar background", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavBarSettings.Mode.entries.forEach { mode ->
                val on = NavBarSettings.mode == mode
                Box(
                    Modifier
                        .weight(1f)
                        .pressable { NavBarSettings.choose(mode) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.label,
                        color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Appearance (inside the account sheet) ──────────────────────────────────────/** Light/dark mode plus every Ledgii theme (and wallpaper colors on Android 12+). */
@Composable
fun AppearanceSettings(showHeader: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = isDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text("Appearance", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                val on = ThemeSettings.mode == key
                Box(
                    Modifier
                        .pressable { ThemeSettings.chooseMode(key) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .pressable { ThemeSettings.chooseAmoled(!ThemeSettings.amoled) }
                .clip(RoundedCornerShape(18.dp))
                .background(if (ThemeSettings.amoled) c.primaryContainer else c.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AMOLED black",
                    color = if (ThemeSettings.amoled) c.onPrimaryContainer else c.onSurface,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                )
                Text(
                    "True black pages and cards in dark mode",
                    color = if (ThemeSettings.amoled) c.onPrimaryContainer.copy(alpha = 0.75f) else c.onSurfaceVariant,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Switch(
                checked = ThemeSettings.amoled,
                onCheckedChange = ThemeSettings::chooseAmoled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        Spacer(Modifier.height(14.dp))
        val choices = buildList {
            if (ThemeSettings.wallpaperAvailable) add(null to "Wallpaper")
            SKIPII_THEMES.forEach { add(it.hex to it.name) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { (hex, name) ->
                val selected = ThemeSettings.seed?.lowercase() == hex?.lowercase()
                val scheme = schemeFor(context, hex, dark, ThemeSettings.amoled)
                Column(
                    Modifier.width(78.dp).pressable { ThemeSettings.choose(hex) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(if (selected) CookieShape else CircleShape)
                            .background(scheme.surface)
                            .padding(5.dp)
                            .clip(if (selected) CookieShape else CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    0f to scheme.primary, 0.5f to scheme.primary,
                                    0.5f to scheme.primaryContainer, 1f to scheme.primaryContainer,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(ImageVector.vectorResource(R.drawable.solar_check), "Selected", tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        name, fontSize = 11.5.sp, maxLines = 1, softWrap = false,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (selected) c.onSurface else c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
