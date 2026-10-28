package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.listen.ClockAlign
import com.eirahoutmoss.nexsense.core.listen.DrillSignal
import com.eirahoutmoss.nexsense.core.listen.DrillSource
import com.eirahoutmoss.nexsense.core.listen.Fusion
import com.eirahoutmoss.nexsense.core.listen.ListenCodec
import com.eirahoutmoss.nexsense.core.listen.ListenEvent
import com.eirahoutmoss.nexsense.core.listen.ListenFormatException
import com.eirahoutmoss.nexsense.core.listen.ListenKind
import com.eirahoutmoss.nexsense.core.listen.ListenPosition
import com.eirahoutmoss.nexsense.core.listen.ListenRecord
import com.eirahoutmoss.nexsense.core.listen.Patterns
import com.eirahoutmoss.nexsense.core.listen.SoundEventDetector
import com.eirahoutmoss.nexsense.core.listen.VibrationDetector
import com.eirahoutmoss.nexsense.core.scene.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class ListenTest {

    private val fs = 16_000

    private fun noise(seconds: Double, amp: Double = 0.002, seed: Int = 1): FloatArray {
        val r = Random(seed)
        return FloatArray((seconds * fs).toInt()) { ((r.nextDouble() * 2 - 1) * amp).toFloat() }
    }

    private fun run(det: SoundEventDetector, x: FloatArray): List<ListenEvent> {
        val out = ArrayList<ListenEvent>()
        var i = 0
        while (i < x.size) {
            val n = minOf(1024, x.size - i)
            out += det.feed(x.copyOfRange(i, i + n))
            i += n
        }
        return out + det.flush()
    }

    private fun mix(base: FloatArray, add: FloatArray, atS: Double): FloatArray {
        val i0 = (atS * fs).toInt()
        for (i in add.indices) if (i0 + i < base.size) base[i0 + i] += add[i]
        return base
    }

    @Test fun detectsDrillKnocksAsDeliberatePattern() {
        val (sig, times) = DrillSignal.pattern(fs)
        val x = mix(noise(2.5 + sig.size.toDouble() / fs + 0.5), sig, 2.5)
        val det = SoundEventDetector(fs)
        val ev = run(det, x)
        assertNotNull(det.floorDbfs)
        assertEquals(9, ev.size)
        assertTrue(ev.all { it.kind == ListenKind.KNOCK })
        for ((e, t) in ev.zip(times)) assertEquals(2.5 + t, e.t, 0.02)
        assertTrue(ev.all { it.aboveFloorDb > 25 })
        val p = Patterns.analyze(ev)
        assertEquals(3, p.repeatedSize)
        assertEquals(3, p.repeats)
        assertTrue(p.looksDeliberate)
    }

    @Test fun voiceLikeAndHumAreTold() {
        val x = noise(2.5 + 1.2 + 1.0 + 1.2 + 0.5)
        val voice = FloatArray((1.2 * fs).toInt()) { i ->
            val t = i.toDouble() / fs
            val env = 0.3 + 0.7 * 0.5 * (1 + sin(2 * PI * 4 * t))
            var v = 0.0
            for (h in 1..20) v += 0.02 * sin(2 * PI * 150.0 * h * t)
            (env * v).toFloat()
        }
        val hum = FloatArray((1.2 * fs).toInt()) { i -> (0.1 * sin(2 * PI * 100.0 * i / fs)).toFloat() }
        mix(x, voice, 2.5)
        mix(x, hum, 4.7)
        val ev = run(SoundEventDetector(fs), x)
        assertEquals(2, ev.size)
        assertEquals(ListenKind.VOICE_LIKE, ev[0].kind)
        assertTrue(ev[0].voiceScore >= 0.5)
        assertEquals(ListenKind.SOUND, ev[1].kind)
        assertTrue(ev[1].voiceScore < 0.3)
        assertEquals(2.5, ev[0].t, 0.03)
        assertEquals(4.7, ev[1].t, 0.03)
    }

    @Test fun vibrationNeedsCalibrationAndRespectsRefractory() {
        val d = VibrationDetector()
        val r = Random(3)
        val events = ArrayList<ListenEvent>()
        var t = 0.0
        while (t < 4.0) {
            val spike = (kotlin.math.abs(t - 3.0) < 1e-6 || kotlin.math.abs(t - 3.05) < 1e-6 || kotlin.math.abs(t - 3.5) < 1e-6)
            val m = 9.81 + (r.nextDouble() - 0.5) * 0.02 + if (spike) 0.8 else 0.0
            d.feed(t, m)?.let { events += it }
            t = Math.round((t + 0.01) * 100) / 100.0
        }
        assertTrue(d.calibrated)
        assertEquals(2, events.size)
        assertTrue(events.all { it.kind == ListenKind.VIBRATION })
        assertEquals(3.0, events[0].t, 1e-6)
        assertEquals(3.5, events[1].t, 1e-6)
    }

    @Test fun patternsRegularRandomAndDuplicates() {
        fun k(t: Double, kind: ListenKind = ListenKind.KNOCK) = ListenEvent(t, kind, 0.05, -20.0, 30.0)
        val regular = Patterns.analyze(listOf(0.0, 1.5, 3.0, 4.5, 6.0).map { k(it) })
        assertTrue(regular.regular)
        assertEquals(1.5, regular.intervalS!!, 1e-9)
        assertNull(regular.repeatedSize)
        val random = Patterns.analyze(listOf(0.0, 0.2, 2.0, 5.5, 5.9, 6.3, 9.0).map { k(it) })
        assertFalse(random.looksDeliberate)
        // Mikrofon + ivmeölçer aynı vuruşu duyar: tek sayılır.
        val dup = Patterns.analyze(listOf(k(1.0), k(1.05, ListenKind.VIBRATION), k(2.0), k(2.04, ListenKind.VIBRATION)))
        assertEquals(2, dup.times.size)
        // Ses benzeri olaylar vuruş desenine karışmaz.
        assertEquals(0, Patterns.analyze(listOf(k(1.0, ListenKind.VOICE_LIKE))).times.size)
    }

    private fun rec(id: String, start: Long, events: List<ListenEvent>, pos: ListenPosition? = null, drill: DrillSource? = null) =
        ListenRecord(id = id, label = "Dinleyici $id", startEpochMs = start, durationS = 30.0, fs = fs, position = pos, events = events, drillSource = drill, drill = true)

    @Test fun codecRoundTripAndRejects() {
        val r = rec("a1b2c3d4e5", 1_700_000_000_000, listOf(ListenEvent(1.25, ListenKind.KNOCK, 0.05, -18.5, 31.0)), ListenPosition(1.0, 2.0))
        val back = ListenCodec.decode(ListenCodec.encode(r))
        assertEquals(r, back)
        assertTrue(ListenCodec.fileName(r).endsWith(".nexlisten.json"))
        assertFalse(ListenCodec.fileName(r).contains(" "))
        fun rejects(text: String) = try { ListenCodec.decode(text); fail() } catch (_: ListenFormatException) { }
        rejects("{}")
        rejects("not json")
        rejects(ListenCodec.encode(r.copy(version = 2)))
        rejects(ListenCodec.encode(r.copy(format = "nexsense")))
        rejects(ListenCodec.encode(r.copy(events = listOf(ListenEvent(99.0, ListenKind.KNOCK, 0.05, -18.5, 31.0)))))
        rejects(ListenCodec.encode(r.copy(events = listOf(ListenEvent(1.0, ListenKind.KNOCK, 0.05, -18.5, 31.0, voiceScore = 2.0)))))
    }

    @Test fun clockOffsetIsRecoveredFromEventTrains() {
        val trueT = listOf(1.0, 2.3, 4.0, 5.1, 7.0)
        fun ev(t: Double) = ListenEvent(t, ListenKind.KNOCK, 0.05, -20.0, 30.0)
        val a = rec("A", 1_000_000, trueT.map { ev(it) })
        // B gerçekte 0,5 sn sonra başladı ama saati 1,3 sn ileri: 1,8 sn geç başlamış görünüyor.
        val b = rec("B", 1_001_800, (trueT.drop(1).map { it - 0.5 } + 3.3).map { ev(it) })
        val al = ClockAlign.offsets(listOf(a, b))
        assertEquals(0.0, al[0].offsetS, 1e-9)
        assertTrue(al[1].aligned)
        assertEquals(-1.3, al[1].offsetS, 0.01)
        // Ortak olay yoksa kayma uydurulmaz.
        val c = rec("C", 1_000_000, listOf(ev(20.0)))
        assertFalse(ClockAlign.offsets(listOf(a, c))[1].aligned)
    }

    private fun levelAt(src: Vec3, p: Vec3, l0: Double = 70.0) = l0 - 20 * log10(maxOf(0.5, hypot(p.x - src.x, p.y - src.y)))

    @Test fun amplitudeLocatesSourceAndShowsUncertainty() {
        val src = Vec3(2.0, 3.0, 0.0)
        val ls = listOf(Vec3(0.0, 0.0, 0.0), Vec3(6.0, 0.0, 0.0), Vec3(0.0, 6.0, 0.0), Vec3(6.0, 6.0, 0.0))
        val groups = listOf(70.0, 64.0).map { l0 -> ls.map { it to levelAt(src, it, l0) } }
        val e = Fusion.locate(groups)!!
        assertTrue("hata: ${e.best}", hypot(e.best.x - src.x, e.best.y - src.y) < 0.4)
        assertEquals(4, e.listeners)
        assertEquals(2, e.groups)
        assertEquals(70.0, e.sourceLevelDb, 3.5)
        // Üç dinleyici: daha belirsiz, ama gerçek nokta bölgede.
        val e3 = Fusion.locate(listOf(ls.take(3).map { it to levelAt(src, it) }))!!
        assertTrue(e3.region.any { hypot(it.x - src.x, it.y - src.y) < 0.4 })
        assertTrue(e3.regionAreaM2 >= e.regionAreaM2)
        // İki dinleyiciyle konum verilmez.
        assertNull(Fusion.locate(listOf(ls.take(2).map { it to 60.0 })))
    }

    @Test fun drillEndToEndAlignsCalibratesAndScores() {
        val src = Vec3(2.0, 3.0, 0.0)
        val (_, times) = DrillSignal.pattern(fs)
        val positions = listOf(Vec3(0.0, 0.0, 0.0), Vec3(6.0, 0.0, 0.0), Vec3(0.0, 6.0, 0.0), Vec3(6.0, 6.0, 0.0))
        val bias = listOf(0.0, 0.0, 5.0, 0.0)
        val clock = listOf(0L, 0L, 700L, 0L)
        val start = 1_700_000_000_000
        val listeners = positions.indices.map { i ->
            val events = times.map { t -> ListenEvent(t + 1.0, ListenKind.KNOCK, 0.05, levelAt(src, positions[i], -10.0) + bias[i], 30.0) }
            rec("L$i", start + clock[i], events, ListenPosition(positions[i].x, positions[i].y))
        }
        val source = rec("S", start, emptyList(), null, DrillSource(src.x, src.y, emitT = times.map { it + 1.0 }))
        val r = Fusion.run(listeners + source)
        assertTrue(r.isDrill)
        assertEquals(-0.7, r.alignments[2].offsetS, 0.02)
        assertEquals(9, r.groups.size)
        assertTrue(r.groups.all { it.members.size == 4 })
        assertEquals(-5.0, r.gainsDb[2]!! - r.gainsDb[0]!!, 0.3)
        val err = r.drill!!.errorM!!
        assertTrue("tatbikat hatası $err m", err < 0.6)
        assertEquals(9, r.drill!!.emitted)
        assertEquals(0, r.loudestRecord) // kaynağa en yakın (2,8 m) dinleyici
    }
}
