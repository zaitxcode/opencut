package com.zaitxcode.opencut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zaitxcode.opencut.ui.editor.panel.Tool

/** The tools offered in the bottom dock of the editor. */
private data class DockEntry(
    val tool: Tool,
    val label: String,
    val icon: ImageVector,
)

private val dockEntries = listOf(
    DockEntry(Tool.MEDIA, "Media", Icons.Default.OndemandVideo),
    DockEntry(Tool.AUDIO, "Audio", Icons.Default.MusicNote),
    DockEntry(Tool.TEXT, "Text", Icons.Default.TextFields),
    DockEntry(Tool.STICKER, "Sticker", Icons.Default.GraphicEq),
    DockEntry(Tool.FILTER, "Filter", Icons.Default.FilterAlt),
    DockEntry(Tool.EFFECT, "Effect", Icons.Default.Wallpaper),
    DockEntry(Tool.ADJUST, "Adjust", Icons.Default.Adjust),
    DockEntry(Tool.TRANSITION, "Transition", Icons.Default.Autorenew),
    DockEntry(Tool.SPEED, "Speed", Icons.Default.Videocam),
    DockEntry(Tool.CANVAS, "Canvas", Icons.Default.Tune),
    DockEntry(Tool.CROP, "Crop", Icons.Default.Crop),
    DockEntry(Tool.KEYFRAME, "Keyframe", Icons.Default.KeyboardArrowUp),
)

/**
 * The horizontally scrollable tool bar.
 *
 * Every entry opens the matching tool panel; tapping the active entry closes it.
 */
@Composable
fun DockBar(
    activeTool: Tool?,
    onSelect: (Tool) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dockEntries.forEach { entry ->
                val selected = activeTool == entry.tool
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onSelect(entry.tool) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** A rounded chip used by the tool panels. */
@Composable
internal fun ToolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
