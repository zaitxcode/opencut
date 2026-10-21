package com.zaitxcode.opencut.core.editor

import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.Canvas
import com.zaitxcode.opencut.core.model.Clip
import com.zaitxcode.opencut.core.model.Keyframe
import com.zaitxcode.opencut.core.model.KeyframeKind
import com.zaitxcode.opencut.core.model.Material
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.MaterialLibrary
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Scale
import com.zaitxcode.opencut.core.model.Segment
import com.zaitxcode.opencut.core.model.TimeRange
import com.zaitxcode.opencut.core.model.Track
import com.zaitxcode.opencut.core.model.TrackType
import com.zaitxcode.opencut.core.model.Transform
import java.util.UUID

/** Identifier of the currently selected layer, if any. */
data class EditorSelection(
    val segmentId: String? = null,
    val trackId: String? = null,
)

/**
 * The mutable state of an editing session.
 *
 * Every mutation goes through [apply] which pushes the previous state onto the undo
 * stack. The state itself is immutable data, so undo is just "put the old value
 * back" and there is no risk of a half-applied operation.
 */
class EditorStore(initial: Project) {

    var project: Project = initial
        private set

    var playheadUs: Long = 0L
        private set

    var selection: EditorSelection = EditorSelection()
        private set

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val durationUs: Long get() = project.durationUs

    val selectedSegment: Segment?
        get() = selection.segmentId?.let { id -> project.segment(id)?.second }

    fun select(segmentId: String?, trackId: String? = null) {
        selection = EditorSelection(segmentId, trackId)
    }

    fun seek(timeUs: Long) {
        playheadUs = timeUs.coerceIn(0L, project.durationUs)
    }

    /** Applies a mutation, recording the previous state for undo. */
    fun apply(mutator: (Project) -> Project) {
        val updated = mutator(project)
        if (updated == project) return
        undoStack.addLast(project)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        project = updated
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = next
    }

    // ---------------------------------------------------------------- media ---

    /** Appends a media item to the given track. */
    fun addMedia(
        material: Material,
        trackType: TrackType = if (material.kind == MaterialKind.AUDIO) TrackType.AUDIO else TrackType.VIDEO,
        startUs: Long = project.durationUs,
    ): String {
        val segmentId = UUID.randomUUID().toString()
        apply { current ->
            val segment = Segment(
                id = segmentId,
                materialId = material.id,
                source = TimeRange(0L, material.durationUs),
                target = TimeRange(startUs, material.durationUs),
            )
            val track = current.tracks.firstOrNull { it.type == trackType }
                ?: Track(id = UUID.randomUUID().toString(), type = trackType)
            current.copy(
                tracks = replaceTrack(current.tracks, track.copy(segments = track.segments + segment)),
                materials = current.materials.with(material),
            ).recalculate()
        }
        select(segmentId)
        return segmentId
    }

    /** Inserts a text layer. */
    fun addText(content: String, startUs: Long = project.durationUs): String {
        val materialId = UUID.randomUUID().toString()
        val segmentId = UUID.randomUUID().toString()
        val duration = 3_000_000L
        apply { current ->
            val material = Material(
                id = materialId,
                kind = MaterialKind.TEXT,
                name = content.take(24),
                content = content,
                durationUs = duration,
            )
            val segment = Segment(
                id = segmentId,
                materialId = materialId,
                source = TimeRange(0L, duration),
                target = TimeRange(startUs, duration),
            )
            val track = current.tracks.firstOrNull { it.type == TrackType.TEXT }
                ?: Track(id = UUID.randomUUID().toString(), type = TrackType.TEXT)
            current.copy(
                tracks = replaceTrack(current.tracks, track.copy(segments = track.segments + segment)),
                materials = current.materials.with(material),
            ).recalculate()
        }
        select(segmentId)
        return segmentId
    }

    /** Inserts a sticker layer. */
    fun addSticker(resourceId: String, name: String, startUs: Long = project.durationUs): String {
        val materialId = UUID.randomUUID().toString()
        val segmentId = UUID.randomUUID().toString()
        val duration = 3_000_000L
        apply { current ->
            val material = Material(
                id = materialId,
                kind = MaterialKind.STICKER,
                name = name,
                resourceId = resourceId,
                durationUs = duration,
            )
            val segment = Segment(
                id = segmentId,
                materialId = materialId,
                source = TimeRange(0L, duration),
                target = TimeRange(startUs, duration),
                clip = Clip(scale = Scale(0.6f, 0.6f)),
            )
            val track = current.tracks.firstOrNull { it.type == TrackType.STICKER }
                ?: Track(id = UUID.randomUUID().toString(), type = TrackType.STICKER)
            current.copy(
                tracks = replaceTrack(current.tracks, track.copy(segments = track.segments + segment)),
                materials = current.materials.with(material),
            ).recalculate()
        }
        select(segmentId)
        return segmentId
    }

    // ------------------------------------------------------------- segments ---

    /**
     * Splits the selected segment at the playhead.
     *
     * The right hand part keeps the original segment id so that keyframes and
     * transitions stay attached to the visible part of the layer.
     */
    fun splitAtPlayhead(): Boolean {
        val segment = selectedSegment ?: return false
        val local = playheadUs - segment.target.start
        if (local <= 0 || local >= segment.target.duration) return false
        apply { current ->
            val (track, seg) = current.segment(segment.id) ?: return@apply current
            val splitSource = TimeRange(
                seg.source.start + local,
                seg.source.duration - local,
            )
            val left = seg.copy(
                source = TimeRange(seg.source.start, local),
                target = TimeRange(seg.target.start, local),
            )
            val rightId = UUID.randomUUID().toString()
            val right = seg.copy(
                id = rightId,
                source = splitSource,
                target = TimeRange(playheadUs, seg.target.duration - local),
                keyframeRefs = emptyList(),
            )
            val segments = track.segments.flatMap { if (it.id == seg.id) listOf(left, right) else listOf(it) }
            current.copy(tracks = replaceTrack(current.tracks, track.copy(segments = segments))).recalculate()
        }
        return true
    }

    fun deleteSelected() {
        val id = selection.segmentId ?: return
        apply { current ->
            val (track, _) = current.segment(id) ?: return@apply current
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.filterNot { it.id == id }),
                ),
            ).recalculate()
        }
        select(null)
    }

    fun duplicateSelected(): String? {
        val segment = selectedSegment ?: return null
        val newId = UUID.randomUUID().toString()
        apply { current ->
            val (track, seg) = current.segment(segment.id) ?: return@apply current
            val copy = seg.copy(
                id = newId,
                target = TimeRange(seg.target.end, seg.target.duration),
            )
            current.copy(
                tracks = replaceTrack(current.tracks, track.copy(segments = track.segments + copy)),
            ).recalculate()
        }
        select(newId)
        return newId
    }

    /** Moves a segment to a new position on its track. */
    fun moveSegment(segmentId: String, newStartUs: Long) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val moved = seg.copy(target = seg.target.shiftedBy(newStartUs - seg.target.start))
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) moved else it }),
                ),
            ).recalculate()
        }
    }

    /**
     * Trims a segment.
     *
     * Trimming the left edge moves the source window forward, trimming the right edge
     * shortens it. Both keep the timeline range and the source range in sync, which
     * is what makes a later speed change or export correct.
     */
    fun trimSegment(segmentId: String, newStartUs: Long, newDurationUs: Long) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val duration = newDurationUs.coerceAtLeast(MIN_SEGMENT_US)
            val delta = newStartUs - seg.target.start
            val trimmed = seg.copy(
                source = TimeRange(
                    (seg.source.start - delta).coerceAtLeast(0L),
                    duration,
                ),
                target = TimeRange(newStartUs, duration),
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) trimmed else it }),
                ),
            ).recalculate()
        }
    }

    /** Applies a speed multiplier, keeping the timeline duration in sync. */
    fun setSpeed(segmentId: String, speed: Float) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val clamped = speed.coerceIn(0.1f, 20f)
            val newDuration = (seg.source.duration / clamped).toLong()
            val updated = seg.copy(
                speed = clamped,
                target = TimeRange(seg.target.start, newDuration),
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            ).recalculate()
        }
    }

    fun setVolume(segmentId: String, volume: Float) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val updated = seg.copy(volume = volume.coerceIn(0f, 4f))
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            )
        }
    }

    fun setMuted(segmentId: String, muted: Boolean) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val updated = seg.copy(muted = muted)
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            )
        }
    }

    /** Updates the transform of the selected layer. */
    fun updateClip(segmentId: String, transform: (Clip) -> Clip) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val updated = seg.copy(clip = transform(seg.clip))
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            )
        }
    }

    /** Applies a colour grading change to the selected layer. */
    fun applyAdjustment(segmentId: String, adjustment: (Adjustment) -> Adjustment) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val material = current.materials.find(seg.materialId) ?: return@apply current
            val updatedMaterial = material.copy(adjust = adjustment(material.adjust))
            val updated = seg.copy(
                extraMaterialRefs = seg.extraMaterialRefs + updatedMaterial.id,
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
                materials = current.materials.with(updatedMaterial),
            )
        }
    }

    /** Attaches a LUT / colour filter to the selected layer. */
    fun applyFilter(segmentId: String, material: Material) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val updated = seg.copy(
                extraMaterialRefs = seg.extraMaterialRefs.filterNot { id ->
                    current.materials.find(id)?.kind == MaterialKind.FILTER
                } + material.id,
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
                materials = current.materials.with(material),
            )
        }
    }

    /** Removes the colour filter of the selected layer. */
    fun clearFilter(segmentId: String) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val filterIds = seg.extraMaterialRefs.filter { id ->
                current.materials.find(id)?.kind == MaterialKind.FILTER
            }
            val updated = seg.copy(extraMaterialRefs = seg.extraMaterialRefs - filterIds.toSet())
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            )
        }
    }

    /**
     * Attaches a transition to the start of the selected layer.
     *
     * The transition is stored on the segment that starts it; the renderer reads it
     * back and cross fades the two clips.
     */
    fun applyTransition(segmentId: String, material: Material, durationUs: Long) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val updated = seg.copy(
                transitionInId = material.id,
                extraMaterialRefs = seg.extraMaterialRefs + material.id,
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
                materials = current.materials.with(material),
            )
        }
    }

    /** Removes the transition of the selected layer. */
    fun clearTransition(segmentId: String) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val transitionIds = seg.extraMaterialRefs.filter { id ->
                current.materials.find(id)?.kind == MaterialKind.TRANSITION
            }
            val updated = seg.copy(
                transitionInId = null,
                extraMaterialRefs = seg.extraMaterialRefs - transitionIds.toSet(),
            )
            current.copy(
                tracks = replaceTrack(
                    current.tracks,
                    track.copy(segments = track.segments.map { if (it.id == segmentId) updated else it }),
                ),
            )
        }
    }

    /** Adds a keyframe for the current playhead position of the selected layer. */
    fun addKeyframe(segmentId: String, kind: KeyframeKind) {
        apply { current ->
            val (track, seg) = current.segment(segmentId) ?: return@apply current
            val local = (playheadUs - seg.target.start).coerceIn(0L, seg.target.duration)
            val existing = current.keyframes.forLayer(segmentId)
            val frame = Keyframe(
                id = UUID.randomUUID().toString(),
                kind = kind,
                timeOffsetUs = local,
                position = seg.clip.transform,
                scale = seg.clip.scale,
                rotation = seg.clip.rotation,
                alpha = seg.clip.alpha,
                volume = seg.volume,
                adjustment = current.materials.find(seg.materialId)?.adjust ?: Adjustment(),
            )
            val updated = existing.filterNot {
                it.kind == kind && it.timeOffsetUs == local
            } + frame
            current.copy(
                keyframes = current.keyframes.copy(
                    layers = current.keyframes.layers + (segmentId to updated),
                ),
            )
        }
    }

    fun removeKeyframe(segmentId: String, keyframeId: String) {
        apply { current ->
            val updated = current.keyframes.forLayer(segmentId).filterNot { it.id == keyframeId }
            current.copy(
                keyframes = current.keyframes.copy(
                    layers = current.keyframes.layers + (segmentId to updated),
                ),
            )
        }
    }

    // -------------------------------------------------------------- project ---

    fun setCanvas(canvas: Canvas) {
        apply { it.copy(canvas = canvas).recalculate() }
    }

    fun rename(name: String) {
        apply { it.copy(name = name) }
    }

    // -------------------------------------------------------------- helpers ---

    private companion object {
        const val MAX_HISTORY = 80
        const val MIN_SEGMENT_US = 100_000L
    }
}

/** Replaces a track with the same id, or appends it when it is new. */
private fun replaceTrack(tracks: List<Track>, updated: Track): List<Track> {
    val index = tracks.indexOfFirst { it.id == updated.id }
    return if (index >= 0) {
        tracks.toMutableList().apply { this[index] = updated }
    } else {
        tracks + updated
    }
}

/** Adds or replaces a material inside the library. */
private fun MaterialLibrary.with(material: Material): MaterialLibrary = when (material.kind) {
    MaterialKind.VIDEO -> copy(videos = replace(videos, material))
    MaterialKind.IMAGE -> copy(images = replace(images, material))
    MaterialKind.AUDIO -> copy(audios = replace(audios, material))
    MaterialKind.TEXT -> copy(texts = replace(texts, material))
    MaterialKind.STICKER -> copy(stickers = replace(stickers, material))
    MaterialKind.EFFECT -> copy(effects = replace(effects, material))
    MaterialKind.FILTER -> copy(filters = replace(filters, material))
    MaterialKind.TRANSITION -> copy(transitions = replace(transitions, material))
    MaterialKind.CANVAS -> copy(canvases = replace(canvases, material))
    MaterialKind.SPEED -> copy(speeds = replace(speeds, material))
    MaterialKind.VOLUME -> copy(volumes = replace(volumes, material))
    MaterialKind.MASK -> copy(masks = replace(masks, material))
    MaterialKind.CHROMA -> copy(chromas = replace(chromas, material))
    MaterialKind.PRIMARY_WHEELS -> copy(primaryWheels = replace(primaryWheels, material))
    MaterialKind.LOG_WHEELS -> copy(logWheels = replace(logWheels, material))
    MaterialKind.CURVES -> copy(curves = replace(curves, material))
    MaterialKind.ANIMATION -> copy(animations = replace(animations, material))
    MaterialKind.TEMPLATE -> copy(templates = replace(templates, material))
    MaterialKind.BEAT -> copy(resources = replace(resources, material))
    MaterialKind.RESOURCE -> copy(resources = replace(resources, material))
}

private fun replace(list: List<Material>, material: Material): List<Material> {
    val index = list.indexOfFirst { it.id == material.id }
    return if (index >= 0) {
        list.toMutableList().apply { this[index] = material }
    } else {
        list + material
    }
}

/** Recomputes the cached project duration after a structural change. */
private fun Project.recalculate(): Project = copy(durationUs = tracks.maxOfOrNull { it.durationUs } ?: 0L)

/** Convenience factory for a brand new project. */
fun newProject(name: String, canvas: Canvas = Canvas()): Project = Project(
    id = UUID.randomUUID().toString(),
    name = name,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis(),
    canvas = canvas,
)

/** Default transform used when a layer is dropped onto the canvas. */
fun defaultTransform(): Transform = Transform(0f, 0f)
