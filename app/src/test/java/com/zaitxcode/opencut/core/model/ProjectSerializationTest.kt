package com.zaitxcode.opencut.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The project document must survive a JSON round trip unchanged. */
class ProjectSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun projectSurvivesARoundTrip() {
        val project = Project(
            id = "p1",
            name = "Demo",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            canvas = Canvas(1920, 1080, "16:9"),
            tracks = listOf(
                Track(
                    id = "t1",
                    type = TrackType.VIDEO,
                    segments = listOf(
                        Segment(
                            id = "s1",
                            materialId = "m1",
                            source = TimeRange(0L, 5_000_000L),
                            target = TimeRange(0L, 5_000_000L),
                            clip = Clip(
                                transform = Transform(0.1f, -0.2f),
                                scale = Scale(1.5f, 1.5f),
                                rotation = 45f,
                                alpha = 0.8f,
                                flip = Flip(horizontal = true),
                            ),
                            speed = 1.5f,
                            volume = 0.5f,
                        ),
                    ),
                ),
            ),
            materials = MaterialLibrary(
                videos = listOf(
                    Material(
                        id = "m1",
                        kind = MaterialKind.VIDEO,
                        path = "/tmp/a.mp4",
                        durationUs = 5_000_000L,
                        width = 1920,
                        height = 1080,
                        adjust = Adjustment(brightness = 0.1f, saturation = 0.2f),
                    ),
                ),
            ),
            keyframes = KeyframeLibrary(
                layers = mapOf(
                    "s1" to listOf(
                        Keyframe(id = "k1", kind = KeyframeKind.VIDEO, timeOffsetUs = 1_000_000L),
                    ),
                ),
            ),
            durationUs = 5_000_000L,
        )

        val encoded = json.encodeToString(Project.serializer(), project)
        val decoded = json.decodeFromString(Project.serializer(), encoded)

        assertEquals(project, decoded)
    }

    @Test
    fun unknownFieldsAreIgnoredSoOlderDocumentsStillOpen() {
        val encoded = """
            {
              "id": "p1",
              "name": "Demo",
              "createdAt": 1,
              "updatedAt": 2,
              "somethingFromTheFuture": {"a": 1}
            }
        """.trimIndent()
        val decoded = json.decodeFromString(Project.serializer(), encoded)
        assertEquals("p1", decoded.id)
        assertTrue(decoded.tracks.isEmpty())
    }

    @Test
    fun segmentKeepsBothTimeRanges() {
        val segment = Segment(
            id = "s",
            materialId = "m",
            source = TimeRange(1_000L, 2_000L),
            target = TimeRange(3_000L, 4_000L),
        )
        assertEquals(1_000L, segment.source.start)
        assertEquals(3_000L, segment.source.end)
        assertEquals(3_000L, segment.target.start)
        assertEquals(4_000L, segment.timelineDurationUs)
        assertEquals(2_000L, segment.sourceDurationUs)
    }
}
