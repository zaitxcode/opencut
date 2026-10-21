package com.zaitxcode.opencut.core.render

import com.zaitxcode.opencut.core.model.Canvas
import com.zaitxcode.opencut.core.model.FilterSpec
import com.zaitxcode.opencut.core.model.Material
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Segment
import com.zaitxcode.opencut.core.model.TimeRange
import com.zaitxcode.opencut.core.model.Track
import com.zaitxcode.opencut.core.model.TrackType
import com.zaitxcode.opencut.core.model.TransitionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layer composition rules: ordering, filters and transitions. */
class RenderStateTest {

    private fun projectWithTwoClips(): Project {
        val video = Material(
            id = "m1",
            kind = MaterialKind.VIDEO,
            path = "/tmp/a.mp4",
            durationUs = 10_000_000L,
            width = 1080,
            height = 1920,
        )
        val transition = Material(
            id = "t1",
            kind = MaterialKind.TRANSITION,
            name = "Fade",
            transition = TransitionSpec(name = "Fade", durationUs = 1_000_000L),
        )
        val first = Segment(
            id = "s1",
            materialId = "m1",
            source = TimeRange(0L, 5_000_000L),
            target = TimeRange(0L, 5_000_000L),
        )
        val second = Segment(
            id = "s2",
            materialId = "m1",
            source = TimeRange(5_000_000L, 5_000_000L),
            target = TimeRange(5_000_000L, 5_000_000L),
            transitionInId = "t1",
            extraMaterialRefs = listOf("t1"),
        )
        return Project(
            id = "p",
            name = "p",
            createdAt = 0,
            updatedAt = 0,
            canvas = Canvas(),
            tracks = listOf(Track(id = "t", type = TrackType.VIDEO, segments = listOf(first, second))),
            materials = com.zaitxcode.opencut.core.model.MaterialLibrary(
                videos = listOf(video),
                transitions = listOf(transition),
            ),
        )
    }

    @Test
    fun layersAreBuiltInTrackOrder() {
        val state = PreviewRenderer.buildState(projectWithTwoClips(), 0L)
        assertEquals(2, state.layers.size)
        assertEquals("s1", state.layers[0].segmentId)
        assertEquals("s2", state.layers[1].segmentId)
    }

    @Test
    fun aTransitionCrossFadesTheTwoClips() {
        val project = projectWithTwoClips()

        // Before the transition window both clips are fully opaque.
        val before = PreviewRenderer.buildState(project, 4_000_000L)
        assertEquals(1f, before.layers[0].clip.alpha, 0.001f)
        assertEquals(1f, before.layers[1].clip.alpha, 0.001f)

        // In the middle of the transition the alphas are complementary.
        val middle = PreviewRenderer.buildState(project, 5_500_000L)
        assertEquals(0.5f, middle.layers[0].clip.alpha, 0.001f)
        assertEquals(0.5f, middle.layers[1].clip.alpha, 0.001f)

        // After the window the incoming clip is fully opaque again.
        val after = PreviewRenderer.buildState(project, 6_500_000L)
        assertEquals(1f, after.layers[1].clip.alpha, 0.001f)
    }

    @Test
    fun aFilterMaterialIsPickedUpFromTheExtraReferences() {
        val project = projectWithTwoClips()
        val filter = Material(
            id = "f1",
            kind = MaterialKind.FILTER,
            name = "Warm",
            filter = FilterSpec(id = "WARM", name = "Warm", intensity = 0.8f),
        )
        val track = project.tracks.single()
        val updated = track.copy(
            segments = track.segments.map {
                if (it.id == "s1") it.copy(extraMaterialRefs = it.extraMaterialRefs + "f1") else it
            },
        )
        val withFilter = project.copy(
            tracks = listOf(updated),
            materials = project.materials.copy(filters = listOf(filter)),
        )
        val state = PreviewRenderer.buildState(withFilter, 0L)
        val spec = state.layers.first().filter
        assertNotNull(spec)
        assertEquals("WARM", spec!!.id)
        assertEquals(0.8f, spec.intensity, 0.001f)
    }

    @Test
    fun unknownLutLooksAreRejected() {
        assertNull(LutFactory.lookOf(null))
        assertNull(LutFactory.lookOf(FilterSpec(id = "NOT_A_LOOK")))
        assertEquals(LutFactory.Look.WARM, LutFactory.lookOf(FilterSpec(id = "warm")))
    }

    @Test
    fun theLutCatalogueCoversEveryBuiltInLook() {
        val catalogue = LutFactory.catalogue()
        assertTrue(catalogue.size >= 5)
        assertTrue(catalogue.any { it.first == LutFactory.Look.MONO })
    }
}
