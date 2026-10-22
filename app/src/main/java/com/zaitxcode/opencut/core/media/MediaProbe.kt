package com.zaitxcode.opencut.core.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** The family of a media file, as far as the editor cares about it. */
enum class MediaKind {
    VIDEO,
    IMAGE,
    AUDIO,
    ;

    /** True when the file contributes pictures to the canvas. */
    val isVisual: Boolean get() = this == VIDEO || this == IMAGE
}

/** Metadata about a media file. */
data class MediaInfo(
    val uri: Uri,
    val kind: MediaKind,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val rotation: Int,
    val displayWidth: Int,
    val displayHeight: Int,
) {
    val aspect: Float
        get() = if (displayHeight == 0) 1f else displayWidth.toFloat() / displayHeight.toFloat()
}

/**
 * Reads metadata straight from the container.
 *
 * `MediaMetadataRetriever` is used for the coarse values and `MediaExtractor` for the
 * exact track layout, because the retriever rounds durations to milliseconds while
 * the extractor exposes the real microsecond duration.
 *
 * Still images are handled separately: they have no timeline of their own, so their
 * size is read from the bitmap header and the editor gives them a fixed duration.
 */
object MediaProbe {

    /** Duration given to a still image when it is dropped on the timeline. */
    const val IMAGE_DURATION_US = 3_000_000L

    fun probe(context: Context, uri: Uri): MediaInfo? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/") || looksLikeImage(uri)) {
            return probeImage(uri)
        }

        val retriever = MediaMetadataRetriever()
        var durationUs = 0L
        var width = 0
        var height = 0
        var rotation = 0
        try {
            retriever.setDataSource(context, uri)
            durationUs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.times(1000) ?: 0L
            width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            // Fall through to the extractor based probing below.
        } finally {
            runCatching { retriever.release() }
        }

        var frameRate = 30f
        var hasAudio = false
        var hasVideo = false
        var extractorDuration = 0L
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, emptyMap())
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    trackMime.startsWith("video/") -> {
                        hasVideo = true
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    }

                    trackMime.startsWith("audio/") -> hasAudio = true
                }
            }
            extractorDuration = extractor
                .let { e ->
                    var max = 0L
                    for (index in 0 until e.trackCount) {
                        val format = e.getTrackFormat(index)
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            max = maxOf(max, format.getLong(MediaFormat.KEY_DURATION))
                        }
                    }
                    max
                }
            extractor.release()
        }

        if (durationUs == 0L) durationUs = extractorDuration
        if (durationUs == 0L && width == 0 && height == 0) return null

        val kind = when {
            hasVideo -> MediaKind.VIDEO
            hasAudio -> MediaKind.AUDIO
            mime.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.IMAGE
        }

        val swap = rotation == 90 || rotation == 270
        return MediaInfo(
            uri = uri,
            kind = kind,
            durationUs = durationUs,
            width = width,
            height = height,
            frameRate = frameRate,
            hasAudio = hasAudio,
            hasVideo = hasVideo,
            rotation = rotation,
            displayWidth = if (swap) height else width,
            displayHeight = if (swap) width else height,
        )
    }

    /**
     * Reads the size of a still image.
     *
     * Only the bitmap header is decoded, so a large photo costs a few kilobytes.
     */
    private fun probeImage(uri: Uri): MediaInfo? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = openStream(uri) ?: return null
        val width: Int
        val height: Int
        try {
            BitmapFactory.decodeStream(opened, null, bounds)
            width = bounds.outWidth
            height = bounds.outHeight
        } finally {
            runCatching { opened.close() }
        }
        if (width <= 0 || height <= 0) return null
        return MediaInfo(
            uri = uri,
            kind = MediaKind.IMAGE,
            durationUs = IMAGE_DURATION_US,
            width = width,
            height = height,
            frameRate = 0f,
            hasAudio = false,
            hasVideo = false,
            rotation = 0,
            displayWidth = width,
            displayHeight = height,
        )
    }

    private fun openStream(uri: Uri): java.io.InputStream? = runCatching {
        when (uri.scheme) {
            "file" -> File(uri.path.orEmpty()).inputStream()
            else -> null
        }
    }.getOrNull()

    /** Resolves a `content://` or `file://` uri into something the codecs can read. */
    fun fileDescriptorOf(context: Context, uri: Uri): Pair<android.os.ParcelFileDescriptor, Long>? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val length = runCatching {
            android.provider.OpenableColumns.SIZE.let { column ->
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                } ?: 0L
            }
        }.getOrDefault(0L)
        return pfd to length
    }

    fun isVideoFile(uri: Uri): Boolean {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi", "ts")
    }

    private fun looksLikeImage(uri: Uri): Boolean {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
    }
}
