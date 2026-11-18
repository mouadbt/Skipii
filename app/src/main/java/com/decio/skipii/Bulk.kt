package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The strip under a page's title while bulk selection is on. It is deliberately one line:
 * the close button, the count, Select all, then whatever actions the page can do.
 */
@Composable
fun BulkSelectionBar(
    count: Int,
    allSelected: Boolean,
    working: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.Rounded.Close, "Exit selection", onClose)
            Spacer(Modifier.width(6.dp))
            Text(
                "$count selected",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (allSelected) "Clear all" else "Select all",
                color = c.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .then(if (working) Modifier else Modifier)
                    .pressable(enabled = !working, onClick = onAll)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(start = 6.dp),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(2.dp))
            }
            actions()
        }
    }
}

/** One pill in [BulkSelectionBar]. Keeps its label on one line even on a narrow phone. */
@Composable
fun BulkAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(38.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (enabled) c.surfaceContainerHigh else c.surfaceContainerHigh.copy(alpha = 0.45f))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = if (enabled) tint else tint.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Target picker for moving already-selected videos out of a library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveVideosSheet(
    libraries: List<PlaylistRef>,
    sourceId: String,
    count: Int,
    onDismiss: () -> Unit,
    onPick: (PlaylistRef) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val targets = buildList {
        add(PlaylistRef("WL", "Watch later", null, null))
        addAll(libraries)
    }.filterNot { it.id == sourceId || it.id == "LL" }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            "Move $count video${if (count == 1) "" else "s"} to",
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        if (targets.isEmpty()) {
            Text(
                "No other library to move to. Create or rename one from your YouTube account first.",
                color = c.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            targets.forEach { target ->
                Row(
                    Modifier.fillMaxWidth().pressable { onPick(target) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).clip(CookieShape).background(c.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ImageVector.vectorResource(if (target.id == "WL") R.drawable.solar_bookmark else R.drawable.solar_playlist),
                            null,
                            tint = c.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(target.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        target.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Quality chooser for a whole selection when downloads are set to ask every time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkDownloadSheet(count: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            "Download $count video${if (count == 1) "" else "s"}",
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )
        Text(
            "Higher qualities use more storage. Downloads stay inside Skipii.",
            color = c.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        listOf(360, 720, 1080).forEach { height ->
            Row(
                Modifier.fillMaxWidth().pressable { onPick(height) }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(c.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${height}p", color = c.onSecondaryContainer, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    when (height) {
                        360 -> "360p · smallest files"
                        720 -> "720p · balanced"
                        else -> "1080p · best quality"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** First few subscriptions, always on one line, with a door into the full channel list. */
@Composable
fun ChannelRail(
    channels: List<ChannelRef>,
    onOpenChannel: (ChannelRef) -> Unit,
    onSeeAll: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val shown = if (channels.size > 3) channels.take(3) else channels
    val seeAll = channels.size > 3
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        shown.forEach { ch ->
            Column(
                Modifier.weight(1f).pressable { onOpenChannel(ch) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 52)
                Spacer(Modifier.height(6.dp))
                Text(
                    ch.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (seeAll) {
            Column(
                Modifier.weight(1f).pressable(onClick = onSeeAll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ImageVector.vectorResource(R.drawable.solar_more), "See all channels", tint = c.onSurface)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "See all",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A full-width row used by the subscriptions list, with a menu target on the right. */
@Composable
fun ChannelListRow(
    channel: ChannelRef,
    selected: Boolean?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (selected) c.primary else c.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(
                    ImageVector.vectorResource(R.drawable.solar_check),
                    "Selected",
                    tint = c.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        ChannelAvatar(channel.avatar, channel.name, size = 52)
        Spacer(Modifier.width(14.dp))
        Text(
            channel.name,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected == null) {
            Box(
                Modifier.size(42.dp).pressable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.solar_more), "Channel options", tint = c.onSurfaceVariant)
            }
        }
    }
}
