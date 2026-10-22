package com.zaitxcode.opencut.ui.editor.panel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zaitxcode.opencut.R
import com.zaitxcode.opencut.core.media.DeviceMedia
import com.zaitxcode.opencut.core.media.DeviceMediaItem
import com.zaitxcode.opencut.core.media.MediaKind
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.KeyframeKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Segment
import com.zaitxcode.opencut.ui.editor.EditorViewModel
import com.zaitxcode.opencut.ui.editor.ToolChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The tools that can open a panel from the dock. */
enum class Tool {
    MEDIA,
    AUDIO,
    TEXT,
    STICKER,
    FILTER,
    EFFECT,
    ADJUST,
    TRANSITION,
    SPEED,
    CANVAS,
    CROP,
    KEYFRAME,
}

/**
 * Dispatches to the panel of the selected tool.
 *
 * Every panel receives the same context so that a tool can act on the current
 * selection (for example the adjust panel edits the selected layer).
 */
@Composable
fun PanelHost(
    tool: Tool,
    project: Project,
    selectedSegment: Segment?,
    viewModel: EditorViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
    ) {
        when (tool) {
            Tool.MEDIA -> MediaPanel(viewModel)
            Tool.AUDIO -> AudioPanel(selectedSegment, viewModel)
            Tool.TEXT -> TextPanel(viewModel)
            Tool.STICKER -> StickerPanel(viewModel)
            Tool.FILTER -> FilterPanel(selectedSegment, viewModel)
            Tool.EFFECT -> EffectPanel(selectedSegment, viewModel)
            Tool.ADJUST -> AdjustPanel(selectedSegment, viewModel)
            Tool.TRANSITION -> TransitionPanel(selectedSegment, viewModel)
            Tool.SPEED -> SpeedPanel(selectedSegment, viewModel)
            Tool.CANVAS -> CanvasPanel(project, viewModel)
            Tool.CROP -> CropPanel(selectedSegment, viewModel)
            Tool.KEYFRAME -> KeyframePanel(selectedSegment, viewModel)
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
    )
}

// ------------------------------------------------------------------ media ---

@Composable
private fun MediaPanel(viewModel: EditorViewModel) {
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.importMedia(uris) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.importMedia(uris) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importMedia(listOf(it)) } }
    var browsing by remember { mutableStateOf(false) }

    Column {
        PanelTitle(stringResource(R.string.tool_media))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(
                label = stringResource(R.string.media_add_video),
                selected = false,
                onClick = {
                    mediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            )
            ToolChip(
                label = stringResource(R.string.media_add_photo),
                selected = false,
                onClick = {
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            ToolChip(
                label = stringResource(R.string.media_add_audio),
                selected = false,
                onClick = { audioLauncher.launch(arrayOf("audio/*")) },
            )
            ToolChip(
                label = stringResource(R.string.media_browse),
                selected = false,
                onClick = { browsing = true },
            )
        }
        Spacer(Modifier.height(4.dp))
        Hint(stringResource(R.string.media_hint))
    }

    if (browsing) {
        DeviceMediaSheet(
            onDismiss = { browsing = false },
            onPick = { uri ->
                browsing = false
                viewModel.importMedia(listOf(uri))
            },
        )
    }
}

/**
 * Lists the media library of the device and imports the item that is tapped.
 *
 * It is the reliable way to add material: the system picker hides files on some
 * devices, while the library below always shows what the gallery has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceMediaSheet(
    onDismiss: () -> Unit,
    onPick: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf<MediaKind?>(null) }
    var items by remember { mutableStateOf<List<DeviceMediaItem>>(emptyList()) }
    var allowed by remember { mutableStateOf(hasMediaPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        allowed = granted.values.any { it }
    }

    LaunchedEffect(filter, allowed) {
        if (allowed) {
            items = withContext(Dispatchers.IO) { DeviceMedia.list(context, filter) }
        }
    }

    LaunchedEffect(Unit) {
        if (!allowed) permissionLauncher.launch(mediaPermissions())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            PanelTitle(stringResource(R.string.media_browse_title))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolChip(
                    label = stringResource(R.string.media_all),
                    selected = filter == null,
                    onClick = { filter = null },
                )
                ToolChip(
                    label = stringResource(R.string.tool_media),
                    selected = filter == MediaKind.VIDEO,
                    onClick = { filter = MediaKind.VIDEO },
                )
                ToolChip(
                    label = stringResource(R.string.media_add_photo),
                    selected = filter == MediaKind.IMAGE,
                    onClick = { filter = MediaKind.IMAGE },
                )
                ToolChip(
                    label = stringResource(R.string.media_add_audio),
                    selected = filter == MediaKind.AUDIO,
                    onClick = { filter = MediaKind.AUDIO },
                )
            }
            Spacer(Modifier.height(6.dp))
            if (items.isEmpty()) {
                Hint(stringResource(R.string.media_empty))
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(items.size) { index ->
                        val item = items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item.uri) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = when (item.kind) {
                                        MediaKind.VIDEO -> stringResource(R.string.media_add_video)
                                        MediaKind.IMAGE -> stringResource(R.string.media_add_photo)
                                        MediaKind.AUDIO -> stringResource(R.string.media_add_audio)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(R.string.media_add),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ audio ---

@Composable
private fun AudioPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importMedia(listOf(it)) } }

    Column {
        PanelTitle(stringResource(R.string.tool_audio))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(
                label = stringResource(R.string.audio_add),
                selected = false,
                onClick = { audioLauncher.launch(arrayOf("audio/*")) },
            )
        }
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        LabeledSlider(
            label = stringResource(R.string.audio_volume),
            value = selectedSegment.volume,
            range = 0f..2f,
        ) { viewModel.setVolume(it) }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(
                label = if (selectedSegment.muted) stringResource(R.string.audio_unmute)
                else stringResource(R.string.audio_mute),
                selected = selectedSegment.muted,
                onClick = { viewModel.setMuted(!selectedSegment.muted) },
            )
        }
    }
}

// ------------------------------------------------------------------- text ---

@Composable
private fun TextPanel(viewModel: EditorViewModel) {
    var text by remember { mutableStateOf("") }
    Column {
        PanelTitle(stringResource(R.string.tool_text))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.text_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            ToolChip(
                label = stringResource(R.string.text_add),
                selected = false,
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.addText(text.trim())
                        text = ""
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- sticker ---

private data class StickerPreset(
    val id: String,
    val labelRes: Int,
)

private val stickerPresets = listOf(
    StickerPreset("circle", R.string.sticker_circle),
    StickerPreset("square", R.string.sticker_square),
    StickerPreset("star", R.string.sticker_star),
    StickerPreset("heart", R.string.sticker_heart),
    StickerPreset("arrow", R.string.sticker_arrow),
    StickerPreset("sparkle", R.string.sticker_sparkle),
)

@Composable
private fun StickerPanel(viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_sticker))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(stickerPresets.size) { index ->
                val preset = stickerPresets[index]
                val label = stringResource(preset.labelRes)
                ToolChip(
                    label = label,
                    selected = false,
                    onClick = { viewModel.addSticker(preset.id, label) },
                )
            }
        }
    }
}

// ----------------------------------------------------------------- filter ---

private data class FilterPreset(
    val id: String,
    val labelRes: Int,
    val tint: Color,
)

private val filterPresets = listOf(
    FilterPreset("NONE", R.string.filter_none, Color(0xFF44464E)),
    FilterPreset("WARM", R.string.filter_warm, Color(0xFFE08A3C)),
    FilterPreset("COOL", R.string.filter_cool, Color(0xFF3C8AE0)),
    FilterPreset("VIVID", R.string.filter_vivid, Color(0xFFD03CE0)),
    FilterPreset("MONO", R.string.filter_mono, Color(0xFF9E9E9E)),
    FilterPreset("SEPIA", R.string.filter_sepia, Color(0xFF9C8E7A)),
    FilterPreset("VINTAGE", R.string.filter_vintage, Color(0xFF6F7D5B)),
)

@Composable
private fun FilterPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_filter))
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filterPresets.size) { index ->
                val preset = filterPresets[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        if (preset.id == "NONE") {
                            viewModel.clearFilter()
                        } else {
                            viewModel.applyLook(preset.id)
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(preset.tint),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(preset.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- effect ---

private data class EffectPreset(
    val id: String,
    val labelRes: Int,
    val adjustment: (Adjustment) -> Adjustment,
)

private val effectPresets = listOf(
    EffectPreset("none", R.string.effect_none) { it },
    EffectPreset("bright", R.string.effect_bright) { it.copy(brightness = 0.18f, lightSensation = 0.2f) },
    EffectPreset("dark", R.string.effect_dark) { it.copy(brightness = -0.15f) },
    EffectPreset("vivid", R.string.effect_punch) { it.copy(contrast = 0.3f, saturation = 0.35f) },
    EffectPreset("soft", R.string.effect_soft) { it.copy(fade = 0.35f) },
    EffectPreset("vignette", R.string.effect_vignette) { it.copy(vignette = 0.6f) },
    EffectPreset("grain", R.string.effect_grain) { it.copy(grain = 0.5f) },
)

@Composable
private fun EffectPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_effect))
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(effectPresets.size) { index ->
                val preset = effectPresets[index]
                ToolChip(
                    label = stringResource(preset.labelRes),
                    selected = false,
                    onClick = { viewModel.applyAdjustment { preset.adjustment(it) } },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- adjust ---

@Composable
private fun AdjustPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    if (selectedSegment == null) {
        PanelTitle(stringResource(R.string.tool_adjust))
        Hint(stringResource(R.string.hint_select_layer))
        return
    }
    val material = viewModel.project.value.materials.find(selectedSegment.materialId)
    val adjust = material?.adjust ?: Adjustment()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        PanelTitle(stringResource(R.string.tool_adjust))
        LabeledSlider(stringResource(R.string.adjust_brightness), adjust.brightness, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(brightness = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_contrast), adjust.contrast, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(contrast = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_saturation), adjust.saturation, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(saturation = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_temperature), adjust.temperature, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(temperature = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_highlights), adjust.highlight, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(highlight = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_shadows), adjust.shadow, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(shadow = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_fade), adjust.fade, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(fade = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_vignette), adjust.vignette, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(vignette = v) }
        }
        LabeledSlider(stringResource(R.string.adjust_grain), adjust.grain, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(grain = v) }
        }
    }
}

// ------------------------------------------------------------- transition ---

private data class TransitionPreset(
    val nameRes: Int,
    val durationUs: Long,
)

private val transitionPresets = listOf(
    TransitionPreset(R.string.transition_fade, 500_000L),
    TransitionPreset(R.string.transition_dissolve, 800_000L),
    TransitionPreset(R.string.transition_slow, 1_500_000L),
)

@Composable
private fun TransitionPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_transition))
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ToolChip(
                    label = stringResource(R.string.transition_none),
                    selected = false,
                    onClick = { viewModel.clearTransition() },
                )
            }
            items(transitionPresets.size) { index ->
                val preset = transitionPresets[index]
                val label = stringResource(preset.nameRes)
                ToolChip(
                    label = label,
                    selected = false,
                    onClick = { viewModel.applyTransition(label, preset.durationUs) },
                )
            }
        }
        Hint(stringResource(R.string.transition_hint))
    }
}

// ----------------------------------------------------------------- speed ---

@Composable
private fun SpeedPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_speed))
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)) { speed ->
                ToolChip(
                    label = "${speed}x",
                    selected = selectedSegment.speed == speed,
                    onClick = { viewModel.setSpeed(speed) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- canvas ---

private data class CanvasPreset(
    val ratio: String,
    val width: Int,
    val height: Int,
)

private val canvasRatios = listOf(
    CanvasPreset("9:16", 1080, 1920),
    CanvasPreset("16:9", 1920, 1080),
    CanvasPreset("1:1", 1080, 1080),
    CanvasPreset("4:3", 1440, 1080),
    CanvasPreset("3:4", 1080, 1440),
    CanvasPreset("21:9", 2560, 1080),
)

@Composable
private fun CanvasPanel(project: Project, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_canvas))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(canvasRatios.size) { index ->
                val preset = canvasRatios[index]
                ToolChip(
                    label = preset.ratio,
                    selected = project.canvas.ratio == preset.ratio,
                    onClick = { viewModel.setCanvasRatio(preset.ratio, preset.width, preset.height) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ crop ---

@Composable
private fun CropPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    if (selectedSegment == null) {
        PanelTitle(stringResource(R.string.tool_crop))
        Hint(stringResource(R.string.hint_select_layer))
        return
    }
    Column {
        PanelTitle(stringResource(R.string.tool_crop))
        LabeledSlider(
            stringResource(R.string.crop_scale),
            selectedSegment.clip.scale.x,
            0.2f..3f,
        ) { value ->
            viewModel.updateClip { it.copy(scale = com.zaitxcode.opencut.core.model.Scale(value, value)) }
        }
        LabeledSlider(
            stringResource(R.string.crop_rotation),
            selectedSegment.clip.rotation,
            -180f..180f,
        ) { value ->
            viewModel.updateClip { it.copy(rotation = value) }
        }
        LabeledSlider(
            stringResource(R.string.crop_opacity),
            selectedSegment.clip.alpha,
            0f..1f,
        ) { value ->
            viewModel.updateClip { it.copy(alpha = value) }
        }
    }
}

// -------------------------------------------------------------- keyframe ---

@Composable
private fun KeyframePanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle(stringResource(R.string.tool_keyframe))
        if (selectedSegment == null) {
            Hint(stringResource(R.string.hint_select_layer))
            return@Column
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(label = stringResource(R.string.keyframe_position), selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = stringResource(R.string.keyframe_scale), selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = stringResource(R.string.keyframe_opacity), selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = stringResource(R.string.keyframe_adjust), selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.ADJUST)
            })
        }
        Spacer(Modifier.height(6.dp))
        Hint(stringResource(R.string.keyframe_hint))
    }
}

// ---------------------------------------------------------------- helpers ---

/** The permissions that are needed to read the media library of the device. */
private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** True when the app may read the media library. */
private fun hasMediaPermission(context: android.content.Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}
