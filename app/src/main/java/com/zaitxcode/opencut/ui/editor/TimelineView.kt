package com.zaitxcode.opencut.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Segment
import kotlin.math.max
import kotlin.math.roundToLong
import androidx.compose.ui.res.stringResource
import com.zaitxcode.opencut.R

/** Height of a single track lane, in density independent pixels. */
private val TRACK_HEIGHT = 52.dp

/** Height of the time ruler above the tracks. */
private val RULER_HEIGHT = 22.dp

/** Minimum length of a clip after a trim. */
private const val MIN_CLIP_US = 100_000L

/**
 * The multitrack timeline.
 *
 * Lanes are drawn on a Canvas: the horizontal axis is time, the vertical axis is the
 * track order. Dragging the body of a clip moves it, dragging its edges trims it.
 */
@Composable
fun TimelineView(
    project: Project,
    playheadUs: Long,
    selectedSegmentId: String?,
    onSelect: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveSegment: (String, Long) -> Unit,
    onTrimSegment: (String, Long, Long) -> Unit,
) {
    val density = LocalDensity.current
    val visibleDurationUs = max(project.durationUs, 5_000_000L)
    var trackWidthPx by remember { mutableStateOf(0f) }
    var dragState by remember { mutableStateOf<DragState?>(null) }

    val trackHeightPx = with(density) { TRACK_HEIGHT.toPx() }
    val pxPerSecond = with(density) { 120.dp.toPx() }.coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 4.dp),
    ) {
        // Action row: the operations that apply to the current selection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSplit) {
                Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.editor_split), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.editor_duplicate), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_delete), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = formatTimelineTime(playheadUs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RULER_HEIGHT)
                .pointerInput(visibleDurationUs) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / pxPerSecond * 1_000_000f).toLong().coerceAtLeast(0L))
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT)) {
                drawRuler(visibleDurationUs, pxPerSecond)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT * project.tracks.size.coerceAtLeast(1))
                .pointerInput(visibleDurationUs, project.tracks) {
                    detectTapGestures { offset ->
                        // Tapping a clip selects it; tapping empty space scrubs.
                        val hit = hitTest(
                            project = project,
                            x = offset.x,
                            y = offset.y,
                            pxPerSecond = pxPerSecond,
                            trackHeightPx = trackHeightPx,
                        )
                        if (hit != null) {
                            onSelect(hit.segment.id)
                            onSeek(hit.segment.target.start)
                        } else {
                            onSeek((offset.x / pxPerSecond * 1_000_000f).toLong().coerceAtLeast(0L))
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT * project.tracks.size.coerceAtLeast(1))
                    .pointerInput(visibleDurationUs, project.tracks) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hit = hitTest(
                                    project = project,
                                    x = offset.x,
                                    y = offset.y,
                                    pxPerSecond = pxPerSecond,
                                    trackHeightPx = trackHeightPx,
                                )
                                if (hit != null) {
                                    onSelect(hit.segment.id)
                                    dragState = DragState(
                                        segmentId = hit.segment.id,
                                        mode = hit.mode,
                                        originStartUs = hit.segment.target.start,
                                        originDurationUs = hit.segment.target.duration,
                                        originPointerX = offset.x,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                val state = dragState ?: return@detectDragGestures
                                val deltaUs =
                                    ((change.position.x - state.originPointerX) / pxPerSecond * 1_000_000f)
                                        .toLong()
                                change.consume()
                                dragState = state.copy(deltaUs = deltaUs)
                            },
                            onDragEnd = {
                                val state = dragState
                                dragState = null
                                if (state == null) return@detectDragGestures
                                when (state.mode) {
                                    HitMode.MOVE -> {
                                        val newStart = (state.originStartUs + state.deltaUs).coerceAtLeast(0L)
                                        onMoveSegment(state.segmentId, newStart)
                                    }

                                    HitMode.LEFT -> {
                                        val newStart = (state.originStartUs + state.deltaUs)
                                            .coerceIn(0L, state.originStartUs + state.originDurationUs - MIN_CLIP_US)
                                        val newDuration = state.originDurationUs - (newStart - state.originStartUs)
                                        onTrimSegment(state.segmentId, newStart, newDuration)
                                    }

                                    HitMode.RIGHT -> {
                                        val newDuration = (state.originDurationUs + state.deltaUs)
                                            .coerceAtLeast(MIN_CLIP_US)
                                        onTrimSegment(state.segmentId, state.originStartUs, newDuration)
                                    }
                                }
                            },
                            onDragCancel = { dragState = null },
                        )
                    },
            ) {
                trackWidthPx = size.width
                drawTracks(project, pxPerSecond, selectedSegmentId, dragState)
            }
        }
    }
}

/** Describes an ongoing drag on the timeline. */
private data class DragState(
    val segmentId: String,
    val mode: HitMode,
    val originStartUs: Long,
    val originDurationUs: Long,
    val originPointerX: Float = 0f,
    val deltaUs: Long = 0L,
)

private enum class HitMode { MOVE, LEFT, RIGHT }

/** Result of a hit test on the timeline. */
private data class Hit(val segment: Segment, val mode: HitMode)

/**
 * Finds the segment under a pointer position.
 *
 * The edges of a clip take priority over its body so that trimming is possible even
 * for very short clips.
 */
private fun hitTest(
    project: Project,
    x: Float,
    y: Float,
    pxPerSecond: Float,
    trackHeightPx: Float,
): Hit? {
    val trackIndex = (y / trackHeightPx).toInt()
    val track = project.tracks.getOrNull(trackIndex) ?: return null
    for (segment in track.segments) {
        val left = segment.target.start / 1_000_000f * pxPerSecond
        val right = (segment.target.start + segment.target.duration) / 1_000_000f * pxPerSecond
        if (x in left..right) {
            val mode = when {
                x - left < EDGE_TOLERANCE_PX -> HitMode.LEFT
                right - x < EDGE_TOLERANCE_PX -> HitMode.RIGHT
                else -> HitMode.MOVE
            }
            return Hit(segment, mode)
        }
    }
    return null
}

private const val EDGE_TOLERANCE_PX = 18f

/** Draws the time ruler with second markers. */
private fun DrawScope.drawRuler(durationUs: Long, pxPerSecond: Float) {
    val totalSeconds = (durationUs / 1_000_000f).coerceAtLeast(1f)
    val step = when {
        pxPerSecond > 240f -> 1
        pxPerSecond > 120f -> 2
        pxPerSecond > 60f -> 5
        else -> 10
    }
    var second = 0
    while (second <= totalSeconds) {
        val x = second * pxPerSecond
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(x, size.height - 6f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        second += step
    }
}

/** Draws every track and its clips. */
private fun DrawScope.drawTracks(
    project: Project,
    pxPerSecond: Float,
    selectedSegmentId: String?,
    dragState: DragState?,
) {
    val trackHeight = size.height / project.tracks.size.coerceAtLeast(1)
    project.tracks.forEachIndexed { index, track ->
        val top = index * trackHeight
        drawRect(
            color = Color.White.copy(alpha = 0.03f),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
        )
        track.segments.forEach { segment ->
            val drag = dragState?.takeIf { it.segmentId == segment.id }
            val dragging = drag != null
            val startUs = if (drag != null) {
                when (drag.mode) {
                    HitMode.MOVE -> (dragState.originStartUs + dragState.deltaUs).coerceAtLeast(0L)
                    HitMode.LEFT -> (dragState.originStartUs + dragState.deltaUs).coerceAtLeast(0L)
                    HitMode.RIGHT -> dragState.originStartUs
                }
            } else {
                segment.target.start
            }
            val durationUs = if (drag != null) {
                when (drag.mode) {
                    HitMode.MOVE -> segment.target.duration
                    HitMode.LEFT -> (dragState.originDurationUs - (startUs - dragState.originStartUs))
                        .coerceAtLeast(MIN_CLIP_US)
                    HitMode.RIGHT -> (dragState.originDurationUs + dragState.deltaUs).coerceAtLeast(MIN_CLIP_US)
                }
            } else {
                segment.target.duration
            }
            val left = startUs / 1_000_000f * pxPerSecond
            val width = (durationUs / 1_000_000f * pxPerSecond).coerceAtLeast(6f)
            val material = project.materials.find(segment.materialId)
            val color = when {
                material?.kind == MaterialKind.AUDIO -> Color(0xFF4CAF7D)
                material?.kind == MaterialKind.TEXT -> Color(0xFFE0A34A)
                material?.kind == MaterialKind.STICKER -> Color(0xFFB169C7)
                else -> Color(0xFF3A7AFE)
            }
            val selected = segment.id == selectedSegmentId
            drawRoundRect(
                color = color.copy(alpha = if (selected) 1f else 0.75f),
                topLeft = Offset(left, top + 4f),
                size = Size(width, trackHeight - 8f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            if (selected) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, top + 4f),
                    size = Size(width, trackHeight - 8f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

/** Formats microseconds as `m:ss.f`. */
fun formatTimelineTime(timeUs: Long): String {
    val totalSeconds = timeUs / 1_000_000f
    val minutes = (totalSeconds / 60).toInt()
    val seconds = totalSeconds % 60
    return "%d:%05.2f".format(minutes, seconds)
}
