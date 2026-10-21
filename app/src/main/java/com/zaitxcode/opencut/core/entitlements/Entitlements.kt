package com.zaitxcode.opencut.core.entitlements

/**
 * The feature catalogue of OpenCut.
 *
 * Every feature listed here is free. There is no paywall, no subscription, no
 * credit system and no watermark: [isUnlocked] always returns `true` by design.
 *
 * The list exists so the UI can show *what the editor can do* and so a future
 * fork can see exactly where a paid tier would have been in a commercial editor
 * — and choose not to put one there.
 */
enum class Feature(
    val title: String,
    val description: String,
    val group: FeatureGroup,
) {
    IMPORT_MEDIA(
        title = "Import media",
        description = "Videos, photos and audio from the device",
        group = FeatureGroup.EDITING,
    ),
    MULTI_TRACK(
        title = "Multi-track timeline",
        description = "Unlimited video, text, sticker, effect and audio layers",
        group = FeatureGroup.EDITING,
    ),
    SPLIT_TRIM(
        title = "Split and trim",
        description = "Frame accurate cutting on every track",
        group = FeatureGroup.EDITING,
    ),
    SPEED(
        title = "Speed control",
        description = "0.1× to 20× with automatic duration recalculation",
        group = FeatureGroup.EDITING,
    ),
    KEYFRAMES(
        title = "Keyframes",
        description = "Animate position, scale, rotation, opacity, volume and colour",
        group = FeatureGroup.EDITING,
    ),
    TRANSITIONS(
        title = "Transitions",
        description = "Cross fades between adjacent clips",
        group = FeatureGroup.VISUAL,
    ),
    FILTERS(
        title = "Colour filters",
        description = "Seven built-in looks, generated as 32³ lookup tables",
        group = FeatureGroup.VISUAL,
    ),
    ADJUST(
        title = "Colour adjust",
        description = "13 sliders: brightness, contrast, saturation and more",
        group = FeatureGroup.VISUAL,
    ),
    EFFECTS(
        title = "Effects",
        description = "Brighten, punch, soft, vignette and film grain",
        group = FeatureGroup.VISUAL,
    ),
    TEXT(
        title = "Text layers",
        description = "Titles and captions on their own track",
        group = FeatureGroup.VISUAL,
    ),
    STICKERS(
        title = "Stickers",
        description = "Overlay shapes and decorations",
        group = FeatureGroup.VISUAL,
    ),
    CANVAS(
        title = "Canvas ratios",
        description = "9:16, 16:9, 1:1, 4:3, 3:4 and 21:9",
        group = FeatureGroup.OUTPUT,
    ),
    EXPORT_1080(
        title = "1080p export",
        description = "H.264 video with mixed AAC audio",
        group = FeatureGroup.OUTPUT,
    ),
    EXPORT_4K(
        title = "4K export",
        description = "Up to 2160×3840 at 60 fps",
        group = FeatureGroup.OUTPUT,
    ),
    NO_WATERMARK(
        title = "No watermark",
        description = "Exports are always clean",
        group = FeatureGroup.OUTPUT,
    ),
    OFFLINE(
        title = "Works offline",
        description = "No network connection required for editing or export",
        group = FeatureGroup.PRIVACY,
    ),
    NO_ACCOUNT_REQUIRED(
        title = "No account required",
        description = "Start editing immediately, sign in only if you want to",
        group = FeatureGroup.PRIVACY,
    ),
    LOCAL_PROJECTS(
        title = "Local projects",
        description = "Your drafts stay on your device as plain JSON",
        group = FeatureGroup.PRIVACY,
    ),
}

/** Grouping used by the about screen. */
enum class FeatureGroup(val title: String) {
    EDITING("Editing"),
    VISUAL("Look and feel"),
    OUTPUT("Export"),
    PRIVACY("Privacy"),
}

/**
 * Entitlement checks.
 *
 * The editor is fully free and open source, so this object deliberately has no
 * storage, no receipt validation and no remote call. It answers `true` for every
 * feature and records why, so the behaviour is explicit rather than accidental.
 */
object Entitlements {

    /** Human readable plan name shown in the account screen. */
    const val PLAN_NAME: String = "Open source — everything free"

    /** Short explainer shown under the plan name. */
    const val PLAN_DETAIL: String =
        "No subscription, no credits, no watermark. All features are included for everyone."

    /** True for every feature, always. */
    fun isUnlocked(@Suppress("UNUSED_PARAMETER") feature: Feature): Boolean = true

    /** The reason a feature is available, used by the UI. */
    fun reason(@Suppress("UNUSED_PARAMETER") feature: Feature): String = "Free forever"

    /** Features grouped for display. */
    fun grouped(): Map<FeatureGroup, List<Feature>> =
        Feature.entries.groupBy { it.group }

    /** All features, in declaration order. */
    val all: List<Feature> get() = Feature.entries
}
