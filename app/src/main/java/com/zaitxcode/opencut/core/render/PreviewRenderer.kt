package com.zaitxcode.opencut.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLES20
import android.util.Log
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.Clip
import com.zaitxcode.opencut.core.model.FilterSpec
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.TrackType
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** What a single layer contributes to a frame. */
data class RenderLayer(
    val segmentId: String,
    val materialId: String,
    val kind: LayerKind,
    val uri: String,
    val content: String,
    /** Extra payload of the layer: the shape id of a sticker. */
    val payload: String = "",
    val clip: Clip,
    val adjustment: Adjustment,
    val filter: FilterSpec?,
    val sourceStartUs: Long,
    val sourceDurationUs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val targetStartUs: Long,
    val targetDurationUs: Long,
    val speed: Float,
)

enum class LayerKind {
    VIDEO,
    IMAGE,
    TEXT,
    STICKER,
}

/** The frame description handed to the GL thread. */
data class RenderState(
    val project: Project? = null,
    val playheadUs: Long = 0L,
    val layers: List<RenderLayer> = emptyList(),
    val backgroundColor: Int = Color.BLACK,
)

/**
 * Draws the project timeline with OpenGL ES 2.0.
 *
 * The renderer is deliberately stateless with respect to the editor: every frame it
 * receives a [RenderState] snapshot built from the project model, so the editor can
 * keep mutating its own state while the GL thread renders independently.
 */
class PreviewRenderer(private val context: Context) {

    @Volatile
    var state: RenderState = RenderState()
        set(value) {
            field = value
        }

    private val decoders = ConcurrentHashMap<String, VideoDecoder>()
    private val textures = ConcurrentHashMap<String, Int>()
    private val composer = FrameComposer()

    fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        composer.init()
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    fun onDrawFrame(gl: GL10?) {
        val current = state
        val project = current.project
        composer.clear(current.backgroundColor)
        if (project == null) return

        composer.setViewport(
            project.canvas.width,
            project.canvas.height,
            surfaceWidth,
            surfaceHeight,
        )
        composer.enableBlend()

        for (layer in current.layers) {
            if (!layerVisible(layer, current.playheadUs)) continue
            val texture = resolveTexture(layer) ?: continue
            val look = LutFactory.lookOf(layer.filter)
            composer.drawLayer(
                layer = layer,
                texture = texture,
                canvasWidth = project.canvas.width,
                canvasHeight = project.canvas.height,
                playheadUs = current.playheadUs,
                lutTexture = look?.let { LutFactory.textureFor(it) } ?: 0,
                lutIntensity = layer.filter?.intensity ?: 1f,
            )
        }
        composer.disableBlend()
    }

    private fun layerVisible(layer: RenderLayer, playheadUs: Long): Boolean {
        val end = layer.targetStartUs + layer.targetDurationUs
        return playheadUs in layer.targetStartUs until end
    }

    private fun resolveTexture(layer: RenderLayer): Int? = when (layer.kind) {
        LayerKind.VIDEO -> {
            val decoder = decoderFor(layer)
            decoder?.let {
                val offsetUs = state.playheadUs - layer.targetStartUs
                val sourceTimeUs = layer.sourceStartUs + (offsetUs * layer.speed).toLong()
                if (sourceTimeUs + FRAME_TOLERANCE_US < it.lastFrameTimeUs) {
                    it.seekTo(sourceTimeUs)
                }
                it.advanceTo(sourceTimeUs)
                it.latchFrame()
                it.textureId
            }
        }

        LayerKind.IMAGE, LayerKind.TEXT, LayerKind.STICKER -> {
            textures.getOrPut(layer.segmentId) {
                GlUtil.createTextureFromBitmap(LayerBitmaps.render(layer))
            }
        }
    }

    private fun decoderFor(layer: RenderLayer): VideoDecoder? {
        decoders[layer.materialId]?.let { return it }
        val created = VideoDecoder(context, Uri.parse(layer.uri))
        if (!created.open()) {
            created.release()
            return null
        }
        decoders[layer.materialId] = created
        return created
    }

    /** Releases every decoder and texture owned by the renderer. */
    fun release() {
        decoders.values.forEach { runCatching { it.release() } }
        decoders.clear()
        textures.clear()
    }

    companion object {
        private const val TAG = "OpenCut/Renderer"
        private const val FRAME_TOLERANCE_US = 80_000L

        /** Builds the layer list for the current project state. */
        fun buildState(
            project: Project,
            playheadUs: Long,
            resolvedUris: Map<String, String> = emptyMap(),
        ): RenderState {
            val layers = mutableListOf<RenderLayer>()
            val ordered = project.tracks.sortedBy { it.type.order }
            for (track in ordered) {
                if (track.type == TrackType.AUDIO || track.hidden) continue

                val trackLayers = mutableListOf<RenderLayer>()
                for (segment in track.segments) {
                    if (!segment.visible) continue
                    val material = project.materials.find(segment.materialId) ?: continue
                    val kind = when (material.kind) {
                        MaterialKind.VIDEO -> LayerKind.VIDEO
                        MaterialKind.IMAGE -> LayerKind.IMAGE
                        MaterialKind.TEXT -> LayerKind.TEXT
                        MaterialKind.STICKER -> LayerKind.STICKER
                        else -> continue
                    }
                    val filterSpec = segment.extraMaterialRefs
                        .asSequence()
                        .mapNotNull { project.materials.find(it) }
                        .firstOrNull { it.kind == MaterialKind.FILTER }
                        ?.filter
                    // Keyframes override the static values of the layer at the
                    // current playhead, so an animated clip moves while it plays.
                    val localUs = (playheadUs - segment.target.start).coerceAtLeast(0L)
                    val frame = project.keyframes.evaluate(segment.id, localUs)
                    val clip = if (frame == null) {
                        segment.clip
                    } else {
                        segment.clip.copy(
                            transform = frame.position,
                            scale = frame.scale,
                            rotation = frame.rotation,
                            alpha = frame.alpha,
                        )
                    }
                    val adjustment = frame?.adjustment ?: material.adjust

                    trackLayers += RenderLayer(
                        segmentId = segment.id,
                        materialId = segment.materialId,
                        kind = kind,
                        uri = resolvedUris[segment.materialId] ?: material.path,
                        content = material.content,
                        payload = material.resourceId,
                        clip = clip,
                        adjustment = adjustment,
                        filter = filterSpec,
                        sourceStartUs = segment.source.start,
                        sourceDurationUs = segment.source.duration,
                        // Layers that are drawn by the app itself (text, stickers)
                        // have no intrinsic size, so they use the canvas size: the
                        // texture then matches the quad one to one and stays sharp.
                        sourceWidth = if (material.width > 0) material.width else project.canvas.width,
                        sourceHeight = if (material.height > 0) material.height else project.canvas.height,
                        targetStartUs = segment.target.start,
                        targetDurationUs = segment.target.duration,
                        speed = segment.speed,
                    )
                }
                layers += applyTransitions(project, track.segments, trackLayers, playheadUs)
            }
            return RenderState(
                project = project,
                playheadUs = playheadUs,
                layers = layers,
                backgroundColor = project.canvas.backgroundColor,
            )
        }

        /**
         * Applies the cross fade of a transition to the layer alphas.
         *
         * A transition is stored on the segment that starts it. While the playhead
         * is inside the transition window the outgoing clip fades out while the
         * incoming clip fades in.
         */
        private fun applyTransitions(
            project: Project,
            segments: List<com.zaitxcode.opencut.core.model.Segment>,
            layers: List<RenderLayer>,
            playheadUs: Long,
        ): List<RenderLayer> {
            if (segments.size < 2) return layers
            val result = layers.toMutableList()
            for (index in 1 until segments.size) {
                val incoming = segments[index]
                val outgoing = segments[index - 1]
                val transitionId = incoming.transitionInId ?: continue
                val transition = project.materials.find(transitionId) ?: continue
                val duration = transition.transition?.durationUs ?: continue
                if (duration <= 0L) continue

                val start = incoming.target.start
                val end = start + duration
                if (playheadUs < start || playheadUs >= end) continue

                val progress = (playheadUs - start).toFloat() / duration.toFloat()
                val outgoingIndex = result.indexOfFirst { it.segmentId == outgoing.id }
                val incomingIndex = result.indexOfFirst { it.segmentId == incoming.id }
                if (outgoingIndex >= 0) {
                    val layer = result[outgoingIndex]
                    result[outgoingIndex] = layer.copy(
                        clip = layer.clip.copy(alpha = layer.clip.alpha * (1f - progress)),
                    )
                }
                if (incomingIndex >= 0) {
                    val layer = result[incomingIndex]
                    result[incomingIndex] = layer.copy(
                        clip = layer.clip.copy(alpha = layer.clip.alpha * progress),
                    )
                }
            }
            return result
        }
    }
}
