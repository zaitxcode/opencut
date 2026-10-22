package com.zaitxcode.opencut.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** One item of the media library of the device. */
data class DeviceMediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val durationUs: Long,
    val sizeBytes: Long,
    val dateAdded: Long,
)

/**
 * Reads the media library of the device.
 *
 * The system picker is used for the first import, but it is not always helpful:
 * on some devices the recent list of the picker is empty even though the gallery
 * is full. The browser below lists the same files directly, so importing always
 * works.
 */
object DeviceMedia {

    /** The libraries that are scanned for media. */
    fun list(context: Context, kind: MediaKind? = null, limit: Int = 200): List<DeviceMediaItem> {
        val items = mutableListOf<DeviceMediaItem>()
        val kinds = kind?.let { listOf(it) } ?: listOf(MediaKind.VIDEO, MediaKind.IMAGE, MediaKind.AUDIO)
        for (entry in kinds) {
            items += query(context, entry, limit)
        }
        return items.sortedByDescending { it.dateAdded }
    }

    private fun query(context: Context, kind: MediaKind, limit: Int): List<DeviceMediaItem> {
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val result = mutableListOf<DeviceMediaItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext() && result.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "media"
                    result += DeviceMediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        kind = kind,
                        durationUs = 0L,
                        sizeBytes = cursor.getLong(sizeColumn),
                        dateAdded = cursor.getLong(dateColumn) * 1000L,
                    )
                }
            }
        }
        return result
    }
}
