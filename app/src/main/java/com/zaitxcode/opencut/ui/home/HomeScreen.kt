package com.zaitxcode.opencut.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zaitxcode.opencut.OpenCutApp
import com.zaitxcode.opencut.core.account.UserProfile
import com.zaitxcode.opencut.core.editor.newProject
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.ui.theme.BrandLogo
import com.zaitxcode.opencut.ui.theme.FreeBadge
import kotlinx.coroutines.launch

/**
 * The project list.
 *
 * It is the entry point of the app: it lists the drafts stored on the device and
 * offers to create a new one. The account button opens the profile and plan page;
 * signing in is optional and never gates a feature.
 */
@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenCutApp
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }

    // Refresh whenever the screen comes back to the foreground, so a project
    // created or deleted in the editor shows up as soon as the user returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            projects = app.projectStore.listProjects()
            profile = app.accountStore.current()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(markSize = 34.dp, fontSize = 24, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenAccount) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(profile?.avatarColor ?: 0xFF2A2A30.toInt()),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (profile == null) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Account",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(
                                    text = profile!!.initial,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "An open source video editor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    FreeBadge()
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val project = newProject("Project ${projects.size + 1}")
                            app.projectStore.writeProject(project)
                            onCreateProject(project.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New project")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch { projects = app.projectStore.listProjects() }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No projects yet.\nTap New project to start editing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            onDelete = {
                                scope.launch {
                                    app.projectStore.deleteProject(project.id)
                                    projects = app.projectStore.listProjects()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = project.canvas.ratio,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${project.tracks.sumOf { it.segments.size }} clips · " +
                    "${formatDuration(project.durationUs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onDelete) {
            Text("Delete", color = Color(0xFFFF6B6B))
        }
    }
}

/** Formats a microsecond duration as `m:ss`. */
fun formatDuration(durationUs: Long): String {
    val totalSeconds = durationUs / 1_000_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
