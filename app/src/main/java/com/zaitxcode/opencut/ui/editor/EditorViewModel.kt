package com.zaitxcode.opencut.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaitxcode.opencut.core.editor.EditorStore
import com.zaitxcode.opencut.core.editor.newProject
import com.zaitxcode.opencut.core.media.MediaProbe
import com.zaitxcode.opencut.core.model.Adjustment
import com.zaitxcode.opencut.core.model.FilterSpec
import com.zaitxcode.opencut.core.model.Canvas
import com.zaitxcode.opencut.core.model.Clip
import com.zaitxcode.opencut.core.model.KeyframeKind
import com.zaitxcode.opencut.core.model.Material
import com.zaitxcode.opencut.core.model.MaterialKind
import com.zaitxcode.opencut.core.model.Project
import com.zaitxcode.opencut.core.model.Segment
import com.zaitxcode.opencut.core.model.TransitionSpec
import com.zaitxcode.opencut.core.store.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Holds the editing session of the open project.
 *
 * The [EditorStore] is the single owner of the project state; this view model mirrors
 * it into observable state and bridges IO (probing and saving) onto the right
 * dispatcher.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val projectStore: ProjectStore =
        (application as com.zaitxcode.opencut.OpenCutApp).projectStore

    private val _project = MutableStateFlow(newProject("Untitled"))
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _playheadUs = MutableStateFlow(0L)
    val playheadUs: StateFlow<Long> = _playheadUs.asStateFlow()

    private val _selectedSegmentId = MutableStateFlow<String?>(null)
    val selectedSegmentId: StateFlow<String?> = _selectedSegmentId.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** Uri of every imported media item, keyed by material id. */
    private val _uris = MutableStateFlow<Map<String, Uri>>(emptyMap())
    val uris: StateFlow<Map<String, Uri>> = _uris.asStateFlow()

    private var store: EditorStore? = null

    val isOpen: Boolean get() = store != null

    val selectedSegment: Segment?
        get() = _selectedSegmentId.value?.let { id -> project.value.segment(id)?.second }

    fun open(project: Project) {
        if (store != null) return
        store = EditorStore(project)
        _project.value = project
    }

    /** Loads a project from disk; falls back to a new project when it is missing. */
    fun load(projectId: String) {
        viewModelScope.launch {
            val loaded = projectStore.readProject(projectId)
            open(loaded ?: newProject("Untitled"))
        }
    }

    /** Applies a mutation and republishes the observable state. */
    private fun mutate(block: EditorStore.() -> Unit) {
        val active = store ?: return
        active.apply(block)
        publish(active)
    }

    private fun publish(active: EditorStore) {
        _project.value = active.project
        _selectedSegmentId.value = active.selection.segmentId
        _playheadUs.value = active.playheadUs
        _canUndo.value = active.canUndo
        _canRedo.value = active.canRedo
    }

    fun select(segmentId: String?, trackId: String? = null) {
        val active = store ?: return
        active.select(segmentId, trackId)
        publish(active)
    }

    fun seek(timeUs: Long) {
        val active = store ?: return
        active.seek(timeUs)
        publish(active)
    }

    fun setPlaying(value: Boolean) {
        _playing.value = value
    }

    fun undo() = mutate { undo() }

    fun redo() = mutate { redo() }

    fun split() = mutate { splitAtPlayhead() }

    fun delete() = mutate { deleteSelected() }

    fun duplicate() = mutate { duplicateSelected() }

    fun moveSegment(segmentId: String, newStartUs: Long) =
        mutate { moveSegment(segmentId, newStartUs) }

    fun trimSegment(segmentId: String, startUs: Long, durationUs: Long) =
        mutate { trimSegment(segmentId, startUs, durationUs) }

    fun setSpeed(speed: Float) = mutate { selectedSegment?.let { setSpeed(it.id, speed) } }

    fun setVolume(volume: Float) = mutate { selectedSegment?.let { setVolume(it.id, volume) } }

    fun setMuted(muted: Boolean) = mutate { selectedSegment?.let { setMuted(it.id, muted) } }

    fun updateClip(transform: (Clip) -> Clip) =
        mutate { selectedSegment?.let { updateClip(it.id, transform) } }

    fun applyAdjustment(block: (Adjustment) -> Adjustment) =
        mutate { selectedSegment?.let { applyAdjustment(it.id, block) } }

    fun addText(content: String) = mutate { addText(content) }

    fun addSticker(resourceId: String, name: String) = mutate { addSticker(resourceId, name) }

    fun addKeyframe(kind: KeyframeKind) = mutate { selectedSegment?.let { addKeyframe(it.id, kind) } }

    /** Applies a built-in colour lookup table to the selected layer. */
    fun applyLook(lookId: String, intensity: Float = 1f) = mutate {
        val segment = selectedSegment ?: return@mutate
        val material = Material(
            id = UUID.randomUUID().toString(),
            kind = MaterialKind.FILTER,
            name = lookId.lowercase().replaceFirstChar(Char::uppercase),
            filter = FilterSpec(id = lookId.uppercase(), name = lookId, intensity = intensity),
        )
        applyFilter(segment.id, material)
    }

    /** Removes the colour filter of the selected layer. */
    fun clearFilter() = mutate {
        selectedSegment?.let { clearFilter(it.id) }
    }

    /** Attaches a cross fade transition to the selected layer. */
    fun applyTransition(name: String, durationUs: Long) = mutate {
        val segment = selectedSegment ?: return@mutate
        val material = Material(
            id = UUID.randomUUID().toString(),
            kind = MaterialKind.TRANSITION,
            name = name,
            transition = TransitionSpec(name = name, durationUs = durationUs),
        )
        applyTransition(segment.id, material, durationUs)
    }

    /** Removes the transition of the selected layer. */
    fun clearTransition() = mutate {
        selectedSegment?.let { clearTransition(it.id) }
    }

    fun setCanvasRatio(ratio: String, width: Int, height: Int) =
        mutate { setCanvas(Canvas(width, height, ratio)) }

    /**
     * Imports the given media uris and appends them to the timeline.
     *
     * Each file is probed in the background so the editor stays responsive while a
     * long video is inspected.
     */
    fun importMedia(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val info = MediaProbe.probe(app, uri) ?: return@mapNotNull null
                    val kind = when {
                        info.hasVideo && !info.hasAudio -> MaterialKind.VIDEO
                        info.hasAudio && !info.hasVideo -> MaterialKind.AUDIO
                        info.hasVideo -> MaterialKind.VIDEO
                        else -> MaterialKind.IMAGE
                    }
                    Material(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        path = uri.toString(),
                        name = uri.lastPathSegment?.substringAfterLast('/') ?: "media",
                        durationUs = info.durationUs,
                        width = info.displayWidth,
                        height = info.displayHeight,
                        frameRate = info.frameRate,
                        hasAudio = info.hasAudio,
                    ) to uri
                }
            }
            if (imported.isEmpty()) return@launch
            _uris.value = _uris.value + imported.associate { it.first.id to it.second }
            mutate {
                imported.forEach { (material, _) -> addMedia(material) }
            }
        }
    }

    /** Persists the current project. */
    fun save() {
        val active = store ?: return
        val snapshot = active.project
        viewModelScope.launch {
            projectStore.writeProject(snapshot.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun listProjects(): List<Project> = projectStore.listProjects()
}
