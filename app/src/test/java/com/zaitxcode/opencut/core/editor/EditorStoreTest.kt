package com.zaitxcode.opencut.core.editor

import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.Canvas
import com.zaitxcode.opencut.core.model.Keyframe
import com.zaitxcode.opencut.core.model.KeyframeKind
import com.zaitxcode.opencut.core.model.KeyframeLibrary
import com.zaitxcode.opencut.core.model.Material
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour of the editing operations. */
class EditorStoreTest {

    private fun storeWithVideo(durationUs: Long = 10_000_000L): EditorStore {
        val project = newProject("test", Canvas(1080, 1920, "9:16"))
        val store = EditorStore(project)
        store.addMedia(
            Material(
                id = "mat-1",
                kind = MaterialKind.VIDEO,
                path = "/tmp/video.mp4",
                durationUs = durationUs,
                width = 1080,
                height = 1920,
            ),
        )
        return store
    }

    @Test
    fun addingMediaCreatesATrackedSegment() {
        val store = storeWithVideo()
        val track = store.project.tracks.single()
        assertEquals(1, track.segments.size)
        assertEquals(TimeRange(0L, 10_000_000L), track.segments.single().target)
        assertEquals(10_000_000L, store.project.durationUs)
    }

    @Test
    fun splittingProducesTwoAdjacentSegments() {
        val store = storeWithVideo()
        store.seek(4_000_000L)
        assertTrue(store.splitAtPlayhead())

        val segments = store.project.tracks.single().segments
        assertEquals(2, segments.size)
        assertEquals(TimeRange(0L, 4_000_000L), segments[0].target)
        assertEquals(TimeRange(4_000_000L, 6_000_000L), segments[1].target)
        // The right hand part continues at the same position inside the source.
        assertEquals(4_000_000L, segments[1].source.start)
    }

    @Test
    fun splittingAtTheEdgeIsRejected() {
        val store = storeWithVideo()
        store.seek(0L)
        assertTrue(!store.splitAtPlayhead())
        store.seek(10_000_000L)
        assertTrue(!store.splitAtPlayhead())
    }

    @Test
    fun duplicatingASegmentAppendsItAfterTheOriginal() {
        val store = storeWithVideo()
        val original = store.project.tracks.single().segments.single()
        store.select(original.id)
        store.duplicateSelected()

        val segments = store.project.tracks.single().segments
        assertEquals(2, segments.size)
        assertEquals(original.target.end, segments[1].target.start)
    }

    @Test
    fun changingSpeedRescalesTheTimelineDuration() {
        val store = storeWithVideo()
        val segment = store.project.tracks.single().segments.single()
        store.select(segment.id)
        store.setSpeed(segment.id, 2f)

        val updated = store.project.tracks.single().segments.single()
        assertEquals(2f, updated.speed, 0.001f)
        assertEquals(5_000_000L, updated.target.duration)
    }

    @Test
    fun undoAndRedoRestoreThePreviousState() {
        val store = storeWithVideo()
        val before = store.project.tracks.single().segments.size
        val segment = store.project.tracks.single().segments.single()
        store.select(segment.id)
        store.deleteSelected()
        assertEquals(before - 1, store.project.tracks.single().segments.size)

        store.undo()
        assertEquals(before, store.project.tracks.single().segments.size)

        store.redo()
        assertEquals(before - 1, store.project.tracks.single().segments.size)
    }

    @Test
    fun adjustmentChangesAreStoredOnTheMaterial() {
        val store = storeWithVideo()
        val segment = store.project.tracks.single().segments.single()
        store.applyAdjustment(segment.id) { it.copy(brightness = 0.25f) }

        val material = store.project.materials.find(segment.materialId)
        assertNotNull(material)
        assertEquals(0.25f, material!!.adjust.brightness, 0.0001f)
    }

    @Test
    fun keyframesAreInterpolatedBetweenNeighbours() {
        val library = KeyframeLibrary(
            layers = mapOf(
                "layer" to listOf(
                    Keyframe(id = "a", kind = KeyframeKind.VIDEO, timeOffsetUs = 0L, alpha = 0f),
                    Keyframe(id = "b", kind = KeyframeKind.VIDEO, timeOffsetUs = 1_000_000L, alpha = 1f),
                ),
            ),
        )
        val middle = library.evaluate("layer", 500_000L)
        assertNotNull(middle)
        assertEquals(0.5f, middle!!.alpha, 0.0001f)
    }

    @Test
    fun addingAKeyframeReplacesTheOneAtTheSamePosition() {
        val store = storeWithVideo()
        val segment = store.project.tracks.single().segments.single()
        store.seek(2_000_000L)
        store.addKeyframe(segment.id, KeyframeKind.VIDEO)
        store.addKeyframe(segment.id, KeyframeKind.VIDEO)

        val frames = store.project.keyframes.forLayer(segment.id)
        assertEquals(1, frames.size)
        assertEquals(2_000_000L, frames.single().timeOffsetUs)
    }

    @Test
    fun emptyProjectHasNoDuration() {
        val store = EditorStore(newProject("empty"))
        assertEquals(0L, store.project.durationUs)
        assertNull(store.selectedSegment)
    }
}
