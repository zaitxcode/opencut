package com.zaitxcode.opencut.core.store

import android.content.Context
import com.zaitxcode.opencut.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk storage for projects.
 *
 * Each project lives in its own folder under `files/projects/<id>/` and is stored as
 * a single JSON document (`project.json`). Keeping one file per project makes the
 * format easy to inspect, diff and export, and avoids a migration chain hidden
 * inside a database.
 */
class ProjectStore(context: Context) {

    private val root: File = File(context.filesDir, "projects").apply { mkdirs() }
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun projectDir(projectId: String): File = File(root, projectId)

    fun projectFile(projectId: String): File = File(projectDir(projectId), "project.json")

    /** Lists the stored projects, newest first. */
    suspend fun listProjects(): List<Project> = withContext(Dispatchers.IO) {
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readProject(it.name) }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }

    suspend fun readProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        val file = projectFile(projectId)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(Project.serializer(), file.readText()) }.getOrNull()
    }

    suspend fun writeProject(project: Project): Boolean = withContext(Dispatchers.IO) {
        val dir = projectDir(project.id).apply { mkdirs() }
        val target = File(dir, "project.json")
        // Write to a temporary file first so an interrupted write cannot destroy a
        // project that the user already has open.
        val tmp = File(dir, "project.json.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(Project.serializer(), project))
            tmp.renameTo(target) || run {
                target.delete()
                tmp.renameTo(target)
            }
        }.isSuccess
    }

    suspend fun deleteProject(projectId: String): Boolean = withContext(Dispatchers.IO) {
        projectDir(projectId).deleteRecursively()
    }

    /** Copies a project's media folder reference into a new project id. */
    suspend fun duplicateProject(source: Project, newId: String, newName: String): Project? =
        withContext(Dispatchers.IO) {
            val copy = source.copy(
                id = newId,
                name = newName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            if (writeProject(copy)) copy else null
        }

    /** Directory where exported media is written. */
    fun exportDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "exports").apply { mkdirs() }
}
