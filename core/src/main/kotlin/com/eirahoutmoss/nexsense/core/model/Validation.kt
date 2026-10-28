package com.eirahoutmoss.nexsense.core.model

import java.time.Instant
import java.time.format.DateTimeParseException

/** Doğrulama sorunu. [code] arayüzde yerelleştirilir; [detail] teknik ayrıntıdır. */
data class ValidationIssue(val code: Code, val detail: String) {
    enum class Code {
        MISSING_UNIT, WRONG_UNIT, NO_VALUES, UNKNOWN_COMPONENT, NOT_FINITE, BAD_TIMESTAMP,
        PARTIAL_POSITION, NOT_FINITE_POSITION, BLANK_NAME, BAD_CONFIDENCE, BAD_REFERENCE,
        BAD_FORMAT, NEWER_SCHEMA, BAD_SCAN, BAD_THRESHOLD,
    }

    override fun toString(): String = "$code: $detail"
}

class ValidationException(val issues: List<ValidationIssue>) :
    IllegalArgumentException(issues.joinToString("; "))

object Validation {

    fun isIsoTimestamp(text: String): Boolean = try {
        Instant.parse(text); true
    } catch (_: DateTimeParseException) {
        false
    }

    fun sample(s: SensorSample): List<ValidationIssue> = buildList {
        if (s.unit.isBlank()) add(ValidationIssue(ValidationIssue.Code.MISSING_UNIT, s.sensorType.key))
        else if (s.unit != s.sensorType.unit) {
            add(ValidationIssue(ValidationIssue.Code.WRONG_UNIT, "${s.sensorType.key}: '${s.unit}' != '${s.sensorType.unit}'"))
        }
        if (s.values.isEmpty()) add(ValidationIssue(ValidationIssue.Code.NO_VALUES, s.sensorType.key))
        for ((k, v) in s.values) {
            if (k !in s.sensorType.components) add(ValidationIssue(ValidationIssue.Code.UNKNOWN_COMPONENT, "${s.sensorType.key}.$k"))
            if (v != null && !v.isFinite()) add(ValidationIssue(ValidationIssue.Code.NOT_FINITE, "${s.sensorType.key}.$k=$v"))
        }
        if (!isIsoTimestamp(s.timestamp)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, s.timestamp))
        s.confidence?.let { if (!it.isFinite() || it < 0.0 || it > 1.0) add(ValidationIssue(ValidationIssue.Code.BAD_CONFIDENCE, "$it")) }
    }

    /** Koordinat ya tamamen bilinir ya tamamen null'dır. Yarım koordinat sahte yerleşime yol açar. */
    fun position(x: Double?, y: Double?, z: Double?): List<ValidationIssue> = buildList {
        val given = listOf(x, y, z).count { it != null }
        if (given in 1..2) add(ValidationIssue(ValidationIssue.Code.PARTIAL_POSITION, "x=$x y=$y z=$z"))
        if (listOf(x, y, z).any { it != null && !it.isFinite() }) {
            add(ValidationIssue(ValidationIssue.Code.NOT_FINITE_POSITION, "x=$x y=$y z=$z"))
        }
    }

    fun point(p: MeasurementPoint): List<ValidationIssue> = buildList {
        addAll(position(p.x, p.y, p.z))
        if (!isIsoTimestamp(p.createdAt)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, p.createdAt))
        p.positionConfidence?.let { if (!it.isFinite() || it < 0.0 || it > 1.0) add(ValidationIssue(ValidationIssue.Code.BAD_CONFIDENCE, "$it")) }
    }

    /** Tüm paketin bütünlüğü: biçim, şema, başvurular, örnekler. */
    fun bundle(b: ProjectBundle): List<ValidationIssue> = buildList {
        if (b.format != BUNDLE_FORMAT) add(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, b.format))
        if (b.schemaVersion > SCHEMA_VERSION) add(ValidationIssue(ValidationIssue.Code.NEWER_SCHEMA, "${b.schemaVersion} > $SCHEMA_VERSION"))
        if (b.project.name.isBlank()) add(ValidationIssue(ValidationIssue.Code.BLANK_NAME, b.project.id))
        val sessionIds = b.sessions.map { it.id }.toSet()
        val pointIds = b.points.map { it.id }.toSet()
        for (s in b.sessions) {
            if (s.projectId != b.project.id) add(ValidationIssue(ValidationIssue.Code.BAD_REFERENCE, "session ${s.id}"))
            if (!isIsoTimestamp(s.startedAt)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, s.startedAt))
            s.endedAt?.let { if (!isIsoTimestamp(it)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, it)) }
        }
        for (p in b.points) {
            if (p.projectId != b.project.id || p.sessionId !in sessionIds) {
                add(ValidationIssue(ValidationIssue.Code.BAD_REFERENCE, "point ${p.id}"))
            }
            addAll(point(p))
        }
        for (s in b.samples) {
            if (s.sessionId !in sessionIds || (s.pointId != null && s.pointId !in pointIds)) {
                add(ValidationIssue(ValidationIssue.Code.BAD_REFERENCE, "sample ${s.id}"))
            }
            addAll(sample(s))
        }
        for (sc in b.scans) {
            if (sc.projectId != b.project.id || (sc.sessionId != null && sc.sessionId !in sessionIds)) {
                add(ValidationIssue(ValidationIssue.Code.BAD_REFERENCE, "scan ${sc.id}"))
            }
            addAll(scan(sc))
        }
        addAll(analysis(b.analysis))
    }

    fun scan(sc: Scan): List<ValidationIssue> = buildList {
        if (!isIsoTimestamp(sc.startedAt)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, sc.startedAt))
        sc.endedAt?.let { if (!isIsoTimestamp(it)) add(ValidationIssue(ValidationIssue.Code.BAD_TIMESTAMP, it)) }
        val badPoint = sc.points.firstOrNull { p ->
            !p.x.isFinite() || !p.y.isFinite() || !p.z.isFinite() || !p.c.isFinite() || p.c < 0.0 || p.c > 1.0
        }
        if (badPoint != null) add(ValidationIssue(ValidationIssue.Code.BAD_SCAN, "${sc.id}: point $badPoint"))
        for (pl in sc.planes) {
            if (pl.polygon.size < 3 || pl.polygon.any { v -> v.size != 3 || v.any { !it.isFinite() } }) {
                add(ValidationIssue(ValidationIssue.Code.BAD_SCAN, "${sc.id}: plane"))
            }
        }
        if (sc.rays.any { r -> listOf(r.ox, r.oy, r.oz, r.x, r.y, r.z, r.c).any { !it.isFinite() } || r.c < 0.0 || r.c > 1.0 }) {
            add(ValidationIssue(ValidationIssue.Code.BAD_SCAN, "${sc.id}: ray"))
        }
        if (sc.trajectory.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() || !it.t.isFinite() }) {
            add(ValidationIssue(ValidationIssue.Code.BAD_SCAN, "${sc.id}: trajectory"))
        }
    }

    fun analysis(a: AnalysisSettings): List<ValidationIssue> = buildList {
        for (t in a.thresholds) {
            val lo = t.min; val hi = t.max
            if ((lo != null && !lo.isFinite()) || (hi != null && !hi.isFinite()) || (lo != null && hi != null && lo > hi)) {
                add(ValidationIssue(ValidationIssue.Code.BAD_THRESHOLD, "${t.layer}: $lo..$hi"))
            }
        }
        if (!a.gridCellM.isFinite() || a.gridCellM < 0.05 || a.gridCellM > 5.0 ||
            !a.maxEstimateDistanceM.isFinite() || a.maxEstimateDistanceM <= 0.0 || a.maxEstimateDistanceM > 20.0
        ) {
            add(ValidationIssue(ValidationIssue.Code.BAD_THRESHOLD, "grid ${a.gridCellM} / ${a.maxEstimateDistanceM}"))
        }
    }
}
