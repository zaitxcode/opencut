package com.zaitxcode.opencut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.render.PreviewRenderer
import androidx.compose.ui.res.stringResource
import com.zaitxcode.opencut.R

/**
 * The preview surface.
 *
 * A [GLSurfaceView] renders the project with OpenGL. The renderer receives an
 * immutable snapshot of the project on every change, so the UI thread never blocks
 * on the GL thread.
 */
@Composable
fun PreviewPane(
    project: Project,
    playheadUs: Long,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember { PreviewRenderer(context) }
    val textureView = remember { GlTextureView(context, renderer) }

    DisposableEffect(Unit) {
        onDispose {
            textureView.release()
        }
    }

    LaunchedEffect(project, playheadUs) {
        renderer.state = PreviewRenderer.buildState(project, playheadUs, emptyMap())
    }

    Box(
        modifier = modifier
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(40.dp),
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) stringResource(R.string.editor_pause) else stringResource(R.string.editor_play),
                tint = Color.White,
            )
        }
    }
}
