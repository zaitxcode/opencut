package com.zaitxcode.opencut.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaitxcode.opencut.core.editor.EditorStore
import com.zaitxcode.opencut.core.editor.newProject
import com.zaitxcode.opencut.core.media.MediaKind
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /**
     * Returns the editing session, creating it from the current snapshot when the
     * screen has not opened one yet.
     *
     * This matters when the activity is recreated while the system picker is in
     * front: the result callback can arrive before the composition has re-opened
     * the project, and a mutation must never be dropped because of that race.
     */
    private fun session(): EditorStore = store ?: EditorStore(_project.value).also { store = it }

    /** Applies a mutation and republishes the observable state. */
    private fun mutate(block: EditorStore.() -> Unit) {
        val active = session()
        active.apply(block)
        publish(active)
    }

    private fun publish(active: EditorStore) {
        _project.value = active.project
        _selectedSegmentId.value = active.selection.segmentId
        _playheadUs.value = active.playheadUs
        _canUndo.value = active.canUndo
        _canRedo.value = active.canRedo
        scheduleSave(active.project)
    }

    private var saveJob: Job? = null

    /**
     * Writes the project shortly after the last edit.
     *
     * A crash or a low-memory kill must not lose the user's timeline, so every
     * mutation schedules a save; rapid edits collapse into one write.
     */
    private fun scheduleSave(project: Project) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            projectStore.writeProject(project.copy(updatedAt = System.currentTimeMillis()))
        }
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

    private var playJob: Job? = null

    /**
     * Starts or stops playback.
     *
     * The clock advances the playhead in real time while the GL surface keeps
     * rendering, so audio-less clips still preview at their natural speed.
     */
    fun setPlaying(value: Boolean) {
        if (_playing.value == value) return
        _playing.value = value
        playJob?.cancel()
        if (!value) return
        playJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                kotlinx.coroutines.delay(16)
                val now = System.nanoTime()
                val deltaUs = (now - last) / 1_000
                last = now
                val active = session()
                val total = active.project.durationUs
                if (total <= 0L) {
                    _playing.value = false
                    break
                }
                val next = active.playheadUs + deltaUs
                if (next >= total) {
                    active.seek(total)
                    _playheadUs.value = total
                    _playing.value = false
                    break
                }
                active.seek(next)
                _playheadUs.value = next
            }
        }
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
     * The bytes are copied into the app's private storage first: the system photo
     * picker only grants access for the lifetime of the process, so a project that
     * referenced the picker uri directly would stop rendering after a restart.
     */
    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val local = copyIntoStorage(app, uri)
                    val info = MediaProbe.probe(app, local) ?: return@mapNotNull null
                    val kind = when (info.kind) {
                        MediaKind.IMAGE -> MaterialKind.IMAGE
                        MediaKind.AUDIO -> MaterialKind.AUDIO
                        MediaKind.VIDEO -> MaterialKind.VIDEO
                    }
                    Material(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        path = local.toString(),
                        name = fileNameOf(uri),
                        durationUs = info.durationUs,
                        width = info.displayWidth,
                        height = info.displayHeight,
                        frameRate = info.frameRate,
                        hasAudio = info.hasAudio,
                    ) to local
                }
            }
            if (imported.isEmpty()) return@launch
            _uris.value = _uris.value + imported.associate { it.first.id to it.second }
            mutate {
                imported.forEach { (material, _) -> addMedia(material) }
            }
        }
    }

    /**
     * Copies a picked document into `files/media` and returns the local uri.
     *
     * The extension is kept so that the decoders and the bitmap loader recognise
     * the copied file.
     */
    private fun copyIntoStorage(app: Application, uri: Uri): Uri = runCatching {
        val dir = File(app.filesDir, "media").apply { mkdirs() }
        val mime = app.contentResolver.getType(uri).orEmpty()
        val extension = extensionFor(mime) ?: uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..5 }
            ?: "bin"
        val target = File(dir, UUID.randomUUID().toString() + "." + extension)
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return uri
        Uri.fromFile(target)
    }.getOrDefault(uri)

    /** Maps a mime type onto the file extension the decoders expect. */
    private fun extensionFor(mime: String): String? = when {
        mime.startsWith("image/jpeg") -> "jpg"
        mime.startsWith("image/png") -> "png"
        mime.startsWith("image/webp") -> "webp"
        mime.startsWith("image/heic") || mime.startsWith("image/heif") -> "heic"
        mime.startsWith("image/gif") -> "gif"
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/quicktime") -> "mov"
        mime.startsWith("video/webm") -> "webm"
        mime.startsWith("video/3gpp") -> "3gp"
        mime.startsWith("audio/mpeg") -> "mp3"
        mime.startsWith("audio/mp4") || mime.startsWith("audio/aac") -> "m4a"
        mime.startsWith("audio/ogg") -> "ogg"
        mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav") -> "wav"
        mime.startsWith("audio/") -> "m4a"
        else -> null
    }

    /** A readable name for an imported file. */
    private fun fileNameOf(uri: Uri): String {
        val raw = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (raw != null && '.' in raw) return raw.substringBeforeLast('.')
        return raw ?: "media"
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
