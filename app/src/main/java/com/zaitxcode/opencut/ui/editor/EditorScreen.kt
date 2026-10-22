package com.zaitxcode.opencut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zaitxcode.opencut.core.editor.newProject
import com.zaitxcode.opencut.ui.editor.panel.PanelHost
import com.zaitxcode.opencut.ui.editor.panel.Tool
import androidx.compose.ui.res.stringResource
import com.zaitxcode.opencut.R

/**
 * The main editing screen.
 *
 * The layout mirrors a classic mobile editor: a compact top bar, the preview canvas,
 * a horizontally scrollable tool dock, the active tool panel and the timeline.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    viewModel: EditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    var activeTool by remember { mutableStateOf<Tool?>(null) }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val playheadUs by viewModel.playheadUs.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val selectedSegmentId by viewModel.selectedSegmentId.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) {
        if (!viewModel.isOpen) {
            val existing = (context.applicationContext as com.zaitxcode.opencut.OpenCutApp)
                .projectStore
                .readProject(projectId)
            viewModel.open(existing ?: newProject("Untitled"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EditorTopBar(
            title = project.name,
            canUndo = canUndo,
            canRedo = canRedo,
            onBack = {
                viewModel.save()
                onBack()
            },
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onExport = {
                viewModel.save()
                onExport()
            },
        )

        PreviewPane(
            project = project,
            playheadUs = playheadUs,
            playing = playing,
            onTogglePlay = { viewModel.setPlaying(!playing) },
            onSeek = viewModel::seek,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        DockBar(
            activeTool = activeTool,
            onSelect = { tool -> activeTool = if (activeTool == tool) null else tool },
        )

        activeTool?.let { tool ->
            PanelHost(
                tool = tool,
                project = project,
                selectedSegment = viewModel.selectedSegment,
                viewModel = viewModel,
            )
        }

        TimelineView(
            project = project,
            playheadUs = playheadUs,
            selectedSegmentId = selectedSegmentId,
            onSelect = { segmentId -> viewModel.select(segmentId) },
            onSeek = viewModel::seek,
            onSplit = viewModel::split,
            onDelete = viewModel::delete,
            onDuplicate = viewModel::duplicate,
            onMoveSegment = viewModel::moveSegment,
            onTrimSegment = viewModel::trimSegment,
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.editor_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.editor_undo),
                tint = if (canUndo) MaterialTheme.colorScheme.onBackground else Color.DarkGray,
            )
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.editor_redo),
                tint = if (canRedo) MaterialTheme.colorScheme.onBackground else Color.DarkGray,
            )
        }
        Text(
            text = stringResource(R.string.editor_export),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onExport)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Small helper so panels can render an inline separator. */
@Composable
internal fun VSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
