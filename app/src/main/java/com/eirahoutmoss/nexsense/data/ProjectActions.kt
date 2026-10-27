package com.eirahoutmoss.nexsense.data

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.export.CsvExporter
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.signal.SoundLevel
import com.eirahoutmoss.nexsense.core.signal.VibrationWindow
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import com.eirahoutmoss.nexsense.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ekranda kısa bilgi (snackbar) olarak gösterilen ileti. */
data class UiMessage(@StringRes val resId: Int, val arg: String = "")

/**
 * Proje işlemlerinin arayüz tarafı: disk işlerini IO iş parçacığında yapar,
 * hataları yerelleştirilmiş iletilere çevirir. Hiçbir işlem uygulamayı çökertmez.
 */
class ProjectActions(
    private val context: Context,
    val repository: ProjectRepository,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.loadAll() } }
            val active = settings.state.value.activeProjectId
            if (active != null && repository.get(active) == null) settings.setActiveProject(null)
            _loaded.value = true
            if (repository.brokenFiles.value.isNotEmpty()) {
                _messages.tryEmit(UiMessage(R.string.msg_broken_files, repository.brokenFiles.value.size.toString()))
            }
        }
    }

    private fun launchOp(@StringRes okMessage: Int? = null, block: () -> Unit) {
        scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { block() }
                okMessage?.let { UiMessage(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ValidationException) {
                UiMessage(messageFor(e.issues.firstOrNull()?.code), e.issues.firstOrNull()?.detail.orEmpty())
            } catch (e: IOException) {
                UiMessage(R.string.err_io, e.message.orEmpty())
            } catch (e: RuntimeException) {
                UiMessage(R.string.err_generic, e.message ?: e.javaClass.simpleName)
            }
            msg?.let { _messages.tryEmit(it) }
        }
    }

    fun createProject(name: String, description: String) = launchOp(R.string.msg_project_created) {
        val p = repository.createProject(name, description)
        settings.setActiveProject(p.id)
    }

    fun renameProject(id: String, name: String, description: String) = launchOp { repository.renameProject(id, name, description) }

    fun deleteProject(id: String) = launchOp(R.string.msg_project_deleted) {
        repository.deleteProject(id)
        if (settings.state.value.activeProjectId == id) settings.setActiveProject(null)
    }

    fun openProject(id: String) = settings.setActiveProject(id)

    fun setReferenceOrigin(projectId: String, origin: String) = launchOp { repository.setReferenceOrigin(projectId, origin) }

    fun startSession(projectId: String) = launchOp(R.string.msg_session_started) { repository.startSession(projectId) }

    fun endSession(projectId: String) = launchOp(R.string.msg_session_ended) { repository.endSession(projectId) }

    /** O an canlı okunan kanalların değerleriyle ölçüm noktası kaydeder. */
    fun addPoint(
        projectId: String,
        label: String,
        notes: String,
        x: Double?, y: Double?, z: Double?,
        readings: Map<Channel, Reading>,
        orientation: DeviceOrientation?,
    ) = launchOp(R.string.msg_point_saved) {
        val drafts = readings.map { (ch, r) ->
            val derived = ch == Channel.VIBRATION || ch == Channel.MICROPHONE
            SampleDraft(
                sensorType = ch.sensorType,
                values = r.values,
                timestamp = r.timestampIso,
                source = if (derived) SampleSource.DERIVED else SampleSource.DEVICE,
                quality = r.accuracy,
                deviceOrientation = orientation,
                calibrationInfo = when (ch) {
                    Channel.VIBRATION -> VibrationWindow.METHOD
                    Channel.MICROPHONE -> SoundLevel.METHOD
                    else -> "factory (uncalibrated by NexSense)"
                },
            )
        }
        repository.addPoint(projectId, label, notes, x, y, z, drafts)
    }

    fun updatePoint(projectId: String, pointId: String, label: String, notes: String, x: Double?, y: Double?, z: Double?) =
        launchOp(R.string.msg_point_updated) { repository.updatePoint(projectId, pointId, label, notes, x, y, z) }

    fun deletePoint(projectId: String, pointId: String) = launchOp(R.string.msg_point_deleted) { repository.deletePoint(projectId, pointId) }

    fun addDemoProject() = launchOp(R.string.msg_demo_added) {
        val b = DemoData.bundle(
            name = context.getString(R.string.demo_project_name),
            description = context.getString(R.string.demo_project_description),
        )
        repository.putBundle(b)
        settings.setActiveProject(b.project.id)
    }

    fun exportCsv(projectId: String, uri: Uri) = launchOp(R.string.msg_exported) {
        val b = repository.get(projectId) ?: throw NoSuchElementException(projectId)
        write(uri, CsvExporter.export(b))
    }

    fun exportJson(projectId: String, uri: Uri) = launchOp(R.string.msg_exported) {
        write(uri, repository.exportJson(projectId))
    }

    fun importJson(uri: Uri) = launchOp(R.string.msg_imported) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_IMPORT_BYTES) throw IOException(context.getString(R.string.err_file_too_large))
            String(bytes, Charsets.UTF_8)
        } ?: throw IOException(context.getString(R.string.err_cannot_open))
        val p = repository.importJson(text)
        settings.setActiveProject(p.id)
    }

    private fun write(uri: Uri, text: String) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException(context.getString(R.string.err_cannot_open))
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    @StringRes
    private fun messageFor(code: ValidationIssue.Code?): Int = when (code) {
        ValidationIssue.Code.BLANK_NAME -> R.string.err_blank_name
        ValidationIssue.Code.PARTIAL_POSITION -> R.string.err_partial_position
        ValidationIssue.Code.NOT_FINITE_POSITION -> R.string.err_bad_number
        ValidationIssue.Code.BAD_FORMAT -> R.string.err_bad_format
        ValidationIssue.Code.NEWER_SCHEMA -> R.string.err_newer_schema
        ValidationIssue.Code.MISSING_UNIT, ValidationIssue.Code.WRONG_UNIT -> R.string.err_unit
        else -> R.string.err_invalid_data
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
    }
}
