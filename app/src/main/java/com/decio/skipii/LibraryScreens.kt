package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The full offline library, separate from the Library tab's short preview rail. */
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val entries = DownloadStore.entries
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            LibraryPageHeader("Downloads", onBack)
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Download,
                    title = "No downloads yet",
                    body = "Download a video from the player and it will be ready here, even offline.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = contentPadding.calculateBottomPadding()),
                ) {
                    items(entries, key = { it.video.id }) { entry ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            VideoRow(
                                entry.video,
                                onClick = { onOpen(entry.video) },
                                trailing = {
                                    RemoveDownloadButton(entry.video.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The full watch history, with the same paging and refresh as the other feeds. */
@Composable
fun HistoryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { lib.history.ensureLoaded() }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        LibraryPageHeader("History", onBack)
        Box(Modifier.weight(1f)) {
            FeedList(
                feed = lib.history,
                contentPadding = PaddingValues(top = 4.dp, bottom = contentPadding.calculateBottomPadding()),
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun LibraryPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            letterSpacing = (-0.8).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun RemoveDownloadButton(videoId: String) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(34.dp)
            .pressable { DownloadStore.remove(videoId) }
            .clip(CircleShape)
            .background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, "Remove download", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
