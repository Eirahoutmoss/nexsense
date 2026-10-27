package com.eirahoutmoss.nexsense.core.model

import java.time.Instant
import java.time.format.DateTimeParseException

/** Doğrulama sorunu. [code] arayüzde yerelleştirilir; [detail] teknik ayrıntıdır. */
data class ValidationIssue(val code: Code, val detail: String) {
    enum class Code {
        MISSING_UNIT, WRONG_UNIT, NO_VALUES, UNKNOWN_COMPONENT, NOT_FINITE, BAD_TIMESTAMP,
        PARTIAL_POSITION, NOT_FINITE_POSITION, BLANK_NAME, BAD_CONFIDENCE, BAD_REFERENCE,
        BAD_FORMAT, NEWER_SCHEMA,
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
    }
}
