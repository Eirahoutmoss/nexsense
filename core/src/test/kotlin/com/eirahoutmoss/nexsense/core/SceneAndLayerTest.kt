package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.layers.LayerAnalysis
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerSelection
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.scene.Bounds
import com.eirahoutmoss.nexsense.core.scene.Grid
import com.eirahoutmoss.nexsense.core.scene.OrbitCamera
import com.eirahoutmoss.nexsense.core.scene.ScenePicking
import com.eirahoutmoss.nexsense.core.scene.ScenePoint
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.store.InMemoryProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneAndLayerTest {

    private val w = 1000.0
    private val h = 800.0

    @Test fun targetProjectsToScreenCenter() {
        val cam = OrbitCamera(target = Vec3(2.0, 1.0, 0.5))
        val p = cam.project(Vec3(2.0, 1.0, 0.5), w, h)!!
        assertEquals(w / 2, p.x, 1e-6)
        assertEquals(h / 2, p.y, 1e-6)
        assertEquals(cam.distance, p.depth, 1e-6)
    }

    @Test fun pointsBehindCameraAreNotDrawn() {
        val cam = OrbitCamera()
        val behind = cam.eye + (cam.eye - cam.target).normalized()
        assertNull(cam.project(behind, w, h))
    }

    @Test fun higherPointAppearsHigherOnScreen() {
        val cam = OrbitCamera(pitchDeg = 20.0)
        val low = cam.project(Vec3(0.0, 0.0, 0.0), w, h)!!
        val high = cam.project(Vec3(0.0, 0.0, 1.0), w, h)!!
        assertTrue(high.y < low.y)
    }

    @Test fun cameraControlsStayInRange() {
        var cam = OrbitCamera()
        cam = cam.rotate(0.0, 10_000.0)
        assertEquals(OrbitCamera.MAX_PITCH, cam.pitchDeg, 1e-9)
        cam = cam.zoom(1e9)
        assertEquals(OrbitCamera.MIN_DIST, cam.distance, 1e-9)
        assertEquals(cam, cam.zoom(0.0))
        val panned = OrbitCamera().pan(100.0, 0.0, h)
        assertTrue(panned.target != Vec3.ZERO)
    }

    @Test fun pickingSelectsNearestTappedPoint() {
        val cam = OrbitCamera()
        val pts = listOf(ScenePoint("a", Vec3(0.0, 0.0, 0.0)), ScenePoint("b", Vec3(2.0, 2.0, 1.0)))
        val pa = cam.project(pts[0].position, w, h)!!
        val pb = cam.project(pts[1].position, w, h)!!
        assertEquals("a", ScenePicking.pick(pts, cam, w, h, pa.x + 3, pa.y - 2, 40.0))
        assertEquals("b", ScenePicking.pick(pts, cam, w, h, pb.x, pb.y, 40.0))
        assertNull(ScenePicking.pick(pts, cam, w, h, 5.0, 5.0, 40.0))
    }

    @Test fun fitCentersOnBounds() {
        val b = Bounds.of(listOf(Vec3(0.0, 0.0, 0.0), Vec3(4.0, 2.0, 2.0)))!!
        val cam = OrbitCamera.fit(b)
        assertEquals(Vec3(2.0, 1.0, 1.0), cam.target)
        assertNull(Bounds.of(emptyList()))
        assertTrue(Grid.lines(b).any { it.isAxis })
    }

    @Test fun demoLayersHaveDataAndDemoFlag() {
        val b = DemoData.bundle("DEMO", "")
        val all = LayerAnalysis.computeAll(b)
        for (k in listOf(LayerKind.MAGNETIC, LayerKind.MOTION, LayerKind.ACOUSTIC, LayerKind.TEMPERATURE, LayerKind.HUMIDITY)) {
            assertEquals(k.name, LayerStatus.HAS_DATA, all.getValue(k).status)
            assertTrue(k.name, all.getValue(k).containsDemo)
        }
        val mag = all.getValue(LayerKind.MAGNETIC)
        assertEquals(18, mag.values.size) // konumsuz noktanın büyüklüğü null → katmana girmez
        assertTrue(mag.max!! > mag.min!!)
        assertEquals(0.0, mag.normalized(mag.min!!), 1e-9)
        assertEquals(1.0, mag.normalized(mag.max!!), 1e-9)
        assertEquals(1, all.getValue(LayerKind.GEOMETRY).unplacedCount)
        assertEquals(LayerStatus.HAS_DATA, all.getValue(LayerKind.NOTES).status)
    }

    @Test fun layersWithoutMeasurementsReportNoData() {
        val r = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }
        val p = r.createProject("Boş")
        val empty = r.get(p.id)!!
        for (k in LayerKind.entries) assertEquals(k.name, LayerStatus.NO_DATA, LayerAnalysis.compute(empty, k).status)

        r.addPoint(p.id, "a", "", null, null, null, listOf(
            SampleDraft(SensorType.MAGNETIC_FIELD, linkedMapOf("x" to 1.0, "y" to 1.0, "z" to 1.0, "magnitude" to 1.7), "2026-10-27T10:00:00Z", SampleSource.DEVICE),
        ))
        val b: ProjectBundle = r.get(p.id)!!
        val mag = LayerAnalysis.compute(b, LayerKind.MAGNETIC)
        assertEquals(LayerStatus.HAS_DATA, mag.status)
        assertEquals(1, mag.unplacedCount)
        assertFalse(mag.containsDemo)
        assertEquals(LayerStatus.NO_DATA, LayerAnalysis.compute(b, LayerKind.TEMPERATURE).status)
        assertEquals(LayerStatus.NO_DATA, LayerAnalysis.compute(b, LayerKind.GEOMETRY).status) // yerleşik nokta yok
    }

    @Test fun layerToggling() {
        var sel = LayerSelection()
        assertTrue(sel.isVisible(LayerKind.MAGNETIC))
        sel = sel.toggle(LayerKind.ACOUSTIC)
        assertEquals(LayerKind.ACOUSTIC, sel.colorLayer)
        sel = sel.toggle(LayerKind.ACOUSTIC)
        assertFalse(sel.isVisible(LayerKind.ACOUSTIC))
        assertEquals(LayerKind.MAGNETIC, sel.colorLayer)
        sel = sel.toggle(LayerKind.MAGNETIC)
        assertNull(sel.colorLayer)
        sel = sel.toggle(LayerKind.GEOMETRY)
        assertFalse(sel.isVisible(LayerKind.GEOMETRY))
        assertNotNull(sel)
    }
}
