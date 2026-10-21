package com.zaitxcode.opencut.ui.export

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zaitxcode.opencut.OpenCutApp
import com.zaitxcode.opencut.core.export.ExportEngine
import com.zaitxcode.opencut.core.export.ExportProgress
import com.zaitxcode.opencut.core.export.ExportSettings
import com.zaitxcode.opencut.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Resolution(
    val label: String,
    val width: Int,
    val height: Int,
    val bitRate: Int,
)

private val resolutions = listOf(
    Resolution("720p", 720, 1280, 5_000_000),
    Resolution("1080p", 1080, 1920, 8_000_000),
    Resolution("1440p", 1440, 2560, 12_000_000),
    Resolution("4K", 2160, 3840, 24_000_000),
)

/**
 * The export screen.
 *
 * The user picks a resolution and a frame rate, then the project is rendered into an
 * MP4 file with the same pipeline that powers the preview.
 */
@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenCutApp
    val scope = rememberCoroutineScopeCompat()

    var project by remember { mutableStateOf<Project?>(null) }
    var resolution by remember { mutableStateOf(resolutions[1]) }
    var frameRate by remember { mutableStateOf(30) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        project = app.projectStore.readProject(projectId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text(
                text = "Resolution",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                resolutions.forEach { option ->
                    OutlinedButton(
                        onClick = { resolution = option },
                        colors = if (resolution == option) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(option.label)
                    }
                }
            }

            Text(
                text = "Frame rate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(24, 30, 60).forEach { option ->
                    OutlinedButton(
                        onClick = { frameRate = option },
                        colors = if (frameRate == option) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text("$option fps")
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text(
                        text = " $stage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            result?.let { file ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Saved to ${file.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${app.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Open")
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Export failed: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val current = project ?: return@Button
                    running = true
                    progress = 0
                    error = null
                    result = null
                    scope.launch {
                        val output = File(app.projectStore.exportDir(context), "opencut_export.mp4")
                        val settings = ExportSettings(
                            width = resolution.width,
                            height = resolution.height,
                            frameRate = frameRate,
                            bitRate = resolution.bitRate,
                        )
                        val outcome = withContext(Dispatchers.Default) {
                            ExportEngine(context).export(current, output, settings) { report: ExportProgress ->
                                progress = report.percent
                                stage = report.stage
                            }
                        }
                        running = false
                        outcome
                            .onSuccess { result = it }
                            .onFailure { error = it.message ?: it::class.java.simpleName }
                    }
                },
                enabled = project != null && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export")
            }
        }
    }
}

/** Small helper so the screen does not need the full compose runtime import set. */
@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
