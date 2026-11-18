package com.decio.skipii

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders. Every list that used to show a spinner while its first page loads
 * shows the shape of the thing that is coming instead: a shimmering video card, comment row
 * or cover. The shimmer stops (static tint) with system animations off.
 */
@Composable
fun rememberSkeletonBrush(): Brush {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val base = c.surfaceContainerHigh
    val highlight = c.surfaceContainerHighest
    if (reduced) return Brush.linearGradient(listOf(base, base))
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_250), RepeatMode.Restart),
        label = "skeletonShift",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift * 900f, 0f),
        end = Offset(shift * 900f + 700f, 700f),
    )
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).background(rememberSkeletonBrush()))
}

/** The same silhouette as [VideoCard]: cover, avatar and two title lines. */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberSkeletonBrush()
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(22.dp)).background(brush))
        Row(Modifier.padding(start = 8.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            SkeletonBox(Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SkeletonBox(Modifier.fillMaxWidth(0.92f).height(15.dp))
                Spacer(Modifier.height(7.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.68f).height(15.dp))
                Spacer(Modifier.height(9.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.48f).height(11.dp))
            }
        }
    }
}

/** The compact row used by "More videos", comments and queue loading states. */
@Composable
fun VideoRowSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberSkeletonBrush()
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(brush))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.95f).height(13.dp))
            Spacer(Modifier.height(7.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(13.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.45f).height(10.dp))
        }
    }
}

@Composable
fun CommentSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SkeletonBox(Modifier.size(36.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.width(120.dp).height(11.dp))
            Spacer(Modifier.height(9.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(11.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.86f).height(11.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.52f).height(11.dp))
        }
    }
}

/** A short stack of card skeletons for a whole-page first load (search, history). */
@Composable
fun VideoCardSkeletonList(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(count) { VideoCardSkeleton() }
    }
}
