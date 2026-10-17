package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Who can see a new library, as YouTube names it. */
private val PRIVACY = listOf("PRIVATE" to "Private", "UNLISTED" to "Unlisted", "PUBLIC" to "Public")

// ── Add to library (the player's sheet) ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val videoId = player.current?.id ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        LibraryPicker(videoId, player, onDone = onDismiss)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The account's libraries, ticked where [videoId] already sits, plus a form for a new one. Shared
 * by the player's sheet and the long-press options, so both save exactly the same way. Reads are
 * made here (a fresh list every open, which is what keeps the ticks right); writes go through
 * [PlayerState] so they survive this content going away.
 */
@Composable
fun LibraryPicker(videoId: String, player: PlayerState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var rows by remember(videoId) { mutableStateOf<List<PlaylistMembership>?>(null) }
    var failed by remember(videoId) { mutableStateOf(false) }
    var creating by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        runCatching { InnerTube.librariesFor(videoId) }
            .onSuccess { rows = it }
            .onFailure { failed = true; rows = emptyList() }
    }
    Column(modifier.fillMaxWidth()) {
        if (creating) {
            NewLibraryForm(
                onCancel = { creating = false },
                onCreate = { title, privacy -> player.createLibrary(title, privacy, videoId); onDone() },
            )
            return@Column
        }
        Text(
            "Add to library",
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        val list = rows
        when {
            list == null -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Loading your libraries…", color = c.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            // Long lists scroll rather than pushing the sheet off the top of the screen.
            else -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                list.forEach { row ->
                    LibraryRow(row) { player.saveToLibrary(row.playlist, videoId, !row.contains); onDone() }
                }
                if (list.isEmpty()) {
                    Text(
                        if (failed) "Couldn't load your libraries." else "You have no libraries yet.",
                        color = c.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                NewLibraryRow { creating = true }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** One library: what it holds and, when the video is already in it, the tick YouTube would show. */
@Composable
private fun LibraryRow(row: PlaylistMembership, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val playlist = row.playlist
    val ink = if (row.contains) c.onPrimaryContainer else c.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (row.contains) c.primaryContainer else c.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist.thumbnail != null) {
            Thumbnail(playlist.thumbnail, Modifier.size(width = 56.dp, height = 32.dp), radius = 10)
        } else {
            Box(
                Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                playlist.subtitle ?: "Playlist",
                color = if (row.contains) c.onPrimaryContainer.copy(alpha = 0.75f) else c.onSurfaceVariant,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
        if (row.contains) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, "In this library", tint = c.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NewLibraryRow(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(c.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.onSecondaryContainer)
        Spacer(Modifier.width(12.dp))
        Text("New library", color = c.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Names a new library and picks who can see it. */
@Composable
private fun NewLibraryForm(onCancel: () -> Unit, onCreate: (title: String, privacy: String) -> Unit) {
    val c = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PRIVACY.first().first) }
    val canCreate = title.isNotBlank()
    // The field is ready to type in as soon as the form appears (same as the comment composer).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).pressable { onCancel() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text("New library", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (title.isEmpty()) Text("Name", color = c.onSurfaceVariant, fontSize = 15.sp)
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate(title.trim(), privacy) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Who can see it", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRIVACY.forEach { (value, label) ->
                val on = privacy == value
                Box(
                    Modifier
                        .pressable { privacy = value }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).pressable(enabled = canCreate) { onCreate(title.trim(), privacy) }
                .clip(RoundedCornerShape(22.dp)).background(if (canCreate) c.primary else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Text("Create", color = if (canCreate) c.onPrimary else c.onSurfaceVariant, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        Spacer(Modifier.height(6.dp))
    }
}
