package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** A finished download. Files live in the app's private storage, never in shared media folders. */
@Immutable
data class DownloadEntry(
    val video: VideoItem,
    val height: Int,
    val videoFile: String,
    /** Present when video and audio were downloaded separately (above the muxed cap). */
    val audioFile: String?,
    val bytes: Long,
    val createdAt: Long,
) {
    val isSeparate: Boolean get() = audioFile != null
}

/** Live progress of a download; [total] is 0 until the server tells us the size. */
@Immutable
data class DownloadProgress(val received: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Download quality policy: ask for a quality on every download, or always take [qualityHeight].
 * Persisted next to the other playback settings.
 */
object DownloadSettings {
    private const val KEY_ASK = "ask_each_time"
    private const val KEY_HEIGHT = "quality_height"
    private var prefs: SharedPreferences? = null

    var askEachTime by mutableStateOf(true)
        private set
    var qualityHeight by mutableStateOf(720)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        askEachTime = prefs?.getBoolean(KEY_ASK, true) ?: true
        qualityHeight = prefs?.getInt(KEY_HEIGHT, 720)?.takeIf { it > 0 } ?: 720
    }

    fun updateAskEachTime(value: Boolean) {
        askEachTime = value
        prefs?.edit()?.putBoolean(KEY_ASK, value)?.apply()
    }

    fun updateQualityHeight(value: Int) {
        qualityHeight = value
        prefs?.edit()?.putInt(KEY_HEIGHT, value)?.apply()
    }
}

/**
 * Offline downloads: fetch the chosen streams from YouTube once, keep them in `filesDir`, and
 * let the player read them back without the network. Nothing is written to shared storage, so
 * the downloads never appear in the gallery or a file manager.
 */
object DownloadStore {
    private const val META = "downloads.json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val byId = ConcurrentHashMap<String, DownloadEntry>()

    private lateinit var dir: File
    private lateinit var metaFile: File

    /** Finished downloads, newest first, observed by Library and the player sheets. */
    var entries by mutableStateOf<List<DownloadEntry>>(emptyList())
        private set
    private val running = mutableStateMapOf<String, DownloadProgress>()
    private val failures = mutableStateMapOf<String, String>()

    fun init(context: Context) {
        dir = File(context.filesDir, "downloads").apply { mkdirs() }
        metaFile = File(dir, META)
        // Half-written temp files from an interrupted process are useless.
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { runCatching { it.delete() } }
        load()
    }

    fun entry(id: String): DownloadEntry? = byId[id]
    fun isDownloaded(id: String): Boolean = byId.containsKey(id)
    fun progressOf(id: String): DownloadProgress? = running[id]
    fun failureOf(id: String): String? = failures[id]
    fun file(name: String): File = File(dir, name)

    /** Heights offered for download at [height]'s quality list (muxed and separate alike). */
    fun qualities(info: StreamInfo): List<Int> = buildList {
        addAll(info.videoStreams.filter { it.isUrl && it.height > 0 }.map { it.height })
        addAll(info.videoOnlyStreams.filter { it.isUrl && it.height > 0 }.map { it.height })
    }.distinct().sortedDescending()

    /** Starts a download at the offered height closest to [height]; no-op if already saved. */
    fun download(video: VideoItem, height: Int) {
        if (byId.containsKey(video.id) || jobs.containsKey(video.id)) return
        failures.remove(video.id)
        jobs[video.id] = scope.launch {
            var videoFile: File? = null
            var audioFile: File? = null
            try {
                val info = StreamCache.getOrLoad(video.id)
                val plan = planFor(info, height) ?: throw IOException("No downloadable stream was found.")
                videoFile = File(dir, "${video.id}-${plan.height}.${extension(plan.videoFormat)}")
                val videoBytes = fetch(plan.videoUrl, videoFile, video.id, base = 0L)
                var audioBytes = 0L
                if (plan.audioUrl != null) {
                    audioFile = File(dir, "${video.id}-audio.${extension(plan.audioFormat)}")
                    audioBytes = fetch(plan.audioUrl, audioFile, video.id, base = videoBytes)
                }
                val entry = DownloadEntry(
                    video = video,
                    height = plan.height,
                    videoFile = videoFile.name,
                    audioFile = audioFile?.name,
                    bytes = videoBytes + audioBytes,
                    createdAt = System.currentTimeMillis(),
                )
                byId[video.id] = entry
                entries = (listOf(entry) + entries).sortedByDescending { it.createdAt }
                save()
            } catch (e: CancellationException) {
                videoFile?.delete()
                audioFile?.delete()
                throw e
            } catch (e: Exception) {
                videoFile?.delete()
                audioFile?.delete()
                failures[video.id] = YouTube.friendlyError(e)
            } finally {
                jobs.remove(video.id)
                running.remove(video.id)
            }
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
    }

    fun remove(id: String) {
        cancel(id)
        val entry = byId.remove(id) ?: return
        file(entry.videoFile).delete()
        entry.audioFile?.let { file(it).delete() }
        entries = entries.filterNot { it.video.id == id }
        save()
    }

    /** What to fetch: a muxed stream when it exists at the wanted height, otherwise separate ones. */
    private data class Plan(
        val videoUrl: String,
        val videoFormat: MediaFormat?,
        val height: Int,
        val audioUrl: String?,
        val audioFormat: MediaFormat?,
    )

    private fun planFor(info: StreamInfo, wanted: Int): Plan? {
        val muxed = info.videoStreams.filter { it.isUrl && it.height > 0 }
        val exact = muxed.filter { it.height == wanted }.maxByOrNull { it.bitrate }
        if (exact != null) return Plan(exact.content, exact.format, exact.height, null, null)

        val videos = info.videoOnlyStreams.filter { it.isUrl && it.height > 0 }
        val video = videos.minByOrNull { abs(it.height - wanted) }
        val audio = info.audioStreams
            .filter { it.isUrl }
            .sortedWith(
                compareBy<org.schabi.newpipe.extractor.stream.AudioStream> { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()
        val muxedNear = muxed.minByOrNull { abs(it.height - wanted) }
        // Prefer the exact separate height when it is closer than the best muxed stream.
        if (video != null && audio != null && (muxedNear == null || abs(video.height - wanted) <= abs(muxedNear.height - wanted))) {
            return Plan(video.content, video.format, video.height, audio.content, audio.format)
        }
        if (muxedNear != null) return Plan(muxedNear.content, muxedNear.format, muxedNear.height, null, null)
        if (video != null && audio != null) return Plan(video.content, video.format, video.height, audio.content, audio.format)
        return null
    }

    /**
     * Streams one URL to [target] in bounded range requests. YouTube throttles a single long
     * connection, so each request fetches at most 8 MiB and the next one resumes where it
     * stopped. Returns the bytes written; progress includes [base] from any already-finished
     * file so a two-file download reports one continuous bar.
     */
    private fun fetch(url: String, target: File, id: String, base: Long): Long {
        val part = File(target.parentFile, target.name + ".part")
        val chunk = 8L * 1024 * 1024
        var written = 0L
        var fileTotal = -1L
        try {
            part.outputStream().buffered().use { out ->
                while (true) {
                    val end = written + chunk - 1
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", Net.USER_AGENT)
                        .header("Range", "bytes=$written-$end")
                        .build()
                    var got = 0L
                    Net.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                        // The first (or a resumed) response names the full file size.
                        if (fileTotal <= 0) {
                            fileTotal = parseTotal(response.header("Content-Range"))
                                ?: response.body?.contentLength()?.takeIf { it > 0 && written == 0L }
                                ?: -1L
                        }
                        val body = response.body ?: throw IOException("Download returned no data.")
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var room = end - written + 1
                            while (room > 0) {
                                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), room).toInt())
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                written += n
                                got += n
                                room -= n
                                running[id] = DownloadProgress(
                                    base + written,
                                    if (fileTotal > 0) base + fileTotal else 0L,
                                )
                            }
                        }
                    }
                    if (got == 0L) break
                    if (fileTotal > 0 && written >= fileTotal) break
                    // Without a total, a short final range is the only end marker.
                    if (fileTotal <= 0 && got < chunk) break
                }
            }
            if (!part.renameTo(target)) throw IOException("Couldn't finish the download.")
            return written
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /** `bytes 0-1023/204800` → 204800, for progress and for knowing when to stop. */
    private fun parseTotal(contentRange: String?): Long? {
        val total = contentRange?.substringAfterLast('/')?.toLongOrNull() ?: return null
        return total.takeIf { it > 0 }
    }

    private fun extension(format: MediaFormat?): String = when (format) {
        MediaFormat.MPEG_4 -> "mp4"
        MediaFormat.M4A -> "m4a"
        MediaFormat.WEBM -> "webm"
        else -> "bin"
    }

    private fun load() {
        val text = runCatching { metaFile.readText() }.getOrNull() ?: return
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return
        val loaded = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val video = runCatching { o.getJSONObject("video").toVideoItem() }.getOrNull() ?: continue
                val videoFile = o.optString("videoFile")
                val audioFile = o.optString("audioFile").ifBlank { null }
                if (videoFile.isBlank() || !file(videoFile).exists()) continue
                if (audioFile != null && !file(audioFile).exists()) continue
                add(
                    DownloadEntry(
                        video = video,
                        height = o.optInt("height"),
                        videoFile = videoFile,
                        audioFile = audioFile,
                        bytes = o.optLong("bytes"),
                        createdAt = o.optLong("createdAt"),
                    ),
                )
            }
        }.sortedByDescending { it.createdAt }
        loaded.forEach { byId[it.video.id] = it }
        entries = loaded
    }

    private fun save() {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("video", e.video.toJson())
                    .put("height", e.height)
                    .put("videoFile", e.videoFile)
                    .put("audioFile", e.audioFile ?: "")
                    .put("bytes", e.bytes)
                    .put("createdAt", e.createdAt),
            )
        }
        runCatching { metaFile.writeText(array.toString()) }
    }

    private fun VideoItem.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("channel", channel)
        .put("channelUrl", channelUrl ?: "")
        .put("thumbnail", thumbnail ?: "")
        .put("avatar", avatar ?: "")
        .put("durationSec", durationSec)
        .put("views", views)
        .put("uploaded", uploaded ?: "")

    private fun JSONObject.toVideoItem() = VideoItem(
        id = getString("id"),
        title = optString("title"),
        channel = optString("channel"),
        channelUrl = optString("channelUrl").ifBlank { null },
        thumbnail = optString("thumbnail").ifBlank { null },
        avatar = optString("avatar").ifBlank { null },
        durationSec = optLong("durationSec"),
        views = optLong("views", -1),
        uploaded = optString("uploaded").ifBlank { null },
    )
}

// ── UI ────────────────────────────────────────────────────────────────────────

/** A download card in the Library's Downloads rail. Tap plays it; the x removes the files. */
@Composable
fun DownloadCard(entry: DownloadEntry, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(168.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick),
    ) {
        Box {
            Thumbnail(entry.video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 20)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .pressable { DownloadStore.remove(entry.video.id) }
                    .clip(CircleShape)
                    .background(c.surface.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Remove download", tint = c.onSurface, modifier = Modifier.size(18.dp)) }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("${entry.height}p · offline", color = c.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(entry.video.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(entry.video.channel, color = c.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The quality choices for [video], shared by the player's download sheet and the long-press
 * options. Loads the available streams once, then lists the heights YouTube offers.
 */
@Composable
fun DownloadQualityList(video: VideoItem, onStarted: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var heights by remember(video.id) { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(video.id) {
        heights = runCatching { DownloadStore.qualities(StreamCache.getOrLoad(video.id)) }.getOrElse { emptyList() }
    }
    Text(
        "Download quality",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
    )
    val available = heights
    if (available == null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            repeat(2) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh),
                )
            }
        }
    } else if (available.isEmpty()) {
        Text(
            "This video has no downloadable stream.",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            color = c.onSurfaceVariant, fontSize = 13.sp,
        )
    } else {
        available.forEach { height ->
            OptionRow(Icons.Rounded.Download, "${height}p") {
                DownloadStore.download(video, height)
                onStarted()
            }
        }
    }
    Text(
        "Stored in Skipii's private storage, so downloads never show up in the gallery or Files.",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        color = c.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
    )
}

/** Quality picker, live progress, or the saved-file actions for the open video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val entry = DownloadStore.entry(item.id)
    val progress = DownloadStore.progressOf(item.id)
    val failure = DownloadStore.failureOf(item.id)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(item.thumbnail, Modifier.width(112.dp).aspectRatio(16f / 9f), radius = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        when {
            entry != null -> {
                OptionRow(Icons.Rounded.DownloadDone, "Saved offline · ${entry.height}p", tint = c.primary) {}
                OptionRow(Icons.Rounded.CheckCircle, "Play from this download") { state.open(entry.video); onDismiss() }
                OptionRow(Icons.Rounded.Close, "Remove download") { DownloadStore.remove(item.id); onDismiss() }
            }
            progress != null -> {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        if (progress.total > 0) "Downloading · ${(progress.fraction * 100).toInt()}%"
                        else "Downloading…",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = c.primary)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Kept inside Skipii only — never in the gallery.", color = c.onSurfaceVariant, fontSize = 12.sp)
                }
                OptionRow(Icons.Rounded.Close, "Cancel download") { DownloadStore.cancel(item.id); onDismiss() }
            }
            else -> {
                if (failure != null) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = c.error)
                        Spacer(Modifier.width(10.dp))
                        Text(failure, color = c.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                DownloadQualityList(item, onStarted = {
                    state.actionMessage = "Download started"
                    onDismiss()
                })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Settings card in the account sheet: ask every time, or always use one quality. */
@Composable
fun DownloadSettingsCard(showHeader: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text("Downloads", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Saved inside Skipii only, never in the gallery", color = c.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ask for quality each time", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (DownloadSettings.askEachTime) "Pick a quality on every download"
                    else "Use the quality below without asking",
                    color = c.onSurfaceVariant, fontSize = 12.sp,
                )
            }
            androidx.compose.material3.Switch(
                checked = DownloadSettings.askEachTime,
                onCheckedChange = DownloadSettings::updateAskEachTime,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        if (!DownloadSettings.askEachTime) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(360, 720, 1080).forEach { height ->
                    val on = DownloadSettings.qualityHeight == height
                    Box(
                        Modifier
                            .pressable { DownloadSettings.updateQualityHeight(height) }
                            .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                            .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${height}p",
                            color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
