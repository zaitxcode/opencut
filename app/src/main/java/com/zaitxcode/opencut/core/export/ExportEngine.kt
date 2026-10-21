package com.zaitxcode.opencut.core.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.render.FrameComposer
import com.zaitxcode.opencut.core.render.LayerKind
import com.zaitxcode.opencut.core.render.PreviewRenderer
import com.zaitxcode.opencut.core.render.RenderLayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Settings of one export job. */
data class ExportSettings(
    val width: Int = 1080,
    val height: Int = 1920,
    val frameRate: Int = 30,
    val bitRate: Int = 8_000_000,
    val includeAudio: Boolean = true,
)

/** Progress reported while exporting. */
data class ExportProgress(
    val percent: Int,
    val stage: String,
)

/**
 * Renders a project into an MP4 file.
 *
 * The video pass uses a hardware encoder: the frames are rendered with the same
 * OpenGL pipeline as the preview and pushed into the encoder input surface. The audio
 * pass decodes every audio layer, mixes it into a single stereo stream and encodes it
 * with AAC. Both streams are written into one MP4 container.
 */
class ExportEngine(private val context: Context) {

    /**
     * Exports [project] into [output].
     *
     * The function is blocking and must be called from a background thread.
     */
    fun export(
        project: Project,
        output: File,
        settings: ExportSettings,
        onProgress: (ExportProgress) -> Unit = {},
    ): Result<File> = runCatching {
        val durationUs = project.durationUs.coerceAtLeast(1_000_000L)
        val frameCount = ((durationUs / 1_000_000.0) * settings.frameRate).roundToInt().coerceAtLeast(1)

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var audioTrack = -1

        // ------------------------------------------------------------- video --
        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            settings.width,
            settings.height,
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, settings.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val egl = EglBridge(inputSurface)
        egl.makeCurrent()
        val composer = FrameComposer()
        composer.init()

        val layers = PreviewRenderer.buildState(project, 0L).layers
        val decoders = HashMap<String, com.zaitxcode.opencut.core.render.VideoDecoder>()
        val textures = HashMap<String, Int>()

        val bufferInfo = MediaCodec.BufferInfo()
        var muxerStarted = false
        var framesEncoded = 0

        for (frameIndex in 0 until frameCount) {
            val playheadUs = frameIndex * 1_000_000L / settings.frameRate
            composer.clear(project.canvas.backgroundColor)
            composer.setViewport(
                project.canvas.width,
                project.canvas.height,
                settings.width,
                settings.height,
            )
            composer.enableBlend()
            for (layer in layers) {
                if (playheadUs !in layer.targetStartUs until (layer.targetStartUs + layer.targetDurationUs)) continue
                val texture = when (layer.kind) {
                    LayerKind.VIDEO -> {
                        val decoder = decoders[layer.materialId] ?: run {
                            val created = com.zaitxcode.opencut.core.render.VideoDecoder(
                                context,
                                Uri.parse(layer.uri),
                            )
                            if (created.open()) {
                                decoders[layer.materialId] = created
                                created
                            } else {
                                null
                            }
                        }
                        decoder?.let {
                            val offset = playheadUs - layer.targetStartUs
                            val sourceTime = layer.sourceStartUs + (offset * layer.speed).toLong()
                            // Seeking on every frame would be far too slow, so the
                            // decoder is only repositioned when it drifted away.
                            if (!it.isNear(sourceTime)) it.seekTo(sourceTime)
                            it.advanceTo(sourceTime)
                            it.textureId
                        }
                    }

                    else -> textures.getOrPut(layer.segmentId) {
                        com.zaitxcode.opencut.core.render.GlUtil.createTextureFromBitmap(
                            textBitmap(layer, project.canvas.width, project.canvas.height),
                        )
                    }
                } ?: continue
                val look = com.zaitxcode.opencut.core.render.LutFactory.lookOf(layer.filter)
                composer.drawLayer(
                    layer = layer,
                    texture = texture,
                    canvasWidth = project.canvas.width,
                    canvasHeight = project.canvas.height,
                    playheadUs = playheadUs,
                    lutTexture = look?.let { com.zaitxcode.opencut.core.render.LutFactory.textureFor(it) } ?: 0,
                    lutIntensity = layer.filter?.intensity ?: 1f,
                )
            }
            composer.disableBlend()
            egl.swapBuffers()

            // Drain the encoder and write into the container.
            while (true) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxerStarted) { "Video format changed twice" }
                    videoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                    continue
                }
                if (outputIndex < 0) continue
                val encoded = encoder.getOutputBuffer(outputIndex)
                if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                    encoded.position(bufferInfo.offset)
                    encoded.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }

            framesEncoded++
            if (frameIndex % 4 == 0) {
                onProgress(
                    ExportProgress(
                        percent = (framesEncoded * 80 / frameCount),
                        stage = "Rendering",
                    ),
                )
            }
        }

        // Flush the encoder.
        encoder.signalEndOfInputStream()
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    videoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                continue
            }
            if (outputIndex < 0) continue
            val encoded = encoder.getOutputBuffer(outputIndex)
            if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                encoded.position(bufferInfo.offset)
                encoded.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(videoTrack, encoded, bufferInfo)
            }
            encoder.releaseOutputBuffer(outputIndex, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }

        encoder.stop()
        encoder.release()
        egl.release()
        decoders.values.forEach { runCatching { it.release() } }

        // ------------------------------------------------------------- audio --
        if (settings.includeAudio) {
            onProgress(ExportProgress(percent = 85, stage = "Mixing audio"))
            audioTrack = AudioMixer(context).encodeInto(
                project = project,
                durationUs = durationUs,
                muxer = muxer,
            )
        }

        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
        onProgress(ExportProgress(percent = 100, stage = "Finished"))
        output
    }

    /** Renders the text of a layer into a bitmap that can be used as a texture. */
    private fun textBitmap(
        layer: RenderLayer,
        canvasWidth: Int,
        canvasHeight: Int,
    ): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            canvasWidth,
            canvasHeight,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = canvasHeight * 0.06f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(layer.content, canvasWidth / 2f, canvasHeight / 2f, paint)
        return bitmap
    }
}

/**
 * A tiny EGL helper that renders into a codec input surface.
 */
private class EglBridge(private val surface: android.view.Surface) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, configCount, 0) &&
                configCount[0] > 0
        ) { "no EGL config" }
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, surfaceAttribs, 0)
    }

    fun makeCurrent() {
        EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
    }

    fun swapBuffers() {
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun release() {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}
