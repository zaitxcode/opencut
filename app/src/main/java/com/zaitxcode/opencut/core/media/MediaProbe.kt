package com.zaitxcode.opencut.core.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** Metadata about a media file. */
data class MediaInfo(
    val uri: Uri,
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
 */
object MediaProbe {

    fun probe(context: Context, uri: Uri): MediaInfo? {
        val path = uri.path
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
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> {
                        hasVideo = true
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    }

                    mime.startsWith("audio/") -> hasAudio = true
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

        val swap = rotation == 90 || rotation == 270
        return MediaInfo(
            uri = uri,
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
}
