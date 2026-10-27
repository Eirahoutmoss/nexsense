package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import com.eirahoutmoss.nexsense.core.store.FileProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class RepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private var t = Instant.parse("2026-10-27T10:00:00Z")
    private val clock = { t.also { t = t.plusSeconds(1) } }

    private fun repo() = ProjectRepository(FileProjectStorage(tmp.root), clock).apply { loadAll() }

    private fun magDraft(ts: String = "2026-10-27T10:00:00Z") = SampleDraft(
        sensorType = SensorType.MAGNETIC_FIELD,
        values = linkedMapOf("x" to 10.0, "y" to -5.5, "z" to 40.0, "magnitude" to 41.6),
        timestamp = ts,
        source = SampleSource.DEVICE,
        quality = "high",
    )

    @Test fun createSaveReopenAndDelete() {
        val r1 = repo()
        val p = r1.createProject("  Salon  ", "Ev ölçümü")
        assertEquals("Salon", p.name)

        // Uygulama kapanıp açılıyor: yeni depo örneği aynı klasörü okur.
        val r2 = repo()
        assertEquals(listOf("Salon"), r2.listProjects().map { it.name })
        assertEquals("Ev ölçümü", r2.get(p.id)!!.project.description)

        r2.renameProject(p.id, "Oturma odası")
        assertEquals("Oturma odası", repo().get(p.id)!!.project.name)

        r2.deleteProject(p.id)
        assertTrue(repo().listProjects().isEmpty())
    }

    @Test fun blankNameRejected() {
        try {
            repo().createProject("   ")
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.BLANK_NAME, e.issues.single().code)
        }
    }

    @Test fun pointAndSamplesPersist() {
        val r = repo()
        val p = r.createProject("Oda")
        val pt = r.addPoint(p.id, "Pencere", "not", 1.0, 2.0, 0.5, listOf(magDraft()))
        assertEquals(PositionSource.MANUAL, pt.positionSource)

        val reopened = repo().get(p.id)!!
        assertEquals(1, reopened.points.size)
        assertEquals(1, reopened.sessions.size)
        assertTrue("oturum otomatik açılmalı", reopened.sessions.single().isOpen)
        val s = reopened.samples.single()
        assertEquals(pt.id, s.pointId)
        assertEquals("µT", s.unit)
        assertEquals(41.6, s.values["magnitude"]!!, 1e-9)
        assertEquals(listOf("x", "y", "z", "magnitude"), s.values.keys.toList())
    }

    @Test fun unknownPositionStaysNull() {
        val r = repo()
        val p = r.createProject("Oda")
        val pt = r.addPoint(p.id, "", "", null, null, null, listOf(magDraft()))
        assertNull(pt.x); assertNull(pt.y); assertNull(pt.z)
        assertEquals(PositionSource.NONE, pt.positionSource)
        assertFalse(pt.isPlaced)
        assertEquals("#1", pt.label)
    }

    @Test fun partialPositionRejected() {
        val r = repo()
        val p = r.createProject("Oda")
        try {
            r.addPoint(p.id, "a", "", 1.0, null, null, emptyList())
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.PARTIAL_POSITION, e.issues.first().code)
        }
        assertTrue(repo().get(p.id)!!.points.isEmpty())
    }

    @Test fun wrongUnitRejectedAndNothingSaved() {
        val r = repo()
        val p = r.createProject("Oda")
        try {
            r.addPoint(p.id, "a", "", null, null, null, listOf(magDraft().copy(unit = "mT")))
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.WRONG_UNIT, e.issues.first().code)
        }
        assertTrue(r.get(p.id)!!.points.isEmpty())
    }

    @Test fun sessionsStartAndEnd() {
        val r = repo()
        val p = r.createProject("Oda")
        val s1 = r.startSession(p.id, "sabah")
        val s2 = r.startSession(p.id, "öğle")
        val b = repo().get(p.id)!!
        assertEquals(2, b.sessions.size)
        assertNotNull("ilk oturum kapanmalı", b.sessions.first { it.id == s1.id }.endedAt)
        assertEquals(s2.id, b.openSession!!.id)
        r.endSession(p.id)
        assertNull(repo().get(p.id)!!.openSession)
    }

    @Test fun updateAndDeletePoint() {
        val r = repo()
        val p = r.createProject("Oda")
        val pt = r.addPoint(p.id, "a", "", null, null, null, listOf(magDraft()))
        val moved = r.updatePoint(p.id, pt.id, "b", "yeni not", 1.0, 1.0, 1.0)
        assertEquals(PositionSource.MANUAL, moved.positionSource)
        assertTrue(repo().get(p.id)!!.points.single().isPlaced)
        r.deletePoint(p.id, pt.id)
        val b = repo().get(p.id)!!
        assertTrue(b.points.isEmpty())
        assertTrue("noktanın örnekleri de silinmeli", b.samples.isEmpty())
    }

    @Test fun brokenFileIsReportedNotDeleted() {
        val r = repo()
        r.createProject("İyi")
        tmp.newFile("bozuk.json").writeText("{ bu json değil")
        val r2 = repo()
        assertEquals(1, r2.listProjects().size)
        assertEquals(listOf("bozuk"), r2.brokenFiles.value)
        assertTrue(java.io.File(tmp.root, "bozuk.json").exists())
    }

    @Test fun turkishCharactersSurvive() {
        val r = repo()
        val p = r.createProject("Çalışma odası ğüşıöç İĞÜŞÖÇ", "µT °C m/s²")
        val b = repo().get(p.id)!!
        assertEquals("Çalışma odası ğüşıöç İĞÜŞÖÇ", b.project.name)
        assertEquals("µT °C m/s²", b.project.description)
    }
}
