package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.guide.Capture
import com.eirahoutmoss.nexsense.core.guide.CaptureKind
import com.eirahoutmoss.nexsense.core.guide.Dir
import com.eirahoutmoss.nexsense.core.guide.GuideEngine
import com.eirahoutmoss.nexsense.core.guide.GuideFrame
import com.eirahoutmoss.nexsense.core.guide.GuideSignals
import com.eirahoutmoss.nexsense.core.guide.GuideStep
import com.eirahoutmoss.nexsense.core.guide.Hint
import com.eirahoutmoss.nexsense.core.guide.Mission
import com.eirahoutmoss.nexsense.core.guide.Missions
import com.eirahoutmoss.nexsense.core.guide.Need
import com.eirahoutmoss.nexsense.core.guide.Quality
import com.eirahoutmoss.nexsense.core.guide.StepOutcome
import com.eirahoutmoss.nexsense.core.scene.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GuideTest {

    private val ALL = Need.entries.toSet()
    private fun one(step: GuideStep) = GuideEngine(Mission("t", listOf(step), 1), ALL)

    /** 10 Hz sinyal üreteci. */
    private class Sim(val e: GuideEngine) {
        var t = 0.0
        val captures = ArrayList<Capture>()
        val hints = ArrayList<Hint?>()
        fun tick(s: (Double) -> GuideSignals): GuideFrame {
            val f = e.update(s(t))
            captures += f.captures
            hints += f.hint
            t += 0.1
            return f
        }
        fun run(seconds: Double, s: (Double) -> GuideSignals) { repeat((seconds * 10).toInt()) { tick(s) } }
    }

    private fun still(t: Double, pos: Vec3? = Vec3.ZERO, h: Double? = 1.0) =
        GuideSignals(t, tracking = pos != null, position = pos, facing = Vec3(0.0, 1.0, 0.0), heightM = h, pitchDeg = 0.0, rollDeg = 0.0,
            azimuthDeg = 0.0, accelMagnitude = 9.81 + 0.01 * sin(t * 7))

    @Test fun holdStillWaitsForCalmThenCapturesBaseline() {
        val sim = Sim(one(GuideStep.HoldStill("s", 3.0)))
        sim.run(1.0) { t -> still(t).copy(accelMagnitude = if ((t * 10).toInt() % 2 == 0) 8.0 else 12.0) }
        assertEquals(Hint.MOVE_LESS, sim.hints.last())
        assertTrue(sim.captures.isEmpty())
        sim.run(5.0) { still(it) } // ~1 sn pencere temizlenir + 3 sn durgunluk
        assertTrue(sim.e.finished)
        assertEquals(CaptureKind.BASELINE, sim.captures.single().kind)
    }

    @Test fun figure8NeedsAllDirectionsAndTilts() {
        val sim = Sim(one(GuideStep.Figure8("f")))
        val f0 = sim.tick { still(it) }
        assertEquals(Hint.TILT_UP, f0.hint)
        var az = 0.0
        sim.run(4.0) { t -> az += 10.0; still(t).copy(azimuthDeg = az % 360, pitchDeg = if (t < 2) -40.0 else 40.0) }
        assertTrue(sim.e.finished)
    }

    @Test fun heightGuidesUpAndDownAndCapturesWhenHeld() {
        val sim = Sim(one(GuideStep.Height("low", -0.5)))
        sim.run(0.5) { still(it, h = 1.0) }
        assertEquals(Hint.LOWER, sim.hints.last())
        sim.run(0.3) { still(it, h = 0.2) }
        assertEquals(Hint.RAISE, sim.hints.last())
        sim.run(1.5) { still(it, h = 0.52) }
        assertTrue(sim.e.finished)
        assertEquals(CaptureKind.POINT, sim.captures.single().kind)
        assertFalse(sim.captures.single().estimated)
    }

    @Test fun moveForwardCapturesEveryHalfMetreAndCorrectsDrift() {
        val sim = Sim(one(GuideStep.Move("m", Dir.FORWARD, 2.0, 0.5)))
        sim.tick { still(it) }
        sim.tick { t -> still(t, Vec3(0.0, -0.5, 1.0)) }
        assertEquals(Hint.WRONG_WAY, sim.hints.last())
        sim.tick { t -> still(t, Vec3(0.6, 0.5, 1.0)) }
        assertEquals("sağa kaydı → sola gel", Hint.DRIFT_LEFT, sim.hints.last())
        var y = 0.5
        sim.run(5.0) { t -> y += 0.05; still(t, Vec3(0.0, y, 1.0)) }
        assertTrue(sim.e.finished)
        assertEquals(5, sim.captures.size) // 0; 0,5; 1; 1,5; 2 m
    }

    @Test fun sidestepRightUsesFacingDirection() {
        val sim = Sim(one(GuideStep.Move("r", Dir.RIGHT, 1.0, 0.5)))
        var x = 0.0
        sim.run(3.0) { t -> x += 0.05; still(t, Vec3(x, 0.0, 1.0)) } // yüz +y, sağ = +x
        assertTrue(sim.e.finished)
    }

    @Test fun returnPointsTowardsStart() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.HoldStill("s", 0.3), GuideStep.Return("r")), 1), ALL)
        val sim = Sim(e)
        var guard = 0
        while (e.current !is GuideStep.Return && guard++ < 50) sim.tick { still(it) } // başlangıç (0,0)
        assertTrue(e.current is GuideStep.Return)
        sim.tick { t -> still(t, Vec3(0.0, 2.0, 1.0)) } // yüz +y, hedef tam arkada: iki dönüş de doğru
        assertTrue(sim.hints.last() == Hint.TURN_LEFT || sim.hints.last() == Hint.TURN_RIGHT)
        sim.tick { t -> still(t, Vec3(0.0, 2.0, 1.0)).copy(facing = Vec3(1.0, 0.0, 0.0)) } // yüz +x, hedef -y → sağa dön
        assertEquals(Hint.TURN_RIGHT, sim.hints.last())
        sim.tick { t -> still(t, Vec3(0.0, 2.0, 1.0)).copy(facing = Vec3(0.0, -1.0, 0.0)) }
        assertEquals(Hint.KEEP_GOING, sim.hints.last())
        sim.tick { t -> still(t, Vec3(0.0, 0.2, 1.0)).copy(facing = Vec3(0.0, -1.0, 0.0)) }
        assertTrue(e.finished)
    }

    @Test fun turnCapturesEveryQuarterAndWarnsWhenTooFast() {
        val sim = Sim(one(GuideStep.Turn("t", 360.0, 90.0)))
        var yaw = 0.0
        sim.run(1.0) { t -> yaw += 30.0; still(t).copy(facing = dir(yaw)) } // 300°/s
        assertTrue(Hint.TURN_SLOWER in sim.hints)
        sim.run(10.0) { t -> yaw += 8.0; still(t).copy(facing = dir(yaw)) }
        assertTrue(sim.e.finished)
        assertEquals(4, sim.captures.size) // 0, 90, 180, 270
    }

    private fun dir(deg: Double) = Vec3(cos(Math.toRadians(deg)), sin(Math.toRadians(deg)), 0.0)

    @Test fun markRequiresDistanceAndAPause() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.Mark("c1"), GuideStep.Mark("c2")), 1), ALL)
        val sim = Sim(e)
        sim.run(0.5) { still(it) }
        assertEquals(Hint.MOVE_AWAY, sim.hints.last())
        sim.run(2.5) { t -> still(t, Vec3(3.0, 0.0, 1.0)) }
        assertTrue(e.current is GuideStep.Mark && e.current!!.id == "c2")
        sim.run(0.5) { t -> still(t, Vec3(3.2, 0.0, 1.0)) }
        assertEquals("ilk köşeye çok yakın", Hint.MOVE_AWAY, sim.hints.last())
        sim.run(2.5) { t -> still(t, Vec3(3.0, 4.0, 1.0)) }
        assertTrue(e.finished)
        assertEquals(2, sim.captures.size)
    }

    @Test fun placeFlatTriggersAcousticAndWaitsForApp() {
        val sim = Sim(one(GuideStep.PlaceFlat("p")))
        sim.run(0.5) { t -> still(t).copy(pitchDeg = 50.0) }
        assertEquals(Hint.LAY_FLAT, sim.hints.last())
        sim.run(3.0) { still(it) }
        assertEquals(CaptureKind.ACOUSTIC_SUITE, sim.captures.single().kind)
        assertEquals(Hint.WORKING, sim.hints.last())
        assertFalse(sim.e.finished)
        sim.e.completeExternal(true)
        assertTrue(sim.e.finished)
        assertEquals(StepOutcome.AUTO, sim.e.summary().outcomes.single().second)
    }

    @Test fun missingSensorsFallBackToHonestManualConfirm() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.Height("high", 0.5), GuideStep.Move("m", Dir.FORWARD, 1.0)), 1), setOf(Need.MOTION, Need.ORIENTATION))
        val f = e.update(still(0.0, pos = null, h = null))
        assertTrue(f.manual)
        assertEquals(Hint.NEED_CONFIRM, f.hint)
        val caps = e.confirm()
        assertTrue("varsayılan değer tahmini işaretlenmeli", caps.single().estimated)
        e.skip()
        assertTrue(e.finished)
        val sum = e.summary()
        assertEquals(1, sum.manual); assertEquals(1, sum.skipped)
        assertEquals(Quality.FAIR, sum.quality)
    }

    @Test fun qualityAndTrackingLosses() {
        val e = GuideEngine(Mission("t", listOf(GuideStep.HoldStill("s", 0.5)), 1), ALL)
        val sim = Sim(e)
        sim.tick { still(it) }
        sim.tick { t -> still(t, pos = null) } // izleme kaybı
        sim.run(1.0) { still(it) }
        val sum = e.summary()
        assertEquals(1, sum.trackingLosses)
        assertEquals(Quality.GOOD, sum.quality)
        val bad = GuideEngine(Missions.FIRST, ALL)
        repeat(Missions.FIRST.steps.size) { bad.skip() }
        assertEquals(Quality.POOR, bad.summary().quality)
    }

    @Test fun missionsAreWellFormed() {
        for (m in Missions.ALL) {
            assertTrue(m.minutes > 0)
            assertEquals(m.id, m.steps.size, m.steps.map { it.id }.toSet().size)
            assertEquals(m, Missions.byId(m.id))
        }
        assertNull(Missions.byId("yok"))
        assertTrue(Need.POSITION in Missions.ROOM.needs)
    }
}
