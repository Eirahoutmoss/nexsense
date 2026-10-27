package com.eirahoutmoss.nexsense.data

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.export.ReportExporter
import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.export.CsvExporter
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.model.AnalysisSettings
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.MeasurementPoint
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.Scan
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
        val drafts = draftsFrom(readings, orientation)
        repository.addPoint(projectId, label, notes, x, y, z, drafts)
    }

    /**
     * Noktayı hemen ekler ve döndürür (otomatik ölçüm, akustik ölçüm için). Hata olursa ileti gösterir, null döner.
     * [quiet]: başarıda ileti gösterme (otomatik kayıtta her nokta için ileti olmasın).
     */
    suspend fun addPointNow(
        projectId: String,
        label: String,
        notes: String,
        x: Double?, y: Double?, z: Double?,
        drafts: List<SampleDraft>,
        positionSource: PositionSource?,
        positionConfidence: Double?,
        quiet: Boolean = true,
    ): MeasurementPoint? = io(if (quiet) null else R.string.msg_point_saved) {
        repository.addPoint(projectId, label, notes, x, y, z, drafts, positionSource, positionConfidence)
    }

    /** Canlı okumaları ölçüm taslaklarına çevirir. */
    fun draftsFrom(readings: Map<Channel, Reading>, orientation: DeviceOrientation?): List<SampleDraft> = readings.map { (ch, r) ->
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

    suspend fun addSamplesNow(projectId: String, pointId: String, drafts: List<SampleDraft>): Boolean =
        io(R.string.msg_acoustic_saved) { repository.addSamples(projectId, pointId, drafts) } != null

    suspend fun addScanNow(projectId: String, scan: Scan): Scan? = io(R.string.msg_scan_saved) { repository.addScan(projectId, scan) }

    suspend fun startSessionNow(projectId: String, notes: String = ""): Boolean =
        io(null) { repository.startSession(projectId, notes) } != null

    suspend fun endSessionNow(projectId: String): Boolean = io(null) { repository.endSession(projectId) } != null

    fun deleteScan(projectId: String, scanId: String) = launchOp(R.string.msg_scan_deleted) { repository.deleteScan(projectId, scanId) }

    fun setAnalysis(projectId: String, a: AnalysisSettings) = launchOp(R.string.msg_saved) { repository.setAnalysis(projectId, a) }

    fun setNorthAngle(projectId: String, deg: Double?) = launchOp(R.string.msg_saved) { repository.setNorthAngle(projectId, deg) }

    fun exportPdf(projectId: String, uri: Uri) = launchOp(R.string.msg_exported) {
        val b = repository.get(projectId) ?: throw NoSuchElementException(projectId)
        writeBytes(uri, reports.pdf(b))
    }

    fun exportPly(projectId: String, uri: Uri, colorLayer: LayerKind?) = launchOp(R.string.msg_exported) {
        val b = repository.get(projectId) ?: throw NoSuchElementException(projectId)
        writeBytes(uri, reports.ply(b, colorLayer))
    }

    fun exportCase(projectId: String, uri: Uri) = launchOp(R.string.msg_exported) {
        val b = repository.get(projectId) ?: throw NoSuchElementException(projectId)
        writeBytes(uri, reports.caseFile(b))
    }

    /** Vaka dosyasını önbelleğe yazar ve paylaşım için geçici adres döndürür (yalnızca kullanıcı onayından sonra çağrılır). */
    suspend fun prepareShare(projectId: String): Uri? = io(null) {
        val b = repository.get(projectId) ?: throw NoSuchElementException(projectId)
        reports.shareFile(b)
    }

    private val reports by lazy { ReportExporter(context) }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException(context.getString(R.string.err_cannot_open))
        out.use { it.write(bytes) }
    }

    private suspend fun <T> io(@StringRes okMessage: Int?, block: () -> T): T? {
        val result: Result<T> = try {
            Result.success(withContext(Dispatchers.IO) { block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.exceptionOrNull()?.let { e ->
            _messages.tryEmit(
                when (e) {
                    is ValidationException -> UiMessage(messageFor(e.issues.firstOrNull()?.code), e.issues.firstOrNull()?.detail.orEmpty())
                    is IOException -> UiMessage(R.string.err_io, e.message.orEmpty())
                    else -> UiMessage(R.string.err_generic, e.message ?: e.javaClass.simpleName)
                },
            )
            return null
        }
        okMessage?.let { _messages.tryEmit(UiMessage(it)) }
        return result.getOrNull()
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
