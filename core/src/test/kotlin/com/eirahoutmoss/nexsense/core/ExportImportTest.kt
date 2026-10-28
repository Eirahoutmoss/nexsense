package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.export.CsvExporter
import com.eirahoutmoss.nexsense.core.export.JsonCodec
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import com.eirahoutmoss.nexsense.core.store.InMemoryProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExportImportTest {

    private fun repoWithData(): Pair<ProjectRepository, String> {
        val r = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        val p = r.createProject("Ofis, 2. kat", "\"tırnak\" ve virgül, satır\nsonu")
        r.addPoint(
            p.id, "Masa", "", 1.0, 2.5, 0.75,
            listOf(
                SampleDraft(SensorType.MAGNETIC_FIELD, linkedMapOf("x" to 1.5, "y" to null, "z" to 3.0, "magnitude" to null), "2026-10-27T10:00:00Z", SampleSource.DEVICE),
                SampleDraft(SensorType.LIGHT, linkedMapOf("value" to 320.0), "2026-10-27T10:00:01Z", SampleSource.DEVICE, quality = "high"),
            ),
        )
        r.addPoint(p.id, "Konumsuz", "", null, null, null, listOf(
            SampleDraft(SensorType.SOUND_LEVEL, linkedMapOf("rms" to -45.25, "peak" to -30.0), "2026-10-27T10:00:02Z", SampleSource.DERIVED),
        ))
        return r to p.id
    }

    @Test fun csvHasHeaderRowsUnitsAndEmptyNulls() {
        val (r, id) = repoWithData()
        val csv = CsvExporter.export(r.get(id)!!)
        val lines = csv.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals(CsvExporter.HEADER.joinToString(","), lines[0])
        // 4 manyetik + 1 ışık + 2 ses bileşeni
        assertEquals(1 + 7, lines.size)
        val yRow = lines.first { it.contains(",magnetic_field,y,") }
        assertTrue("null değer boş hücre olmalı: $yRow", yRow.contains(",magnetic_field,y,,µT,"))
        assertTrue(lines.first { it.contains(",light,value,") }.contains(",320,lx,1,2.5,0.75,manual,device,high,"))
        val soundRow = lines.first { it.contains(",sound_level,rms,") }
        assertTrue("konumsuz noktada koordinat boş: $soundRow", soundRow.contains(",-45.25,dBFS,,,,none,derived,"))
        assertTrue(csv.contains("\"Ofis, 2. kat\""))
    }

    @Test fun csvEscaping() {
        assertEquals("abc", CsvExporter.escape("abc"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"a\"\"b\"", CsvExporter.escape("a\"b"))
        assertEquals("\"a\nb\"", CsvExporter.escape("a\nb"))
        assertEquals("", CsvExporter.num(null))
        assertEquals("0", CsvExporter.num(0.0))
        assertEquals("-0.125", CsvExporter.num(-0.125))
    }

    @Test fun jsonRoundTripIntoAnotherRepository() {
        val (r, id) = repoWithData()
        val json = r.exportJson(id)
        assertTrue(json.contains("\"schemaVersion\": 3"))
        assertTrue(json.contains("\"y\": null"))

        val other = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        val imported = other.importJson(json)
        assertEquals(id, imported.id)
        assertEquals(r.get(id)!!, other.get(imported.id)!!)
    }

    @Test fun importingSameProjectTwiceCreatesCopyWithNewIds() {
        val (r, id) = repoWithData()
        val copy = r.importJson(r.exportJson(id))
        assertNotEquals(id, copy.id)
        val a = r.get(id)!!
        val b = r.get(copy.id)!!
        assertEquals(a.points.size, b.points.size)
        assertEquals(a.samples.size, b.samples.size)
        assertTrue(b.points.all { it.projectId == copy.id })
        assertTrue(b.samples.all { s -> b.points.any { it.id == s.pointId } })
        assertEquals(2, r.listProjects().size)
    }

    @Test fun rejectsForeignJson() {
        val r = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        for (bad in listOf("{}", "[1,2]", "not json", "{\"format\":\"başka\",\"schemaVersion\":1}")) {
            try {
                r.importJson(bad)
                fail(bad)
            } catch (e: ValidationException) {
                assertEquals(ValidationIssue.Code.BAD_FORMAT, e.issues.first().code)
            }
        }
    }

    @Test fun rejectsNewerSchema() {
        val (r, id) = repoWithData()
        val json = r.exportJson(id).replace("\"schemaVersion\": 3", "\"schemaVersion\": 99")
        try {
            r.importJson(json)
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.NEWER_SCHEMA, e.issues.first().code)
        }
    }

    @Test fun rejectsTamperedUnit() {
        val (r, id) = repoWithData()
        val json = r.exportJson(id).replace("\"unit\": \"lx\"", "\"unit\": \"\"")
        try {
            r.importJson(json)
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.MISSING_UNIT, e.issues.first().code)
        }
    }

    @Test fun demoBundleIsValidAndFullyLabelled() {
        val b = DemoData.bundle("DEMO", "")
        val r = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        r.putBundle(b)
        assertTrue(b.hasDemoData)
        assertTrue(b.samples.all { it.source == SampleSource.DEMO })
        assertEquals(b, JsonCodec.decode(JsonCodec.encode(b)))
    }
}
