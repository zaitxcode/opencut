package com.zaitxcode.opencut.core.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes a video track into an OpenGL external texture.
 *
 * The decoder owns a [SurfaceTexture] bound to an EGL surface. Frames are rendered
 * by asking the codec to release output buffers with `render = true`, which makes the
 * platform draw the decoded frame into the texture.
 */
class VideoDecoder(
    private val context: Context,
    private val uri: Uri,
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var trackIndex = -1
    private var inputDone = false
    private var release = false

    /** Set by the producer thread whenever a decoded frame reaches the surface. */
    @Volatile
    private var frameAvailable = false

    /** True once a frame has actually been rendered into the texture. */
    private var hasFrame = false

    val textureId: Int = createExternalTexture()
    private val released = AtomicBoolean(false)

    /** Presentation time of the most recently rendered frame, in microseconds. */
    @Volatile
    var lastFrameTimeUs: Long = 0L
        private set

    /**
     * True when the decoder is close enough to [timeUs] that decoding forward is
     * cheaper than seeking.
     */
    fun isNear(timeUs: Long, toleranceUs: Long = 500_000L): Boolean =
        kotlin.math.abs(lastFrameTimeUs - timeUs) <= toleranceUs

    /** Open the track and start the decoder. Returns false when the file has no video. */
    fun open(): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            var index = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    index = i
                    format = candidate
                    break
                }
            }
            if (index < 0 || format == null) {
                extractor.release()
                return false
            }
            this.extractor = extractor
            this.trackIndex = index
            extractor.selectTrack(index)

            // The texture must match the coded size, otherwise the decoder scales
            // the picture into a wrongly sized buffer.
            val codedWidth = format.getInteger(MediaFormat.KEY_WIDTH).coerceAtLeast(16)
            val codedHeight = format.getInteger(MediaFormat.KEY_HEIGHT).coerceAtLeast(16)
            val texture = SurfaceTexture(textureId).also {
                it.setDefaultBufferSize(codedWidth, codedHeight)
                it.setOnFrameAvailableListener { frameAvailable = true }
            }
            surfaceTexture = texture

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, surfaceFor(texture), null, 0)
            codec.start()
            this.codec = codec
            true
        } catch (error: Throwable) {
            Log.w(TAG, "open failed", error)
            release()
            false
        }
    }

    /**
     * Seeks the decoder so the next rendered frame is the one at [timeUs].
     *
     * A key frame seek is used, which is fast enough for an interactive preview and
     * does not need an exact (but slow) search.
     */
    fun seekTo(timeUs: Long) {
        val extractor = extractor ?: return
        val codec = codec ?: return
        try {
            extractor.seekTo(timeUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
            inputDone = false
            frameAvailable = false
            hasFrame = false
        } catch (error: Throwable) {
            Log.w(TAG, "seek failed", error)
        }
    }

    /**
     * Copies the newest decoded frame into the external texture.
     *
     * A frame is produced asynchronously by the codec, so the frame that was just
     * submitted is usually not ready yet; the renderer calls this every frame and
     * picks the picture up as soon as the producer delivers it. Must run on the GL
     * thread that owns [textureId].
     */
    fun latchFrame(): Boolean {
        if (!frameAvailable) return false
        frameAvailable = false
        return try {
            surfaceTexture?.updateTexImage()
            true
        } catch (error: Throwable) {
            Log.w(TAG, "updateTexImage failed", error)
            false
        }
    }

    /**
     * Decodes forward until a frame at or after [untilUs] has been rendered.
     *
     * Returns the presentation time of the frame that is now on the texture.
     */
    fun advanceTo(untilUs: Long): Long {
        val codec = codec ?: return 0L
        val extractor = extractor ?: return 0L
        if (hasFrame && untilUs <= lastFrameTimeUs && lastFrameTimeUs - untilUs < FRAME_TOLERANCE_US) {
            return lastFrameTimeUs
        }
        val bufferInfo = MediaCodec.BufferInfo()
        var guard = 0
        while (guard++ < MAX_DECODE_STEPS) {
            if (release) return lastFrameTimeUs
            var inputIndex = -1
            try {
                inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            } catch (_: Throwable) {
                return lastFrameTimeUs
            }
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                val size = if (inputBuffer != null) {
                    extractor.readSampleData(inputBuffer, 0)
                } else {
                    -1
                }
                if (size < 0) {
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        0L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
                continue
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val presentationUs = bufferInfo.presentationTimeUs
                val render = bufferInfo.size > 0
                codec.releaseOutputBuffer(outputIndex, render)
                if (render) {
                    lastFrameTimeUs = presentationUs
                    hasFrame = true
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return lastFrameTimeUs
                }
                if (render && presentationUs >= untilUs) {
                    return lastFrameTimeUs
                }
                continue
            }
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (inputDone) return lastFrameTimeUs
            }
        }
        return lastFrameTimeUs
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        release = true
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        runCatching { surface?.release() }
        runCatching { surfaceTexture?.release() }
        codec = null
        extractor = null
        surfaceTexture = null
        surface = null
    }

    // ------------------------------------------------------------------ gl ---

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(
            android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return ids[0]
    }

    /**
     * The codec writes straight into the texture's buffer queue, so no EGL surface
     * is needed for decoding.
     */
    private fun surfaceFor(texture: SurfaceTexture): Surface = Surface(texture)

    private companion object {
        const val TAG = "OpenCut/Decoder"
        const val TIMEOUT_US = 10_000L
        const val MAX_DECODE_STEPS = 96
        const val FRAME_TOLERANCE_US = 80_000L
    }
}
