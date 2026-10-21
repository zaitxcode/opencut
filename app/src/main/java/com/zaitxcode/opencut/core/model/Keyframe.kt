package com.zaitxcode.opencut.core.model

import kotlinx.serialization.Serializable

/** Discriminator for the animated property families. */
@Serializable
enum class KeyframeKind {
    VIDEO,
    TEXT,
    AUDIO,
    STICKER,
    ADJUST,
    FILTER,
}

/**
 * A single animated property.
 *
 * Keyframes are stored per layer (identified by the segment they belong to) and are
 * interpolated linearly between the surrounding keyframes. [timeOffsetUs] is relative
 * to the start of the owning segment, not to the project timeline.
 */
@Serializable
data class Keyframe(
    val id: String,
    val kind: KeyframeKind,
    val timeOffsetUs: Long = 0L,
    val position: Transform = Transform.ORIGIN,
    val scale: Scale = Scale.UNITY,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
    val volume: Float = 1f,
    val textAlpha: Float = 1f,
    val backgroundAlpha: Float = 0f,
    val borderWidth: Float = 0f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0x00000000,
    val borderColor: Int = 0x00000000,
    val filterValue: Float = 0f,
    val adjustment: Adjustment = Adjustment(),
    /** Curve speed keyframe graph, used by the curve speed ramp. */
    val curveSpeed: List<CurvePoint> = emptyList(),
) {
    /**
     * Linear interpolation towards [other] for a normalised blend factor.
     */
    fun lerp(other: Keyframe, t: Float): Keyframe = copy(
        position = Transform(
            x = position.x + (other.position.x - position.x) * t,
            y = position.y + (other.position.y - position.y) * t,
        ),
        scale = Scale(
            x = scale.x + (other.scale.x - scale.x) * t,
            y = scale.y + (other.scale.y - scale.y) * t,
        ),
        rotation = rotation + (other.rotation - rotation) * t,
        alpha = alpha + (other.alpha - alpha) * t,
        volume = volume + (other.volume - volume) * t,
        textAlpha = textAlpha + (other.textAlpha - textAlpha) * t,
        backgroundAlpha = backgroundAlpha + (other.backgroundAlpha - backgroundAlpha) * t,
        borderWidth = borderWidth + (other.borderWidth - borderWidth) * t,
        filterValue = filterValue + (other.filterValue - filterValue) * t,
        adjustment = adjustment.interpolate(other.adjustment, t),
    )
}

/**
 * Linear interpolation of two colour grading states.
 *
 * The interpolation of a numeric field is always continuous, while the "hsl
 * enabled" flag switches at the midpoint of the range.
 */
fun Adjustment.interpolate(other: Adjustment, t: Float): Adjustment {
    val mix = { a: Float, b: Float -> a + (b - a) * t }
    val hslOn = if (t < 0.5f) hslEnabled else other.hslEnabled
    return Adjustment(
        brightness = mix(brightness, other.brightness),
        contrast = mix(contrast, other.contrast),
        saturation = mix(saturation, other.saturation),
        sharpen = mix(sharpen, other.sharpen),
        highlight = mix(highlight, other.highlight),
        shadow = mix(shadow, other.shadow),
        temperature = mix(temperature, other.temperature),
        tone = mix(tone, other.tone),
        fade = mix(fade, other.fade),
        lightSensation = mix(lightSensation, other.lightSensation),
        vignette = mix(vignette, other.vignette),
        grain = mix(grain, other.grain),
        hslEnabled = hslOn,
        hue = mix(hue, other.hue),
        hslSaturation = mix(hslSaturation, other.hslSaturation),
        hslLightness = mix(hslLightness, other.hslLightness),
    )
}

/**
 * All keyframes of a project, grouped by layer id.
 *
 * Grouping by layer keeps lookups on the render path a single map lookup instead of
 * a scan over every keyframe in the document.
 */
@Serializable
data class KeyframeLibrary(
    val layers: Map<String, List<Keyframe>> = emptyMap(),
) {
    fun forLayer(layerId: String): List<Keyframe> = layers[layerId].orEmpty()

    /**
     * Evaluates the animated state of a layer at [timeUs] (relative to the layer).
     *
     * Returns `null` when the layer has no keyframes, which lets callers fall back
     * to the static values stored on the segment.
     */
    fun evaluate(layerId: String, timeUs: Long): Keyframe? {
        val frames = forLayer(layerId).sortedBy { it.timeOffsetUs }
        if (frames.isEmpty()) return null
        val first = frames.first()
        if (timeUs <= first.timeOffsetUs) return first
        val last = frames.last()
        if (timeUs >= last.timeOffsetUs) return last
        var index = 0
        while (index < frames.size - 1 && frames[index + 1].timeOffsetUs <= timeUs) index++
        val a = frames[index]
        val b = frames[index + 1]
        val span = (b.timeOffsetUs - a.timeOffsetUs).toFloat()
        if (span <= 0f) return b
        return a.lerp(b, (timeUs - a.timeOffsetUs).toFloat() / span)
    }
}
