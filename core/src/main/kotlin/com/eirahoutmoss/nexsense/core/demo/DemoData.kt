package com.eirahoutmoss.nexsense.core.demo

import com.eirahoutmoss.nexsense.core.model.CoordinateSystem
import com.eirahoutmoss.nexsense.core.model.MeasurementPoint
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.Project
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorSample
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.Session
import java.time.Instant
import java.util.UUID
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Örnek proje. TÜM değerler uydurmadır: kaynak "demo", konum kaynağı "demo".
 * Arayüz bu projeyi ve katmanlarını her zaman "DEMO VERİSİ" etiketiyle gösterir.
 */
object DemoData {

    fun bundle(
        name: String,
        description: String,
        now: Instant = Instant.now(),
        newId: () -> String = { UUID.randomUUID().toString() },
        seed: Int = 42,
    ): ProjectBundle {
        val rnd = Random(seed)
        val pid = newId()
        val sid = newId()
        val start = now.minusSeconds(600)
        val project = Project(
            id = pid, name = name, description = description,
            createdAt = start.toString(), updatedAt = now.toString(),
            coordinateSystem = CoordinateSystem(origin = "DEMO"),
        )
        val session = Session(id = sid, projectId = pid, startedAt = start.toString(), endedAt = now.toString(), notes = "DEMO")
        val points = mutableListOf<MeasurementPoint>()
        val samples = mutableListOf<SensorSample>()
        // 5 m x 4 m oda, 3 x 3 ızgara, iki yükseklik. (4.5, 0.5) köşesinde "sigorta kutusu" benzeri manyetik iz.
        var i = 0
        for (z in listOf(0.3, 1.2)) for (yy in listOf(0.5, 2.0, 3.5)) for (xx in listOf(0.5, 2.5, 4.5)) {
            i++
            val ts = start.plusSeconds(i * 30L).toString()
            val p = MeasurementPoint(
                id = newId(), projectId = pid, sessionId = sid, label = "D$i",
                notes = if (xx == 4.5 && yy == 0.5) "DEMO: elektrik panosu yakını" else "",
                x = xx, y = yy, z = z, createdAt = ts,
                positionSource = PositionSource.DEMO,
            )
            points += p
            val dPanel = sqrt((xx - 4.5) * (xx - 4.5) + (yy - 0.5) * (yy - 0.5) + (z - 1.2) * (z - 1.2))
            val mag = 46.0 + 30.0 / (1.0 + dPanel * dPanel) + rnd.nextDouble(-1.5, 1.5)
            val mx = mag * 0.4; val my = mag * 0.3
            val mz = sqrt((mag * mag - mx * mx - my * my).coerceAtLeast(0.0))
            samples += s(newId(), p.id, sid, SensorType.MAGNETIC_FIELD, linkedMapOf("x" to r(mx), "y" to r(my), "z" to r(mz), "magnitude" to r(mag)), ts)
            val vib = 0.02 + (if (yy == 3.5) 0.08 else 0.0) + rnd.nextDouble(0.0, 0.01)
            samples += s(newId(), p.id, sid, SensorType.VIBRATION_RMS, linkedMapOf("rms" to r(vib, 4)), ts)
            val snd = -52.0 + (if (xx == 0.5) 8.0 else 0.0) + rnd.nextDouble(-2.0, 2.0)
            samples += s(newId(), p.id, sid, SensorType.SOUND_LEVEL, linkedMapOf("rms" to r(snd), "peak" to r(snd + 9)), ts)
            val t = 21.0 + z * 1.4 + (if (xx == 4.5) 0.8 else 0.0) + rnd.nextDouble(-0.2, 0.2)
            samples += s(newId(), p.id, sid, SensorType.AMBIENT_TEMPERATURE, linkedMapOf("value" to r(t)), ts)
            samples += s(newId(), p.id, sid, SensorType.RELATIVE_HUMIDITY, linkedMapOf("value" to r(48.0 - z * 2 + rnd.nextDouble(-1.0, 1.0))), ts)
        }
        // Konumu bilinmeyen bir nokta: sahnede gösterilmez, listede "konumsuz" görünür.
        val ts = start.plusSeconds(600).toString()
        val unplaced = MeasurementPoint(
            id = newId(), projectId = pid, sessionId = sid, label = "D-konumsuz",
            notes = "DEMO: konum girilmedi", createdAt = ts, positionSource = PositionSource.NONE,
        )
        points += unplaced
        samples += s(newId(), unplaced.id, sid, SensorType.MAGNETIC_FIELD, linkedMapOf("x" to 20.0, "y" to 15.0, "z" to null, "magnitude" to null), ts)
        return ProjectBundle(project = project, sessions = listOf(session), points = points, samples = samples)
    }

    private fun r(v: Double, digits: Int = 2): Double {
        var f = 1.0
        repeat(digits) { f *= 10 }
        return round(v * f) / f
    }

    private fun s(id: String, pointId: String, sid: String, t: SensorType, values: Map<String, Double?>, ts: String) =
        SensorSample(
            id = id, pointId = pointId, sessionId = sid, sensorType = t, values = values, unit = t.unit,
            timestamp = ts, source = SampleSource.DEMO, calibrationInfo = "DEMO",
        )
}
