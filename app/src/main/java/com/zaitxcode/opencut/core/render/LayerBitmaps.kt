package com.zaitxcode.opencut.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.min

/**
 * Draws the bitmap of every non video layer.
 *
 * The preview and the exporter share this code so that a sticker, a caption or a
 * photo looks exactly the same in the preview and in the exported file.
 */
object LayerBitmaps {

    /** Upper bound for the texture of a still layer; keeps the memory sane. */
    private const val MAX_SIZE = 1920

    /**
     * Renders the layer into a bitmap.
     *
     * The bitmap keeps the aspect ratio of the layer so that the texture can be
     * stretched over the quad the composer draws.
     */
    fun render(layer: RenderLayer): Bitmap {
        val width = layer.sourceWidth.coerceIn(16, MAX_SIZE)
        val height = layer.sourceHeight.coerceIn(16, MAX_SIZE)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (layer.kind) {
            LayerKind.IMAGE -> drawImage(canvas, layer, width, height)
            LayerKind.TEXT -> drawText(canvas, layer, width, height)
            LayerKind.STICKER -> drawSticker(canvas, layer, width, height)
            LayerKind.VIDEO -> canvas.drawColor(Color.DKGRAY)
        }
        return bitmap
    }

    // ----------------------------------------------------------------- image ---

    private fun drawImage(canvas: Canvas, layer: RenderLayer, width: Int, height: Int) {
        val decoded = decode(layer.uri, width, height) ?: run {
            // A missing file still produces a visible placeholder instead of an
            // invisible layer, so the user can see that something is missing.
            canvas.drawColor(Color.DKGRAY)
            return
        }
        val sourceWidth = decoded.width.toFloat()
        val sourceHeight = decoded.height.toFloat()
        val scale = min(width / sourceWidth, height / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(decoded, null, android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), paint)
        decoded.recycle()
    }

    private fun decode(uri: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        // The header is decoded first to know the real size of the picture. The
        // decoder returns no bitmap for a bounds only pass, so the stream is the
        // value that has to be checked.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = openStream(uri) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val sample = calculateSampleSize(options.outWidth, options.outHeight, targetWidth, targetHeight)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        return openStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun openStream(uri: String): InputStream? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (parsed.scheme == null || parsed.scheme == "file") {
            val path = parsed.path ?: return null
            val file = File(path)
            return if (file.exists()) file.inputStream() else null
        }
        return null
    }

    // ------------------------------------------------------------------ text ---

    private fun drawText(canvas: Canvas, layer: RenderLayer, width: Int, height: Int) {
        val content = layer.content.ifBlank { " " }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.06f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(height * 0.01f, 0f, height * 0.005f, Color.argb(160, 0, 0, 0))
        }
        val lines = wrap(content, paint, width * 0.86f)
        val lineHeight = paint.textSize * 1.25f
        var baseline = height / 2f - (lines.size - 1) * lineHeight / 2f
        for (line in lines) {
            canvas.drawText(line, width / 2f, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                result += ""
                continue
            }
            var line = words.first()
            for (word in words.drop(1)) {
                val candidate = "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line = candidate
                } else {
                    result += line
                    line = word
                }
            }
            result += line
        }
        return result.ifEmpty { listOf("") }
    }

    // --------------------------------------------------------------- sticker ---

    /**
     * Draws one of the built in shapes.
     *
     * The shapes are vector paths, so they stay sharp at any canvas size and the
     * app ships no binary assets.
     */
    private fun drawSticker(canvas: Canvas, layer: RenderLayer, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height) * 0.8f
        when (layer.payload.lowercase()) {
            "square" -> canvas.drawRoundRect(
                cx - size / 2f,
                cy - size / 2f,
                cx + size / 2f,
                cy + size / 2f,
                size * 0.12f,
                size * 0.12f,
                paint,
            )

            "star" -> canvas.drawPath(starPath(cx, cy, size), paint)
            "heart" -> canvas.drawPath(heartPath(cx, cy, size), paint)
            "arrow" -> canvas.drawPath(arrowPath(cx, cy, size), paint)
            "sparkle" -> {
                canvas.drawPath(sparklePath(cx, cy, size), paint)
                paint.color = Color.argb(200, 255, 235, 150)
                canvas.drawPath(sparklePath(cx + size * 0.28f, cy - size * 0.28f, size * 0.35f), paint)
            }

            else -> {
                canvas.drawCircle(cx, cy, size / 2f, paint)
                paint.color = Color.argb(150, 0, 0, 0)
                canvas.drawCircle(cx, cy, size / 2.6f, paint)
            }
        }
    }

    private fun starPath(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        val outer = size / 2f
        val inner = outer * 0.45f
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + point * 36.0)
            val x = cx + (kotlin.math.cos(angle) * radius).toFloat()
            val y = cy + (kotlin.math.sin(angle) * radius).toFloat()
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun heartPath(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        val w = size / 2f
        val h = size / 2f
        path.moveTo(cx, cy + h)
        path.cubicTo(cx - w * 1.4f, cy - h * 0.2f, cx - w * 0.6f, cy - h * 1.1f, cx, cy - h * 0.35f)
        path.cubicTo(cx + w * 0.6f, cy - h * 1.1f, cx + w * 1.4f, cy - h * 0.2f, cx, cy + h)
        path.close()
        return path
    }

    private fun arrowPath(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        val w = size / 2f
        val h = size / 2f
        path.moveTo(cx - w, cy - h * 0.3f)
        path.lineTo(cx + w * 0.2f, cy - h * 0.3f)
        path.lineTo(cx + w * 0.2f, cy - h * 0.7f)
        path.lineTo(cx + w, cy)
        path.lineTo(cx + w * 0.2f, cy + h * 0.7f)
        path.lineTo(cx + w * 0.2f, cy + h * 0.3f)
        path.lineTo(cx - w, cy + h * 0.3f)
        path.close()
        return path
    }

    private fun sparklePath(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        val h = size / 2f
        val w = size * 0.28f
        path.moveTo(cx, cy - h)
        path.lineTo(cx + w, cy)
        path.lineTo(cx, cy + h)
        path.lineTo(cx - w, cy)
        path.close()
        return path
    }
}
