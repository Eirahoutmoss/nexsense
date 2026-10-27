package com.eirahoutmoss.nexsense.core.export

import com.eirahoutmoss.nexsense.core.model.ProjectBundle

/**
 * Uzun biçimli CSV: her satır bir ölçüm bileşenidir (ör. manyetik alan x).
 * Eksik değer ve bilinmeyen koordinat boş hücredir (sıfır yazılmaz).
 * Sayılar nokta ondalık ayırıcıyla yazılır (yerel ayardan bağımsız). Kodlama UTF-8.
 */
object CsvExporter {

    val HEADER = listOf(
        "timestamp", "project", "session_id", "point_id", "point_label",
        "sensor_type", "component", "value", "unit",
        "x", "y", "z", "position_source",
        "source", "quality", "confidence", "calibration_info",
    )

    fun export(bundle: ProjectBundle): String {
        val points = bundle.points.associateBy { it.id }
        val sb = StringBuilder()
        // Excel'in UTF-8'i tanıması için BOM; diğer araçlar yok sayar.
        sb.append('﻿')
        sb.append(HEADER.joinToString(",")).append("\r\n")
        val ordered = bundle.samples.sortedWith(compareBy({ it.timestamp }, { it.sensorType.ordinal }))
        for (s in ordered) {
            val p = s.pointId?.let { points[it] }
            for ((component, value) in s.values) {
                val row = listOf(
                    s.timestamp,
                    bundle.project.name,
                    s.sessionId,
                    s.pointId.orEmpty(),
                    p?.label.orEmpty(),
                    s.sensorType.key,
                    component,
                    num(value),
                    s.unit,
                    num(p?.x), num(p?.y), num(p?.z),
                    p?.positionSource?.name?.lowercase().orEmpty(),
                    s.source.name.lowercase(),
                    s.quality.orEmpty(),
                    num(s.confidence),
                    s.calibrationInfo.orEmpty(),
                )
                sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }

    /** Ondalık sayıyı yerel ayardan bağımsız yazar; null boş hücredir. */
    fun num(v: Double?): String = when {
        v == null -> ""
        v == Math.rint(v) && kotlin.math.abs(v) < 1e15 -> v.toLong().toString()
        else -> v.toString()
    }

    /** RFC 4180: virgül, tırnak, satır sonu içeren alan tırnağa alınır; tırnak ikilenir. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
