package com.decio.skipii

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Progress and completion notifications for offline downloads. Each video gets its own
 * notification, and a summary appears while two or more are running so a batch shows both
 * the count and the individual titles.
 */
object DownloadNotifications {
    private const val CHANNEL = "downloads"
    private const val GROUP = "skipii.downloads"
    private const val SUMMARY_ID = 0x5D0A
    private const val UPDATE_MS = 500L

    private var context: Context? = null
    private val active = ConcurrentHashMap<String, String>()
    private val lastUpdate = ConcurrentHashMap<String, Long>()

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        context = app
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Offline download progress and finished videos"
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
        // A download only lives as long as this process. Clear progress notifications left by
        // an interrupted run so they can't sit in the shade as an undismissable bar.
        manager?.activeNotifications?.forEach { status ->
            val ongoing = (status.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
            if (status.notification.channelId == CHANNEL && ongoing) manager.cancel(status.id)
        }
    }

    /** A download just started (or is waiting for the server to report its size). */
    fun start(video: VideoItem) {
        val ctx = context ?: return
        active[video.id] = video.title
        lastUpdate.remove(video.id)
        postProgress(ctx, video, DownloadProgress(0, 0))
        refreshSummary(ctx)
    }

    /** Called from the fetch loop; throttled so a fast connection doesn't spam the shade. */
    fun update(video: VideoItem, progress: DownloadProgress) {
        val ctx = context ?: return
        val now = SystemClock.elapsedRealtime()
        val last = lastUpdate[video.id]
        if (last != null && now - last < UPDATE_MS) return
        lastUpdate[video.id] = now
        postProgress(ctx, video, progress)
    }

    /** Replaces the progress notification with a dismissible "finished" one. */
    fun complete(video: VideoItem, height: Int, bytes: Long) {
        val ctx = context ?: return
        active.remove(video.id)
        lastUpdate.remove(video.id)
        val detail = "${height}p · ${formatBytes(bytes)} · Ready offline"
        val done = common(ctx, video, "Download complete")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${video.title}\n$detail"))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        notify(ctx, id(video.id), done)
        refreshSummary(ctx)
    }

    fun failed(video: VideoItem, message: String) {
        val ctx = context ?: return
        active.remove(video.id)
        lastUpdate.remove(video.id)
        val failed = common(ctx, video, "Download failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${video.title}\n$message"))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        notify(ctx, id(video.id), failed)
        refreshSummary(ctx)
    }

    /** Removes any notification for [videoId], e.g. when its download is cancelled or removed. */
    fun clear(videoId: String) {
        val ctx = context ?: return
        active.remove(videoId)
        lastUpdate.remove(videoId)
        NotificationManagerCompat.from(ctx).cancel(id(videoId))
        refreshSummary(ctx)
    }

    private fun postProgress(ctx: Context, video: VideoItem, progress: DownloadProgress) {
        val known = progress.total > 0
        val pct = if (known) ((progress.received * 100) / progress.total).toInt().coerceIn(0, 100) else 0
        val body = if (known) {
            "$pct% · ${formatBytes(progress.received)} of ${formatBytes(progress.total)}"
        } else {
            "${formatBytes(progress.received)} downloaded"
        }
        val n = common(ctx, video, video.title)
            .setContentText(body)
            .setSubText(if (active.size > 1) "Downloading · ${active.size} active" else "Downloading")
            .setProgress(100, pct, !known)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(ctx, id(video.id), n)
    }

    /** One bundled notification while several videos are running. */
    private fun refreshSummary(ctx: Context) {
        val names = active.values.toList()
        if (names.size < 2) {
            NotificationManagerCompat.from(ctx).cancel(SUMMARY_ID)
            return
        }
        val short = if (names.size <= 2) names.joinToString(" · ")
        else "${names.take(2).joinToString(" · ")} +${names.size - 2} more"
        val summary = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.solar_download)
            .setContentTitle("${names.size} videos downloading")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n")))
            .setContentIntent(openApp(ctx))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(ctx, SUMMARY_ID, summary)
    }

    private fun common(ctx: Context, video: VideoItem, title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.solar_download)
            .setContentTitle(title)
            .setContentText(video.title)
            .setContentIntent(openApp(ctx))
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun openApp(ctx: Context): PendingIntent? {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun id(videoId: String): Int = videoId.hashCode() and 0x7fffffff

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(id, notification) }
    }

    private fun canPost(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }.replace(".0 ", " ")
}
