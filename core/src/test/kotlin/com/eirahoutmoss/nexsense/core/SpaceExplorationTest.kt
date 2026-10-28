package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.guide.CaptureKind
import com.eirahoutmoss.nexsense.core.guide.GuideEngine
import com.eirahoutmoss.nexsense.core.guide.GuideSignals
import com.eirahoutmoss.nexsense.core.guide.GuideStep
import com.eirahoutmoss.nexsense.core.guide.Hint
import com.eirahoutmoss.nexsense.core.guide.Mission
import com.eirahoutmoss.nexsense.core.guide.Missions
import com.eirahoutmoss.nexsense.core.guide.Need
import com.eirahoutmoss.nexsense.core.guide.StepOutcome
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanRay
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.spatial.CellState
import com.eirahoutmoss.nexsense.core.spatial.Frontier
import com.eirahoutmoss.nexsense.core.spatial.Frontiers
import com.eirahoutmoss.nexsense.core.spatial.OccupancyGrid
import com.eirahoutmoss.nexsense.core.spatial.RadarReading
import com.eirahoutmoss.nexsense.core.spatial.RadarSweep
import com.eirahoutmoss.nexsense.core.store.InMemoryProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SpaceExplorationTest {

    @Test fun rayCarvesFreeSpaceAndMarksHit() {
        val g = OccupancyGrid(0.1)
        g.addRay(0.05, 0.05, 2.05, 0.05, hit = true)
        assertEquals(CellState.FREE, g.stateAt(1.0, 0.05))
        assertEquals(CellState.OCCUPIED, g.stateAt(2.05, 0.05))
        assertEquals(CellState.UNKNOWN, g.stateAt(3.0, 0.05))
        assertEquals(CellState.UNKNOWN, g.stateAt(1.0, 1.0))
        // Yankısız ışın: uç dolu değil
        g.addRay(0.05, 0.05, 0.05, 2.05, hit = false)
        assertEquals(CellState.FREE, g.stateAt(0.05, 2.05))
    }

    @Test fun conflictingEvidenceBalancesAndConfidenceGrows() {
        val g = OccupancyGrid(0.1)
        g.addOccupied(1.0, 1.0)
        val c1 = g.confidence(g.ix(1.0), g.ix(1.0))
        g.addOccupied(1.0, 1.0)
        assertTrue(g.confidence(g.ix(1.0), g.ix(1.0)) > c1)
        repeat(6) { g.addFree(1.0, 1.0) }
        assertEquals(CellState.FREE, g.stateAt(1.0, 1.0))
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), OccupancyGrid.bresenham(0, 0, 2, 2))
    }

    /** Uçları kapalı, sonu açık bir koridor (mağara/tünel benzeri): sınır yalnızca açık uçta olmalı. */
    private fun corridor(): OccupancyGrid {
        val g = OccupancyGrid(0.1)
        var x = 0.05
        while (x < 3.0) {
            var y = -0.45
            while (y < 0.5) { g.addFree(x, y, 1.0); y += 0.1 }
            g.addOccupied(x, 0.55, 1.0); g.addOccupied(x, -0.55, 1.0)
            x += 0.1
        }
        var y = -0.65
        while (y < 0.7) { g.addOccupied(-0.05, y, 1.0); y += 0.1 }
        return g
    }

    @Test fun frontierAppearsOnlyAtOpenEnd() {
        val f = Frontiers.find(corridor())
        assertEquals(1, f.size)
        assertEquals(2.95, f[0].centroid.x, 0.06)
        assertEquals(0.0, f[0].centroid.y, 0.06)
        val chosen = Frontiers.choose(f, Vec3(0.5, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))
        assertNotNull(chosen)
        assertNull("çok yakın sınır seçilmez", Frontiers.choose(f, Vec3(2.9, 0.0, 0.0), null, minDist = 0.5))
    }

    @Test fun chooseFavoursBigNearAndAhead() {
        val small = Frontier(5, Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))
        val bigBehind = Frontier(20, Vec3(-3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0))
        val bigAhead = Frontier(20, Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0))
        assertEquals(bigAhead, Frontiers.choose(listOf(small, bigBehind, bigAhead), Vec3.ZERO, Vec3(1.0, 0.0, 0.0)))
    }

    @Test fun radarSectorsTurnsAndRays() {
        val r = RadarSweep(12)
        assertEquals(0, r.sectorOf(10.0))
        assertEquals(11, r.sectorOf(-10.0))
        assertEquals(6, r.sectorOf(185.0))
        r.add(RadarReading(15.0, listOf(2.0, 3.5), 0.9))
        r.add(RadarReading(45.0, emptyList(), 0.3)) // yankı yok
        r.add(RadarReading(75.0, emptyList(), 0.0)) // ölçüm başarısız
        assertEquals(3, r.measured)
        val t = r.turnToNext(15.0)!!
        assertTrue("en yakın ölçülmemiş dilim sağda (−15°)", t < 0)
        val rays = r.rays(Vec3(1.0, 1.0, 1.2))
        assertEquals(2, rays.size)
        val hit = rays.first { it.hit }
        assertEquals(1.0 + 2.0 * cos(Math.toRadians(15.0)), hit.x, 1e-6)
        assertEquals(1.0 + 2.0 * sin(Math.toRadians(15.0)), hit.y, 1e-6)
        assertTrue("yön belirsizliği: güven ≤ 0,6", hit.c <= 0.6)
        assertEquals(0.2, rays.first { !it.hit }.c, 1e-9)
        assertEquals(2, r.echoPoints(Vec3.ZERO).size) // ilk okumanın iki yankısı
    }

    @Test fun radarStepPingsEachSectorOnce() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.RadarSweep("r", sectors = 4, holdS = 0.5)), 1), Need.entries.toSet())
        var t = 0.0
        var yaw = 45.0
        var pings = 0
        repeat(400) {
            val f = Math.toRadians(yaw)
            val fr = e.update(GuideSignals(t, tracking = true, position = Vec3.ZERO, facing = Vec3(cos(f), sin(f), 0.0), accelMagnitude = 9.81))
            for (c in fr.captures) {
                assertEquals(CaptureKind.ACOUSTIC_PING, c.kind)
                val dy = ((c.yawDeg!! - yaw) % 360.0 + 540.0) % 360.0 - 180.0
                assertEquals("aynı yön (±180° sarması)", 0.0, dy, 1e-6)
                assertEquals(Hint.WORKING, fr.hint)
                pings++
                e.completePing(c.yawDeg!!, RadarReading(c.yawDeg!!, listOf(2.0), 0.8))
            }
            // Ölçülen dilimde motorun gösterdiği yöne dön
            if (fr.hint == Hint.TURN_LEFT) yaw += 10.0
            if (fr.hint == Hint.TURN_RIGHT) yaw -= 10.0
            t += 0.1
            if (e.finished) return@repeat
        }
        assertTrue(e.finished)
        assertEquals(4, pings)
        assertEquals(StepOutcome.AUTO, e.summary().outcomes.single().second)
    }

    @Test fun frontierStepGuidesAndExhaustionEndsMission() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.Frontier("go1"), GuideStep.RadarSweep("radar2"), GuideStep.Frontier("go2")), 1), Need.entries.toSet())
        val f0 = e.update(GuideSignals(0.0, tracking = true, position = Vec3.ZERO, facing = Vec3(0.0, 1.0, 0.0)))
        assertEquals("sınır henüz hesaplanmadı", Hint.WORKING, f0.hint)
        val f1 = e.update(GuideSignals(0.1, tracking = true, position = Vec3.ZERO, facing = Vec3(0.0, 1.0, 0.0), frontier = Vec3(3.0, 0.0, 0.0)))
        assertEquals("hedef sağda", Hint.TURN_RIGHT, f1.hint)
        val f2 = e.update(GuideSignals(0.2, tracking = true, position = Vec3(1.0, 0.0, 0.0), facing = Vec3(1.0, 0.0, 0.0), frontier = Vec3(3.0, 0.0, 0.0)))
        assertEquals(Hint.KEEP_GOING, f2.hint)
        e.update(GuideSignals(0.3, tracking = true, position = Vec3(2.6, 0.0, 0.0), facing = Vec3(1.0, 0.0, 0.0), frontier = Vec3(3.0, 0.0, 0.0)))
        assertTrue(e.current is GuideStep.RadarSweep)
        e.skip()
        e.update(GuideSignals(0.4, tracking = true, position = Vec3(2.6, 0.0, 0.0), frontierExhausted = true))
        assertTrue("sınır kalmadı → görev biter", e.finished)
        assertEquals(3, e.summary().outcomes.size)
    }

    @Test fun exploreMissionHasNoCornerAssumption() {
        val m = Missions.EXPLORE
        assertTrue(m.steps.none { it is GuideStep.Mark })
        assertTrue(m.steps.any { it is GuideStep.RadarSweep } && m.steps.any { it is GuideStep.Frontier })
        assertTrue(Need.ACOUSTIC in Missions.RADAR.needs)
    }

    @Test fun gridFromDemoAndRadarScanPersistence() {
        val b = DemoData.bundle("DEMO", "")
        val g = OccupancyGrid.build(b)
        assertTrue(g.count(CellState.OCCUPIED) > 50)
        assertTrue(g.count(CellState.FREE) > 20)
        assertEquals(CellState.FREE, g.stateAt(2.5, 2.0)) // yürüme yolu

        val r = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        val p = r.createProject("Mağara")
        val sweep = RadarSweep(12).apply { for (k in 0 until 12) add(RadarReading(k * 30.0 + 15, listOf(1.5 + k * 0.1), 0.7)) }
        r.addScan(p.id, Scan(id = "", projectId = "", method = ScanMethod.ACOUSTIC_RADAR, startedAt = "2026-10-28T10:00:00Z", rays = sweep.rays(Vec3(0.0, 0.0, 1.0))))
        val copy = r.importJson(r.exportJson(p.id))
        val cb = r.get(copy.id)!!
        assertEquals(12, cb.scans.single().rays.size)
        val g2 = OccupancyGrid.build(cb)
        assertEquals(CellState.FREE, g2.stateAt(0.5 * cos(Math.toRadians(15.0)), 0.5 * sin(Math.toRadians(15.0))))
        assertTrue(Frontiers.find(g2).isEmpty() || Frontiers.find(g2).all { it.cells >= 4 })
        try {
            r.addScan(p.id, Scan(id = "", projectId = "", method = ScanMethod.ACOUSTIC_RADAR, startedAt = "2026-10-28T10:00:00Z", rays = listOf(ScanRay(0.0, 0.0, 0.0, Double.NaN, 0.0, 0.0, true, 0.5))))
            fail()
        } catch (_: ValidationException) {
        }
    }
}
