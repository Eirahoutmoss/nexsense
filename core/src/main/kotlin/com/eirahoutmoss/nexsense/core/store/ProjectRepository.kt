package com.eirahoutmoss.nexsense.core.store

import com.eirahoutmoss.nexsense.core.export.JsonCodec
import com.eirahoutmoss.nexsense.core.model.AnalysisSettings
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.MeasurementPoint
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.Project
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorSample
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.Session
import com.eirahoutmoss.nexsense.core.model.Validation
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/** Kaydedilmeden önceki ölçüm; kimlikleri depo atar. */
data class SampleDraft(
    val sensorType: SensorType,
    val values: Map<String, Double?>,
    val timestamp: String,
    val source: SampleSource,
    val unit: String = sensorType.unit,
    val quality: String? = null,
    val confidence: Double? = null,
    val deviceOrientation: DeviceOrientation? = null,
    val calibrationInfo: String? = null,
)

/** Proje listesi için hafif özet. */
data class ProjectSummary(
    val project: Project,
    val pointCount: Int,
    val sessionCount: Int,
    val hasDemoData: Boolean,
)

/**
 * Projeler ve ölçümler. Tüm işlemler eşzamanlıdır (uygulama bunları IO iş parçacığında çağırır)
 * ve her değişiklik hemen diske yazılır; uygulama kapanıp açılınca veri korunur.
 */
class ProjectRepository(
    private val storage: ProjectStorage,
    private val clock: () -> Instant = { Instant.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val cache = mutableMapOf<String, ProjectBundle>()

    /** Okunamayan (bozuk) proje dosyaları; silinmez, kullanıcıya bildirilir. */
    private val _broken = MutableStateFlow<List<String>>(emptyList())
    val brokenFiles: StateFlow<List<String>> = _broken.asStateFlow()

    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val summaries: StateFlow<List<ProjectSummary>> = _summaries.asStateFlow()

    /** Her değişiklikte artar; açık ekranlar projeyi yeniden okur. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun now(): String = clock().toString()

    /** Diskteki tüm projeleri okur. Uygulama açılışında bir kez çağrılır. */
    fun loadAll() = synchronized(lock) {
        cache.clear()
        val broken = mutableListOf<String>()
        for (id in storage.listIds()) {
            val text = storage.read(id) ?: continue
            try {
                cache[id] = JsonCodec.decode(text)
            } catch (_: ValidationException) {
                broken += id
            }
        }
        _broken.value = broken
        publish()
    }

    fun listProjects(): List<Project> = synchronized(lock) {
        cache.values.map { it.project }.sortedByDescending { it.updatedAt }
    }

    fun get(projectId: String): ProjectBundle? = synchronized(lock) { cache[projectId] }

    fun createProject(name: String, description: String = ""): Project = synchronized(lock) {
        val clean = name.trim()
        if (clean.isEmpty()) throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BLANK_NAME, "")))
        val ts = now()
        val p = Project(id = newId(), name = clean, description = description.trim(), createdAt = ts, updatedAt = ts)
        save(ProjectBundle(project = p))
        p
    }

    fun renameProject(projectId: String, name: String, description: String? = null): Project = synchronized(lock) {
        val clean = name.trim()
        if (clean.isEmpty()) throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BLANK_NAME, projectId)))
        val b = require(projectId)
        val p = b.project.copy(name = clean, description = description?.trim() ?: b.project.description)
        save(b.copy(project = p))
        get(projectId)!!.project
    }

    fun setReferenceOrigin(projectId: String, origin: String) = synchronized(lock) {
        val b = require(projectId)
        save(b.copy(project = b.project.copy(coordinateSystem = b.project.coordinateSystem.copy(origin = origin.trim()))))
    }

    fun deleteProject(projectId: String) = synchronized(lock) {
        storage.delete(projectId)
        cache.remove(projectId)
        publish()
    }

    /** Yeni oturum başlatır; açık oturum varsa önce kapatılır. */
    fun startSession(projectId: String, notes: String = ""): Session = synchronized(lock) {
        val b = closeOpen(require(projectId))
        val s = Session(id = newId(), projectId = projectId, startedAt = now(), notes = notes.trim())
        save(b.copy(sessions = b.sessions + s))
        s
    }

    fun endSession(projectId: String) = synchronized(lock) {
        save(closeOpen(require(projectId)))
    }

    private fun closeOpen(b: ProjectBundle): ProjectBundle {
        if (b.sessions.none { it.isOpen }) return b
        val ts = now()
        return b.copy(sessions = b.sessions.map { if (it.isOpen) it.copy(endedAt = ts) else it })
    }

    /**
     * Ölçüm noktası ekler ve o anki sensör değerlerini bağlar. Açık oturum yoksa yenisi başlatılır.
     * Koordinat ya tamamen verilir ya hiç verilmez (null); yarım koordinat reddedilir.
     */
    fun addPoint(
        projectId: String,
        label: String,
        notes: String,
        x: Double?,
        y: Double?,
        z: Double?,
        samples: List<SampleDraft>,
        positionSource: PositionSource? = null,
        positionConfidence: Double? = null,
    ): MeasurementPoint = synchronized(lock) {
        val issues = Validation.position(x, y, z)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        var b = require(projectId)
        val session = b.openSession ?: Session(id = newId(), projectId = projectId, startedAt = now()).also {
            b = b.copy(sessions = b.sessions + it)
        }
        val placed = x != null
        val point = MeasurementPoint(
            id = newId(),
            projectId = projectId,
            sessionId = session.id,
            label = label.trim().ifEmpty { "#${b.points.size + 1}" },
            notes = notes.trim(),
            x = x, y = y, z = z,
            createdAt = now(),
            positionSource = positionSource ?: if (placed) PositionSource.MANUAL else PositionSource.NONE,
            positionConfidence = positionConfidence,
        )
        Validation.point(point).let { if (it.isNotEmpty()) throw ValidationException(it) }
        val made = samples.map { it.toSample(point.id, session.id) }
        val bad = made.flatMap { Validation.sample(it) }
        if (bad.isNotEmpty()) throw ValidationException(bad)
        save(b.copy(points = b.points + point, samples = b.samples + made))
        point
    }

    fun updatePoint(
        projectId: String,
        pointId: String,
        label: String,
        notes: String,
        x: Double?,
        y: Double?,
        z: Double?,
    ): MeasurementPoint = synchronized(lock) {
        val issues = Validation.position(x, y, z)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        val b = require(projectId)
        val old = b.points.firstOrNull { it.id == pointId } ?: throw NoSuchElementException(pointId)
        val moved = old.x != x || old.y != y || old.z != z
        val updated = old.copy(
            label = label.trim().ifEmpty { old.label },
            notes = notes.trim(),
            x = x, y = y, z = z,
            positionSource = when {
                x == null -> PositionSource.NONE
                moved || old.positionSource == PositionSource.NONE -> PositionSource.MANUAL
                else -> old.positionSource
            },
        )
        save(b.copy(points = b.points.map { if (it.id == pointId) updated else it }))
        updated
    }

    /** Var olan noktaya yeni ölçümler ekler (ör. sabit noktada akustik ölçüm). */
    fun addSamples(projectId: String, pointId: String, samples: List<SampleDraft>) = synchronized(lock) {
        var b = require(projectId)
        if (b.points.none { it.id == pointId }) throw NoSuchElementException(pointId)
        val session = b.openSession ?: Session(id = newId(), projectId = projectId, startedAt = now()).also {
            b = b.copy(sessions = b.sessions + it)
        }
        val made = samples.map { it.toSample(pointId, session.id) }
        val bad = made.flatMap { Validation.sample(it) }
        if (bad.isNotEmpty()) throw ValidationException(bad)
        save(b.copy(samples = b.samples + made))
    }

    /** Taramayı kaydeder (kimlik ve proje atanır). Açık oturum varsa ona bağlanır. */
    fun addScan(projectId: String, draft: Scan): Scan = synchronized(lock) {
        val b = require(projectId)
        val sc = draft.copy(id = newId(), projectId = projectId, sessionId = draft.sessionId ?: b.openSession?.id)
        val issues = Validation.scan(sc)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        save(b.copy(scans = b.scans + sc))
        sc
    }

    fun deleteScan(projectId: String, scanId: String) = synchronized(lock) {
        val b = require(projectId)
        save(b.copy(scans = b.scans.filterNot { it.id == scanId }))
    }

    fun setAnalysis(projectId: String, analysis: AnalysisSettings) = synchronized(lock) {
        val issues = Validation.analysis(analysis)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        val b = require(projectId)
        save(b.copy(analysis = analysis))
    }

    fun setNorthAngle(projectId: String, deg: Double?) = synchronized(lock) {
        if (deg != null && !deg.isFinite()) throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, "north=$deg")))
        val b = require(projectId)
        val norm = deg?.let { ((it % 360.0) + 360.0) % 360.0 }
        save(b.copy(project = b.project.copy(coordinateSystem = b.project.coordinateSystem.copy(northAngleDeg = norm))))
    }

    fun deletePoint(projectId: String, pointId: String) = synchronized(lock) {
        val b = require(projectId)
        save(b.copy(points = b.points.filterNot { it.id == pointId }, samples = b.samples.filterNot { it.pointId == pointId }))
    }

    /**
     * JSON projesini içe alır. Aynı kimlikli proje varsa üzerine yazılmaz: tüm kimlikler yenilenir.
     * @throws ValidationException dosya geçersizse.
     */
    fun importJson(text: String): Project = synchronized(lock) {
        val incoming = JsonCodec.decode(text)
        val bundle = if (cache.containsKey(incoming.project.id)) reassignIds(incoming) else incoming
        save(bundle, touch = false)
        bundle.project
    }

    fun exportJson(projectId: String): String = synchronized(lock) { JsonCodec.encode(require(projectId)) }

    /** Hazır bir paketi (ör. demo verisi) kaydeder. */
    fun putBundle(bundle: ProjectBundle): Project = synchronized(lock) {
        val issues = Validation.bundle(bundle)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        save(bundle, touch = false)
        bundle.project
    }

    private fun reassignIds(b: ProjectBundle): ProjectBundle {
        val pid = newId()
        val sessionMap = b.sessions.associate { it.id to newId() }
        val pointMap = b.points.associate { it.id to newId() }
        return b.copy(
            scans = b.scans.map {
                it.copy(
                    id = newId(), projectId = pid,
                    sessionId = it.sessionId?.let { s -> sessionMap.getValue(s) },
                    alignment = it.alignment.copy(startPointId = it.alignment.startPointId?.let { p -> pointMap[p] }),
                )
            },
            project = b.project.copy(id = pid),
            sessions = b.sessions.map { it.copy(id = sessionMap.getValue(it.id), projectId = pid) },
            points = b.points.map { it.copy(id = pointMap.getValue(it.id), projectId = pid, sessionId = sessionMap.getValue(it.sessionId)) },
            samples = b.samples.map {
                it.copy(
                    id = newId(),
                    sessionId = sessionMap.getValue(it.sessionId),
                    pointId = it.pointId?.let { p -> pointMap.getValue(p) },
                )
            },
        )
    }

    private fun SampleDraft.toSample(pointId: String?, sessionId: String) = SensorSample(
        id = newId(),
        pointId = pointId,
        sessionId = sessionId,
        sensorType = sensorType,
        values = values,
        unit = unit,
        timestamp = timestamp,
        source = source,
        quality = quality,
        confidence = confidence,
        deviceOrientation = deviceOrientation,
        calibrationInfo = calibrationInfo,
    )

    private fun require(projectId: String): ProjectBundle =
        cache[projectId] ?: throw NoSuchElementException("Proje bulunamadı: $projectId")

    private fun save(bundle: ProjectBundle, touch: Boolean = true) {
        val b = if (touch) bundle.copy(project = bundle.project.copy(updatedAt = now())) else bundle
        storage.write(b.project.id, JsonCodec.encode(b))
        cache[b.project.id] = b
        publish()
    }

    private fun publish() {
        _summaries.value = cache.values
            .map { ProjectSummary(it.project, it.points.size, it.sessions.size, it.hasDemoData) }
            .sortedByDescending { it.project.updatedAt }
        _revision.value = _revision.value + 1
    }
}
