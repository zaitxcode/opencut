package com.zaitxcode.opencut.ui.editor.panel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.KeyframeKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Segment
import com.zaitxcode.opencut.ui.editor.EditorViewModel
import com.zaitxcode.opencut.ui.editor.ToolChip

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
            .height(190.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp),
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
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

// ------------------------------------------------------------------ media ---

@Composable
private fun MediaPanel(viewModel: EditorViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> viewModel.importMedia(uris) }

    Column {
        PanelTitle("Media")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(
                label = "Add from device",
                selected = false,
                onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
            )
            ToolChip(label = "Add photo", selected = false, onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    }
}

// ------------------------------------------------------------------ audio ---

@Composable
private fun AudioPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Audio")
        if (selectedSegment == null) {
            Hint("Select a layer to change its audio")
            return@Column
        }
        LabeledSlider(
            label = "Volume",
            value = selectedSegment.volume,
            range = 0f..2f,
        ) { viewModel.setVolume(it) }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(
                label = if (selectedSegment.muted) "Unmute" else "Mute",
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
        PanelTitle("Text")
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
                placeholder = { Text("Type something") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            ToolChip(
                label = "Add",
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

private val stickerPresets = listOf(
    "circle" to "Circle",
    "square" to "Square",
    "star" to "Star",
    "heart" to "Heart",
    "arrow" to "Arrow",
    "sparkle" to "Sparkle",
)

@Composable
private fun StickerPanel(viewModel: EditorViewModel) {
    Column {
        PanelTitle("Sticker")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(stickerPresets.size) { index ->
                val (id, name) = stickerPresets[index]
                ToolChip(
                    label = name,
                    selected = false,
                    onClick = { viewModel.addSticker(id, name) },
                )
            }
        }
    }
}

// ----------------------------------------------------------------- filter ---

private data class FilterPreset(
    val id: String,
    val name: String,
    val tint: Color,
)

private val filterPresets = listOf(
    FilterPreset("NONE", "None", Color(0xFF44464E)),
    FilterPreset("WARM", "Warm", Color(0xFFE08A3C)),
    FilterPreset("COOL", "Cool", Color(0xFF3C8AE0)),
    FilterPreset("VIVID", "Vivid", Color(0xFFD03CE0)),
    FilterPreset("MONO", "Mono", Color(0xFF9E9E9E)),
    FilterPreset("SEPIA", "Sepia", Color(0xFF9C8E7A)),
    FilterPreset("VINTAGE", "Vintage", Color(0xFF6F7D5B)),
)

@Composable
private fun FilterPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Filters")
        if (selectedSegment == null) {
            Hint("Select a layer to apply a filter")
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(preset.tint),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preset.name,
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
    val name: String,
    val adjustment: (Adjustment) -> Adjustment,
)

private val effectPresets = listOf(
    EffectPreset("none", "None") { it },
    EffectPreset("bright", "Brighten") { it.copy(brightness = 0.18f, lightSensation = 0.2f) },
    EffectPreset("dark", "Darken") { it.copy(brightness = -0.15f) },
    EffectPreset("vivid", "Punch") { it.copy(contrast = 0.3f, saturation = 0.35f) },
    EffectPreset("soft", "Soft") { it.copy(fade = 0.35f) },
    EffectPreset("vignette", "Vignette") { it.copy(vignette = 0.6f) },
    EffectPreset("grain", "Grain") { it.copy(grain = 0.5f) },
)

@Composable
private fun EffectPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Effects")
        if (selectedSegment == null) {
            Hint("Select a layer to apply an effect")
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(effectPresets.size) { index ->
                val preset = effectPresets[index]
                ToolChip(
                    label = preset.name,
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
        PanelTitle("Adjust")
        Hint("Select a layer to adjust it")
        return
    }
    val material = viewModel.project.value.materials.find(selectedSegment.materialId)
    val adjust = material?.adjust ?: Adjustment()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        PanelTitle("Adjust")
        LabeledSlider("Brightness", adjust.brightness, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(brightness = v) }
        }
        LabeledSlider("Contrast", adjust.contrast, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(contrast = v) }
        }
        LabeledSlider("Saturation", adjust.saturation, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(saturation = v) }
        }
        LabeledSlider("Temperature", adjust.temperature, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(temperature = v) }
        }
        LabeledSlider("Highlights", adjust.highlight, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(highlight = v) }
        }
        LabeledSlider("Shadows", adjust.shadow, -1f..1f) { v ->
            viewModel.applyAdjustment { it.copy(shadow = v) }
        }
        LabeledSlider("Fade", adjust.fade, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(fade = v) }
        }
        LabeledSlider("Vignette", adjust.vignette, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(vignette = v) }
        }
        LabeledSlider("Grain", adjust.grain, 0f..1f) { v ->
            viewModel.applyAdjustment { it.copy(grain = v) }
        }
    }
}

// ------------------------------------------------------------- transition ---

private val transitionPresets = listOf(
    "Fade" to 500_000L,
    "Cross dissolve" to 800_000L,
    "Slow dissolve" to 1_500_000L,
)

@Composable
private fun TransitionPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Transition")
        if (selectedSegment == null) {
            Hint("Select a clip to add a transition")
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ToolChip(
                    label = "None",
                    selected = false,
                    onClick = { viewModel.clearTransition() },
                )
            }
            items(transitionPresets.size) { index ->
                val (name, duration) = transitionPresets[index]
                ToolChip(
                    label = name,
                    selected = false,
                    onClick = { viewModel.applyTransition(name, duration) },
                )
            }
        }
        Hint("The transition cross fades the previous clip into the selected one")
    }
}

// ----------------------------------------------------------------- speed ---

@Composable
private fun SpeedPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Speed")
        if (selectedSegment == null) {
            Hint("Select a clip to change its speed")
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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

private val canvasRatios = listOf(
    Triple("9:16", 1080, 1920),
    Triple("16:9", 1920, 1080),
    Triple("1:1", 1080, 1080),
    Triple("4:3", 1440, 1080),
    Triple("3:4", 1080, 1440),
    Triple("21:9", 2560, 1080),
)

@Composable
private fun CanvasPanel(project: Project, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Canvas")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(canvasRatios.size) { index ->
                val (ratio, width, height) = canvasRatios[index]
                ToolChip(
                    label = ratio,
                    selected = project.canvas.ratio == ratio,
                    onClick = { viewModel.setCanvasRatio(ratio, width, height) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ crop ---

@Composable
private fun CropPanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    if (selectedSegment == null) {
        PanelTitle("Crop")
        Hint("Select a clip to crop it")
        return
    }
    Column {
        PanelTitle("Crop")
        LabeledSlider("Scale", selectedSegment.clip.scale.x, 0.2f..3f) { value ->
            viewModel.updateClip { it.copy(scale = com.zaitxcode.opencut.core.model.Scale(value, value)) }
        }
        LabeledSlider("Rotation", selectedSegment.clip.rotation, -180f..180f) { value ->
            viewModel.updateClip { it.copy(rotation = value) }
        }
        LabeledSlider("Opacity", selectedSegment.clip.alpha, 0f..1f) { value ->
            viewModel.updateClip { it.copy(alpha = value) }
        }
    }
}

// -------------------------------------------------------------- keyframe ---

@Composable
private fun KeyframePanel(selectedSegment: Segment?, viewModel: EditorViewModel) {
    Column {
        PanelTitle("Keyframes")
        if (selectedSegment == null) {
            Hint("Select a layer to animate it")
            return@Column
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolChip(label = "Position", selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = "Scale", selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = "Opacity", selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.VIDEO)
            })
            ToolChip(label = "Adjust", selected = false, onClick = {
                viewModel.addKeyframe(KeyframeKind.ADJUST)
            })
        }
    }
}

// ---------------------------------------------------------------- helpers ---

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            modifier = Modifier.width(88.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}
