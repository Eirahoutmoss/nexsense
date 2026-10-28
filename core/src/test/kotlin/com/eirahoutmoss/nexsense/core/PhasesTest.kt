package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.acoustic.Sonar
import com.eirahoutmoss.nexsense.core.acoustic.SoundPressure
import com.eirahoutmoss.nexsense.core.analysis.AnalysisEngine
import com.eirahoutmoss.nexsense.core.analysis.Anomalies
import com.eirahoutmoss.nexsense.core.analysis.Correlations
import com.eirahoutmoss.nexsense.core.analysis.Coverage
import com.eirahoutmoss.nexsense.core.analysis.CoverageFlag
import com.eirahoutmoss.nexsense.core.analysis.Interpolation
import com.eirahoutmoss.nexsense.core.analysis.MagneticFrame
import com.eirahoutmoss.nexsense.core.analysis.RoomEstimator
import com.eirahoutmoss.nexsense.core.analysis.Stats
import com.eirahoutmoss.nexsense.core.analysis.Thresholds
import com.eirahoutmoss.nexsense.core.analysis.Timeline
import com.eirahoutmoss.nexsense.core.demo.DemoData
import com.eirahoutmoss.nexsense.core.export.JsonCodec
import com.eirahoutmoss.nexsense.core.layers.LayerAnalysis
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.AnalysisSettings
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.SCHEMA_VERSION
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.ScanPoint
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.Threshold
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import com.eirahoutmoss.nexsense.core.report.CaseFile
import com.eirahoutmoss.nexsense.core.report.PlyWriter
import com.eirahoutmoss.nexsense.core.integrity.Sha256
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.spatial.AutoRecordPolicy
import com.eirahoutmoss.nexsense.core.spatial.FrameMath
import com.eirahoutmoss.nexsense.core.spatial.PdrTracker
import com.eirahoutmoss.nexsense.core.spatial.PeakStepDetector
import com.eirahoutmoss.nexsense.core.spatial.TrackRecorder
import com.eirahoutmoss.nexsense.core.spatial.VoxelGrid
import com.eirahoutmoss.nexsense.core.store.InMemoryProjectStorage
import com.eirahoutmoss.nexsense.core.store.ProjectRepository
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PhasesTest {

    private fun repo() = ProjectRepository(InMemoryProjectStorage()).apply { loadAll() }

    private fun mag(m: Double, ts: String = "2026-10-27T10:00:00Z") =
        SampleDraft(SensorType.MAGNETIC_FIELD, linkedMapOf("x" to 0.0, "y" to 0.0, "z" to m, "magnitude" to m), ts, SampleSource.DEVICE)

    private fun light(v: Double) = SampleDraft(SensorType.LIGHT, linkedMapOf("value" to v), "2026-10-27T10:00:00Z", SampleSource.DEVICE)

    // ---------- Şema 2 ----------

    @Test fun schemaV1FileIsUpgraded() {
        val v1 = """
            {"format":"nexsense-project","schemaVersion":1,
             "project":{"id":"p1","name":"Eski","description":"","createdAt":"2026-10-27T10:00:00Z","updatedAt":"2026-10-27T10:00:00Z",
                        "coordinateSystem":{"type":"relative-cartesian","unit":"m","handedness":"right","axes":"","origin":""},"schemaVersion":1},
             "sessions":[{"id":"s1","projectId":"p1","startedAt":"2026-10-27T10:00:00Z","endedAt":null,"notes":""}],
             "points":[{"id":"a","projectId":"p1","sessionId":"s1","label":"A","notes":"","x":1.0,"y":2.0,"z":0.5,
                        "createdAt":"2026-10-27T10:00:00Z","positionSource":"manual","positionConfidence":null}],
             "samples":[]}
        """.trimIndent()
        val b = JsonCodec.decode(v1)
        assertEquals(SCHEMA_VERSION, b.schemaVersion)
        assertEquals(SCHEMA_VERSION, b.project.schemaVersion)
        assertTrue(b.scans.isEmpty())
        assertEquals(AnalysisSettings(), b.analysis)
        assertNull(b.project.coordinateSystem.northAngleDeg)
        assertEquals("A", b.points.single().label)
    }

    @Test fun scansPersistAndReimportWithNewIds() {
        val r = repo()
        val p = r.createProject("Tarama")
        val pt = r.addPoint(p.id, "A1", "", 0.0, 0.0, 1.0, listOf(mag(45.0)), PositionSource.ARCORE, 0.9)
        assertEquals(0.9, pt.positionConfidence!!, 1e-9)
        val sc = r.addScan(
            p.id,
            Scan(
                id = "", projectId = "", method = ScanMethod.ARCORE, startedAt = "2026-10-27T10:00:00Z",
                points = listOf(ScanPoint(1.0, 2.0, 0.5, 0.8)), alignment = ScanAlignment(startPointId = pt.id),
            ),
        )
        assertEquals(p.id, sc.projectId)
        assertNotNull("açık oturuma bağlanmalı", sc.sessionId)
        val copy = r.importJson(r.exportJson(p.id))
        val cb = r.get(copy.id)!!
        assertEquals(1, cb.scans.size)
        assertEquals(copy.id, cb.scans[0].projectId)
        assertEquals(cb.points.single().id, cb.scans[0].alignment.startPointId)
        assertTrue(cb.scans[0].sessionId in cb.sessions.map { it.id })
    }

    @Test fun invalidScanAndThresholdRejected() {
        val r = repo()
        val p = r.createProject("X")
        try {
            r.addScan(p.id, Scan(id = "", projectId = "", method = ScanMethod.ARCORE, startedAt = "2026-10-27T10:00:00Z", points = listOf(ScanPoint(Double.NaN, 0.0, 0.0, 0.5))))
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.BAD_SCAN, e.issues.first().code)
        }
        try {
            r.setAnalysis(p.id, AnalysisSettings(thresholds = listOf(Threshold("MAGNETIC", min = 10.0, max = 5.0))))
            fail()
        } catch (e: ValidationException) {
            assertEquals(ValidationIssue.Code.BAD_THRESHOLD, e.issues.first().code)
        }
        r.setNorthAngle(p.id, -90.0)
        assertEquals(270.0, r.get(p.id)!!.project.coordinateSystem.northAngleDeg!!, 1e-9)
    }

    // ---------- Faz 2: konumlama ----------

    @Test fun arFrameMapsForwardToPlusY() {
        // ARCore'da kamera başlangıçta -z'ye bakar → proje +y
        val f = FrameMath.arToScan(0.0, 0.0, -1.0)
        assertEquals(0.0, f.x, 1e-12); assertEquals(1.0, f.y, 1e-12); assertEquals(0.0, f.z, 1e-12)
        val u = FrameMath.arToScan(0.0, 1.5, 0.0)
        assertEquals(0.0, u.y, 1e-12); assertEquals(1.5, u.z, 1e-12)
        val r = FrameMath.rotateZ(Vec3(1.0, 0.0, 0.0), 90.0)
        assertEquals(0.0, r.x, 1e-9); assertEquals(1.0, r.y, 1e-9)
        val a = FrameMath.align(Vec3(1.0, 0.0, 0.0), ScanAlignment(offsetX = 2.0, offsetY = 3.0, offsetZ = 0.5, yawDeg = 90.0))
        assertEquals(2.0, a.x, 1e-9); assertEquals(4.0, a.y, 1e-9); assertEquals(0.5, a.z, 1e-9)
        // Manyetik kuzey proje +x yönündeyse (saat yönünde 90°), ENU kuzey → proje +x
        val n = FrameMath.enuToProject(Vec3(0.0, 1.0, 0.0), 90.0)
        assertEquals(1.0, n.x, 1e-9); assertEquals(0.0, n.y, 1e-9)
    }

    @Test fun voxelGridKeepsBestPointPerCellAndCaps() {
        val g = VoxelGrid(sizeM = 0.1, maxPoints = 3)
        assertTrue(g.add(ScanPoint(0.01, 0.01, 0.01, 0.3)))
        assertTrue("aynı küpte daha güvenilir", g.add(ScanPoint(0.02, 0.02, 0.02, 0.9)))
        assertFalse(g.add(ScanPoint(0.03, 0.03, 0.03, 0.5)))
        assertEquals(1, g.size)
        assertEquals(0.9, g.points().single().c, 1e-9)
        g.add(ScanPoint(1.0, 0.0, 0.0, 0.5)); g.add(ScanPoint(-1.0, 0.0, 0.0, 0.5))
        assertFalse("sınır", g.add(ScanPoint(5.0, 5.0, 5.0, 0.5)))
        assertEquals(3, g.size)
        assertFalse(g.add(ScanPoint(Double.NaN, 0.0, 0.0, 1.0)))
    }

    @Test fun autoRecordPolicy() {
        val p = AutoRecordPolicy(stepM = 0.5, minIntervalS = 1.0)
        assertFalse("izleme yokken asla", p.offer(Vec3.ZERO, 0.0, tracking = false))
        assertTrue("ilk nokta başlangıçta", p.offer(Vec3.ZERO, 0.0, true))
        assertFalse(p.offer(Vec3(0.3, 0.0, 0.0), 2.0, true))
        assertFalse("çok hızlı", p.offer(Vec3(0.6, 0.0, 0.0), 0.5, true))
        assertTrue(p.offer(Vec3(0.6, 0.0, 0.0), 1.5, true))
        assertFalse(p.offer(Vec3(2.0, 0.0, 0.0), 3.0, false))
        assertEquals(2, p.count)
    }

    @Test fun trackRecorderThinsAndMeasuresLength() {
        val t = TrackRecorder(minStepM = 0.1)
        for (i in 0..100) t.offer(i * 0.1, Vec3(i * 0.01, 0.0, 0.0))
        assertEquals(1.0, t.length, 0.11)
        assertTrue(t.points().size in 9..12)
    }

    @Test fun pdrWalksInHeadingDirection() {
        val p = PdrTracker(startHeadingDeg = 30.0, stepLengthM = 0.7, holdHeightM = 1.1)
        repeat(4) { p.onStep(30.0) } // başlangıç yönü = +y
        assertEquals(0.0, p.x, 1e-9); assertEquals(2.8, p.y, 1e-9); assertEquals(1.1, p.position.z, 1e-9)
        repeat(2) { p.onStep(120.0) } // 90° sağa dön → +x
        assertEquals(1.4, p.x, 1e-9); assertEquals(2.8, p.y, 1e-9)
        assertTrue(p.confidence < 0.6)
        repeat(100) { p.onStep(30.0) }
        assertEquals(0.1, p.confidence, 1e-9)
    }

    @Test fun peakStepDetectorCountsSteps() {
        val d = PeakStepDetector()
        var steps = 0
        for (i in 0 until 1000) { // 10 sn, 100 Hz, 2 adım/sn
            val t = i * 10L
            val m = 9.81 + 3.0 * maxOf(0.0, sin(2 * PI * 2.0 * i / 100.0))
            if (d.feed(m, t)) steps++
        }
        assertTrue("adım $steps", steps in 17..21)
    }

    // ---------- Faz 4: akustik ----------

    @Test fun sonarFindsMultipleEchoesInOrder() {
        val fs = 48_000
        val ch = Sonar.chirp(fs, 2000.0, 10_000.0, 0.005)
        val c = Sonar.soundSpeed(20.0)
        val x = DoubleArray(12_000)
        fun put(dist: Double, amp: Double) {
            val delay = (2 * dist / c * fs).toInt()
            for (i in ch.indices) x[500 + delay + i] += amp * ch[i]
        }
        for (i in ch.indices) x[500 + i] += ch[i]
        put(2.5, 0.2); put(1.2, 0.3)
        val e = Sonar.echoes(Sonar.correlate(x, ch, 9000), fs, c)
        assertTrue("yankı ${e.size}", e.size >= 2)
        assertEquals(1.2, e[0].distanceM, 0.03)
        assertEquals(2.5, e[1].distanceM, 0.03)
        assertTrue(Sonar.confidence01(e[0].confidence) > 0.3)
        assertTrue(Sonar.echoes(DoubleArray(5000), fs, c).isEmpty())
    }

    @Test fun pingTrainSurvivesUnknownLatency() {
        val fs = 48_000
        val ch = Sonar.chirp(fs, 3000.0, 9000.0, 0.004)
        val c = Sonar.soundSpeed(20.0)
        val play = Sonar.pingTrain(ch, fs, pings = 6, spacingS = 0.1)
        val latency = 4321 // hoparlör gecikmesi (bilinmiyor)
        val echoDelay = (2 * 1.8 / c * fs).toInt()
        val x = DoubleArray(play.size + fs / 2 + latency)
        val r = kotlin.random.Random(5)
        for (i in play.indices) {
            x[latency + i] += play[i]
            if (latency + echoDelay + i < x.size) x[latency + echoDelay + i] += 0.2 * play[i]
        }
        for (i in x.indices) x[i] += r.nextDouble(-0.02, 0.02)
        val corr = Sonar.correlate(x, ch, x.size - ch.size)
        val avg = Sonar.alignPings(corr, fs, 0.1, 6, c, 5.0)
        val e = Sonar.echoes(avg, fs, c, maxDistM = 5.0)
        assertTrue(e.isNotEmpty())
        assertEquals(1.8, e.first().distanceM, 0.03)
    }

    @Test fun soundPressureAndDominantFrequency() {
        val fs = 48_000
        val sine = FloatArray(fs) { (0.5 * sin(2 * PI * 1000.0 * it / fs)).toFloat() }
        assertEquals(-9.03, SoundPressure.leqDbfs(sine)!!, 0.05) // 0,5/√2
        assertEquals(-6.02, SoundPressure.peakDbfs(sine)!!, 0.05)
        assertEquals(1000.0, SoundPressure.dominantHz(sine, fs)!!, 2.0)
        val off = SoundPressure.offsetFrom(70.0, -30.0)
        assertEquals(100.0, off, 1e-9)
        assertEquals(64.0, SoundPressure.spl(-36.0, off), 1e-9)
        assertNull(SoundPressure.leqDbfs(FloatArray(0)))
    }

    // ---------- Faz 5: analiz ----------

    private fun gridProject(values: List<Double>, positions: List<Triple<Double, Double, Double>>): Pair<ProjectRepository, String> {
        val r = repo()
        val p = r.createProject("Izgara")
        values.forEachIndexed { i, v ->
            val (x, y, z) = positions[i]
            r.addPoint(p.id, "P$i", "", x, y, z, listOf(mag(v), light(v * 2 + 1)))
        }
        return r to p.id
    }

    @Test fun idwIsExactAtPointsAndUnknownFarAway() {
        val pos = listOf(Triple(0.0, 0.0, 1.0), Triple(2.0, 0.0, 1.0), Triple(0.0, 2.0, 1.0), Triple(2.0, 2.0, 1.0))
        val (r, id) = gridProject(listOf(40.0, 50.0, 60.0, 70.0), pos)
        val b = r.get(id)!!
        val data = LayerAnalysis.compute(b, LayerKind.MAGNETIC)
        val g = Interpolation.idw(b, data, AnalysisSettings(gridCellM = 0.5, maxEstimateDistanceM = 1.0))!!
        // (0,0) noktasını içeren hücre merkezi (0.25, 0.25) → 40'a yakın
        val i0 = ((0.25 - g.originX) / g.cellM).toInt(); val j0 = ((0.25 - g.originY) / g.cellM).toInt()
        assertEquals(40.0, g.at(i0, j0)!!, 3.0)
        assertTrue(g.confidenceAt(i0, j0) > 0.5)
        // Ortadaki (1,1) hücresi her noktaya √2 > 1 m uzak → tahmin yok
        val ic = ((1.25 - g.originX) / g.cellM).toInt() - 1; val jc = ((1.25 - g.originY) / g.cellM).toInt() - 1
        assertNull(g.at(ic, jc))
        assertTrue(g.estimatedCells > 0)
        // Az nokta: ızgara yok
        val (r2, id2) = gridProject(listOf(1.0, 2.0, 3.0), pos.take(3))
        assertNull(Interpolation.idw(r2.get(id2)!!, LayerAnalysis.compute(r2.get(id2)!!, LayerKind.MAGNETIC)))
    }

    @Test fun anomaliesCorrelationsThresholdsCoverage() {
        val pos = (0 until 8).map { Triple(it.toDouble(), 0.0, 1.0) }
        val (r, id) = gridProject(listOf(45.0, 46.0, 44.0, 45.5, 46.5, 44.5, 45.0, 120.0), pos)
        r.setAnalysis(id, AnalysisSettings(thresholds = listOf(Threshold("MAGNETIC", max = 100.0), Threshold("LIGHT_NOT_A_LAYER", max = 1.0))))
        val b = r.get(id)!!
        val res = AnalysisEngine.run(b)
        val an = Anomalies.find(res.layers.getValue(LayerKind.MAGNETIC))
        assertEquals(1, an.size)
        assertEquals(120.0, an[0].value, 1e-9)
        assertEquals(1, res.alerts.size)
        assertTrue(res.alerts[0].above)
        assertTrue(Correlations.all(res.layers).isEmpty()) // ışık bir katman değil
        val cov = res.coverage.first { it.kind == LayerKind.MAGNETIC }
        assertTrue(CoverageFlag.ONE_SESSION in cov.flags)
        assertFalse(CoverageFlag.FEW_POINTS in cov.flags)
        assertTrue(CoverageFlag.NO_DATA in res.coverage.first { it.kind == LayerKind.TEMPERATURE }.flags)
        assertEquals(1.0, Stats.pearson(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))!!, 1e-9)
        assertNull(Stats.pearson(listOf(1.0, 1.0, 1.0), listOf(2.0, 4.0, 6.0)))
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 2.0, 3.0))!!, 1e-9)
    }

    @Test fun demoAnalysisFindsCorrelationsAndRoom() {
        val b = DemoData.bundle("DEMO", "")
        val res = AnalysisEngine.run(b)
        assertTrue(res.correlations.isNotEmpty())
        assertTrue(res.correlations.all { it.n >= Correlations.MIN_PAIRS && abs(it.r) <= 1.0 })
        val room = RoomEstimator.of(b)!!
        assertEquals(5.0, room.lengthM, 0.3); assertEquals(4.0, room.widthM, 0.3); assertEquals(2.6, room.heightM, 0.3)
        assertEquals(LayerStatus.HAS_DATA, res.layers.getValue(LayerKind.SCAN).status)
        assertTrue(res.layers.getValue(LayerKind.SCAN).containsDemo)
        assertEquals(LayerStatus.HAS_DATA, res.layers.getValue(LayerKind.REVERB).status)
    }

    @Test fun timelineComparesSameSpotAcrossSessions() {
        val r = repo()
        val p = r.createProject("Zaman")
        r.startSession(p.id)
        r.addPoint(p.id, "sabah", "", 1.0, 1.0, 1.0, listOf(mag(45.0)))
        r.startSession(p.id)
        r.addPoint(p.id, "akşam", "", 1.1, 1.0, 1.0, listOf(mag(60.0)))
        r.addPoint(p.id, "uzak", "", 5.0, 5.0, 1.0, listOf(mag(10.0)))
        val b = r.get(p.id)!!
        val ch = Timeline.changes(b)
        assertEquals(1, ch.size)
        assertEquals(15.0, ch[0].delta, 1e-9)
        assertEquals(1, ch[0].fromSession); assertEquals(2, ch[0].toSession)
        val st = Timeline.stats(b).filter { it.kind == LayerKind.MAGNETIC }
        assertEquals(listOf(1, 2), st.map { it.index })
    }

    @Test fun magneticWorldComponents() {
        val r = repo()
        val p = r.createProject("M")
        val ident = listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        r.addPoint(
            p.id, "a", "", null, null, null,
            listOf(
                SampleDraft(
                    SensorType.MAGNETIC_FIELD, linkedMapOf("x" to 0.0, "y" to 20.0, "z" to -40.0, "magnitude" to 44.7),
                    "2026-10-27T10:00:00Z", SampleSource.DEVICE, deviceOrientation = DeviceOrientation(rotationMatrix = ident),
                ),
            ),
        )
        val m = MagneticFrame.of(r.get(p.id)!!).single()
        assertEquals(20.0, m.horizontal, 1e-9)
        assertEquals(-40.0, m.vertical, 1e-9)
        assertEquals(63.43, m.inclinationDeg, 0.01)
    }

    // ---------- Faz 6: raporlama ----------

    @Test fun plyHasConsistentHeader() {
        val b = DemoData.bundle("DEMO", "")
        val ply = PlyWriter.render(b, LayerAnalysis.compute(b, LayerKind.MAGNETIC))
        val lines = ply.lines()
        val nv = lines.first { it.startsWith("element vertex") }.split(" ").last().toInt()
        val nf = lines.first { it.startsWith("element face") }.split(" ").last().toInt()
        val body = lines.dropWhile { it != "end_header" }.drop(1).filter { it.isNotBlank() }
        assertEquals(nv + nf, body.size)
        assertEquals(600 + 12 + 18, nv) // tarama + 3 düzlem×4 köşe + yerleşik noktalar
        assertEquals(6, nf)
        assertTrue(body.first().split(" ").size == 6)
    }

    @Test fun caseFileManifestMatchesContents() {
        val out = ByteArrayOutputStream()
        val files = listOf("proje.json" to "{}".toByteArray(), "veri.csv" to "a,b\n".toByteArray())
        CaseFile.write(out, files, "oku beni")
        val names = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names[e.name] = z.readBytes()
            }
        }
        assertEquals(setOf("proje.json", "veri.csv", "BENIOKU.txt", CaseFile.MANIFEST), names.keys)
        val manifest = String(names.getValue(CaseFile.MANIFEST))
        assertTrue(manifest.contains("${Sha256.hex("{}".toByteArray())}  proje.json"))
        assertEquals(3, manifest.trim().lines().size)
        assertEquals("Salon-2.-kat", CaseFile.safeName(" Salon 2. kat "))
    }
}
