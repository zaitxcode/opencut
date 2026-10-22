package com.zaitxcode.opencut.ui.editor

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaitxcode.opencut.R
import com.zaitxcode.opencut.ui.editor.panel.Tool

/** The tools offered in the bottom dock of the editor. */
private data class DockEntry(
    val tool: Tool,
    val labelRes: Int,
    val icon: ImageVector,
)

/**
 * Every tool of the editor.
 *
 * The list is laid out on two rows of six so that all of them stay visible: a tool
 * that has to be discovered by scrolling is a tool that does not exist for most of
 * the users.
 */
private val dockEntries = listOf(
    DockEntry(Tool.MEDIA, R.string.tool_media, Icons.Default.OndemandVideo),
    DockEntry(Tool.AUDIO, R.string.tool_audio, Icons.Default.MusicNote),
    DockEntry(Tool.TEXT, R.string.tool_text, Icons.Default.TextFields),
    DockEntry(Tool.STICKER, R.string.tool_sticker, Icons.Default.EmojiEmotions),
    DockEntry(Tool.FILTER, R.string.tool_filter, Icons.Default.FilterAlt),
    DockEntry(Tool.EFFECT, R.string.tool_effect, Icons.Default.AutoAwesome),
    DockEntry(Tool.ADJUST, R.string.tool_adjust, Icons.Default.Adjust),
    DockEntry(Tool.TRANSITION, R.string.tool_transition, Icons.Default.Autorenew),
    DockEntry(Tool.SPEED, R.string.tool_speed, Icons.Default.Speed),
    DockEntry(Tool.CANVAS, R.string.tool_canvas, Icons.Default.AspectRatio),
    DockEntry(Tool.CROP, R.string.tool_crop, Icons.Default.Crop),
    DockEntry(Tool.KEYFRAME, R.string.tool_keyframe, Icons.Default.Videocam),
)

private const val TOOLS_PER_ROW = 6

/**
 * The tool bar at the bottom of the editor.
 *
 * Every entry opens the matching tool panel; tapping the active entry closes it.
 */
@Composable
fun DockBar(
    activeTool: Tool?,
    onSelect: (Tool) -> Unit,
) {
    Column {
        dockEntries.chunked(TOOLS_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { entry ->
                    ToolButton(
                        entry = entry,
                        selected = activeTool == entry.tool,
                        onClick = { onSelect(entry.tool) },
                        modifier = Modifier.weight(1f),
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

@Composable
private fun ToolButton(
    entry: DockEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(entry.labelRes)
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = label,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
