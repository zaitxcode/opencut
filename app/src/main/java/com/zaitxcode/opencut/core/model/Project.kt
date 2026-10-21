package com.zaitxcode.opencut.core.model

import kotlinx.serialization.Serializable

/**
 * Canvas geometry of a project.
 *
 * The canvas is the composition surface every track is laid out on. [width]/[height]
 * are expressed in pixels at the project's own resolution, while [ratio] keeps the
 * user facing aspect label ("16:9", "9:16", …) so the UI can round-trip it.
 */
@Serializable
data class Canvas(
    val width: Int = 1080,
    val height: Int = 1920,
    val ratio: String = "9:16",
    val backgroundColor: Int = 0xFF000000.toInt(),
) {
    val aspect: Float get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
}

/** Kinds of lane in the timeline. Lower [order] renders first (furthest back). */
@Serializable
enum class TrackType(val order: Int) {
    CANVAS(0),
    AUDIO(1),
    VIDEO(2),
    EFFECT(3),
    STICKER(4),
    TEXT(5),
    OVERLAY(6);

    companion object {
        fun fromName(value: String): TrackType =
            entries.firstOrNull { it.name == value } ?: VIDEO
    }
}

/**
 * Half-open time range `[start, start + duration)` expressed in microseconds.
 *
 * `source` refers to the position inside the underlying media file, `target` refers to
 * the position on the project timeline. A split or a speed change moves the two ranges
 * apart, which is why both are kept.
 */
@Serializable
data class TimeRange(
    val start: Long = 0L,
    val duration: Long = 0L,
) {
    val end: Long get() = start + duration

    operator fun contains(timeUs: Long): Boolean = timeUs in start until end

    fun shiftedBy(deltaUs: Long): TimeRange = copy(start = start + deltaUs)

    fun resizedTo(newDuration: Long): TimeRange = copy(duration = newDuration)

    companion object {
        val ZERO = TimeRange(0L, 0L)
    }
}

/** Position of a layer inside the canvas, normalised to the canvas size. */
@Serializable
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    companion object {
        val ORIGIN = Transform()
    }
}

/** Non-uniform scale of a layer. */
@Serializable
data class Scale(
    val x: Float = 1f,
    val y: Float = 1f,
) {
    companion object {
        val UNITY = Scale()
    }
}

/** Horizontal / vertical mirroring of a layer. */
@Serializable
data class Flip(
    val horizontal: Boolean = false,
    val vertical: Boolean = false,
)

/**
 * The visual transform applied to a layer before it is drawn.
 *
 * [rotation] is in degrees, [alpha] in 0..1.
 */
@Serializable
data class Clip(
    val transform: Transform = Transform.ORIGIN,
    val scale: Scale = Scale.UNITY,
    val rotation: Float = 0f,
    val flip: Flip = Flip(),
    val alpha: Float = 1f,
) {
    companion object {
        val IDENTITY = Clip()
    }
}

/**
 * A single piece of media placed on a track.
 *
 * A segment references a [Material] through [materialId]; the material carries the
 * file path or the textual/vector payload. Segments also carry their own playback
 * modifiers (speed, volume, flags) so that a duplicate can differ from its source.
 */
@Serializable
data class Segment(
    val id: String,
    val materialId: String,
    val source: TimeRange = TimeRange.ZERO,
    val target: TimeRange = TimeRange.ZERO,
    val clip: Clip = Clip.IDENTITY,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val reverse: Boolean = false,
    val mirror: Boolean = false,
    val cartoon: Boolean = false,
    val intensityAudio: Float = 1f,
    val visible: Boolean = true,
    val renderIndex: Int = 0,
    val extraMaterialRefs: List<String> = emptyList(),
    val keyframeRefs: List<String> = emptyList(),
    val groupId: String? = null,
    val transitionInId: String? = null,
    val transitionOutId: String? = null,
) {
    /** Duration on the timeline, taking speed into account. */
    val timelineDurationUs: Long get() = target.duration

    /** Duration of the consumed source material. */
    val sourceDurationUs: Long get() = source.duration
}

/** A lane holding an ordered list of segments. */
@Serializable
data class Track(
    val id: String,
    val type: TrackType,
    val segments: List<Segment> = emptyList(),
    val locked: Boolean = false,
    val muted: Boolean = false,
    val hidden: Boolean = false,
) {
    val durationUs: Long get() = segments.maxOfOrNull { it.target.end } ?: 0L
}
