package com.zaitxcode.opencut.core.model

import kotlinx.serialization.Serializable

/** Discriminator for the different asset families a project can reference. */
@Serializable
enum class MaterialKind {
    VIDEO,
    IMAGE,
    AUDIO,
    TEXT,
    STICKER,
    EFFECT,
    FILTER,
    TRANSITION,
    CANVAS,
    SPEED,
    VOLUME,
    MASK,
    CHROMA,
    PRIMARY_WHEELS,
    LOG_WHEELS,
    CURVES,
    ANIMATION,
    TEMPLATE,
    BEAT,
    RESOURCE,
}

/**
 * A project asset.
 *
 * One flat structure is used for every asset family: the editor mutates single
 * fields in place (for example `adjust.brightness`), and a flat layout keeps those
 * mutations cheap and the on-disk format stable. Fields that do not apply to a
 * given [kind] simply stay at their default value.
 */
@Serializable
data class Material(
    val id: String,
    val kind: MaterialKind,
    /** File system path for media backed assets, or an asset id for packaged ones. */
    val path: String = "",
    val name: String = "",
    val durationUs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 30f,
    val hasAudio: Boolean = false,
    val category: String = "",
    /** Free form payload used by text and sticker assets. */
    val content: String = "",
    val resourceId: String = "",
    /** Colour filter / LUT descriptor. */
    val filter: FilterSpec? = null,
    /** Colour grading values applied to a video or image layer. */
    val adjust: Adjustment = Adjustment(),
    /** Curve speed ramp descriptor. */
    val speedSpec: SpeedSpec? = null,
    /** Volume envelope descriptor. */
    val volumeSpec: VolumeSpec? = null,
    /** Chroma key descriptor. */
    val chroma: ChromaSpec? = null,
    /** Mask / matting descriptor. */
    val mask: MaskSpec? = null,
    /** Primary colour wheels descriptor. */
    val primaryWheels: ColorWheelsSpec? = null,
    /** Log colour wheels descriptor. */
    val logWheels: ColorWheelsSpec? = null,
    /** Colour curve descriptor. */
    val curves: CurvesSpec? = null,
    /** Sticker or effect animation descriptor. */
    val animation: AnimationSpec? = null,
    /** Transition descriptor (used by segments referencing a transition material). */
    val transition: TransitionSpec? = null,
    /** Effect strength in 0..1 for effect materials. */
    val effectStrength: Float = 1f,
    /** Optional secondary strength used by two-slider effects. */
    val effectStrengthSecondary: Float = 0f,
) {
    val isMedia: Boolean
        get() = kind == MaterialKind.VIDEO || kind == MaterialKind.IMAGE

    val isAudio: Boolean get() = kind == MaterialKind.AUDIO
}

/** LUT / colour-filter descriptor. */
@Serializable
data class FilterSpec(
    val id: String = "",
    val name: String = "",
    /** Path of a `.cube` file inside the app's asset folder. */
    val path: String = "",
    val intensity: Float = 1f,
)

/**
 * Colour grading parameters.
 *
 * Every value is a normalised adjustment where 0 is "no change"; the renderer maps
 * them to concrete shader uniforms.
 */
@Serializable
data class Adjustment(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpen: Float = 0f,
    val highlight: Float = 0f,
    val shadow: Float = 0f,
    val temperature: Float = 0f,
    val tone: Float = 0f,
    val fade: Float = 0f,
    val lightSensation: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val hslEnabled: Boolean = false,
    val hue: Float = 0f,
    val hslSaturation: Float = 0f,
    val hslLightness: Float = 0f,
) {
    val isNeutral: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && sharpen == 0f &&
            highlight == 0f && shadow == 0f && temperature == 0f && tone == 0f &&
            fade == 0f && lightSensation == 0f && vignette == 0f && grain == 0f &&
            !hslEnabled
}

/** Curve based speed ramp: control points mapped over normalised time. */
@Serializable
data class SpeedSpec(
    val mode: String = "uniform",
    val curvePoints: List<CurvePoint> = emptyList(),
)

@Serializable
data class CurvePoint(
    val x: Float,
    val y: Float,
)

/** Volume envelope. */
@Serializable
data class VolumeSpec(
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
)

/** Chroma key (green screen) descriptor. */
@Serializable
data class ChromaSpec(
    val color: Int = 0xFF00FF00.toInt(),
    val intensity: Float = 0.5f,
    val shadow: Float = 0f,
    val spill: Float = 0f,
)

/** Elliptical / rectangular mask descriptor. */
@Serializable
data class MaskSpec(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
    val feather: Float = 0f,
    val cornerRadius: Float = 0f,
    val rotation: Float = 0f,
    val inverted: Boolean = false,
)

/** Three colour wheels (lift / gamma / gain style). */
@Serializable
data class ColorWheelsSpec(
    val intensity: Float = 0f,
    val shadowTint: Int = 0x00000000,
    val midToneTint: Int = 0x00000000,
    val highlightTint: Int = 0x00000000,
)

/** RGB curve descriptor with per-channel control points. */
@Serializable
data class CurvesSpec(
    val luma: List<CurvePoint> = emptyList(),
    val red: List<CurvePoint> = emptyList(),
    val green: List<CurvePoint> = emptyList(),
    val blue: List<CurvePoint> = emptyList(),
)

/** In / out animation descriptor for stickers, text and effects. */
@Serializable
data class AnimationSpec(
    val type: String = "none",
    val durationUs: Long = 0L,
    val resourceId: String = "",
)

/** Transition descriptor. */
@Serializable
data class TransitionSpec(
    val name: String = "",
    val durationUs: Long = 0L,
    val overlap: Boolean = true,
    val resourceId: String = "",
)

/** The full set of assets referenced by a project. */
@Serializable
data class MaterialLibrary(
    val videos: List<Material> = emptyList(),
    val audios: List<Material> = emptyList(),
    val images: List<Material> = emptyList(),
    val texts: List<Material> = emptyList(),
    val stickers: List<Material> = emptyList(),
    val effects: List<Material> = emptyList(),
    val filters: List<Material> = emptyList(),
    val transitions: List<Material> = emptyList(),
    val canvases: List<Material> = emptyList(),
    val speeds: List<Material> = emptyList(),
    val volumes: List<Material> = emptyList(),
    val masks: List<Material> = emptyList(),
    val chromas: List<Material> = emptyList(),
    val primaryWheels: List<Material> = emptyList(),
    val logWheels: List<Material> = emptyList(),
    val curves: List<Material> = emptyList(),
    val animations: List<Material> = emptyList(),
    val templates: List<Material> = emptyList(),
    val resources: List<Material> = emptyList(),
) {
    val all: List<Material>
        get() = videos + audios + images + texts + stickers + effects + filters +
            transitions + canvases + speeds + volumes + masks + chromas +
            primaryWheels + logWheels + curves + animations + templates + resources

    fun find(id: String): Material? = all.firstOrNull { it.id == id }
}

/**
 * The root document persisted on disk.
 *
 * It is intentionally close to a plain data record so that it can be read and
 * written without any runtime dependency on the editor UI.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val canvas: Canvas = Canvas(),
    val tracks: List<Track> = emptyList(),
    val materials: MaterialLibrary = MaterialLibrary(),
    val keyframes: KeyframeLibrary = KeyframeLibrary(),
    val durationUs: Long = 0L,
    val frameRate: Float = 30f,
) {
    fun track(id: String): Track? = tracks.firstOrNull { it.id == id }

    fun segment(segmentId: String): Pair<Track, Segment>? {
        for (track in tracks) {
            val segment = track.segments.firstOrNull { it.id == segmentId }
            if (segment != null) return track to segment
        }
        return null
    }
}
