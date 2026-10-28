package com.eirahoutmoss.nexsense.core.guide

import com.eirahoutmoss.nexsense.core.scene.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rehberli Keşif: kullanıcıya tek tek eylem yaptırır ("yukarı kaldır", "3 adım ileri", "yavaşça dön"),
 * sensörlerle eylemin gerçekten yapıldığını denetler, doğru anda veriyi kendisi kaydettirir.
 *
 * Tasarım ilkeleri (docs/REHBER.md):
 *  - Bir anda tek yönerge; anlık geri bildirim (ipucu); her adımın "neden"i açıklanır.
 *  - Kullanıcı her an atlayabilir ya da bitirebilir; başarısızlık suçlanmaz, kalite dürüstçe raporlanır.
 *  - Gereken sensör yoksa adım "elle onay"a düşer ve kaydedilen değer "tahmini" işaretlenir; sahte veri üretilmez.
 */

/** Bir anlık sensör durumu. Bilinmeyen alan null. */
data class GuideSignals(
    /** Görev başından beri saniye. */
    val tS: Double,
    /** Konum kaynağı şu an güvenilir mi (ARCore izliyor / adım modu çalışıyor). */
    val tracking: Boolean = false,
    /** Proje çerçevesinde konum (m). */
    val position: Vec3? = null,
    /** Yatay bakış yönü, konumla aynı çerçevede (birim vektör, z = 0). */
    val facing: Vec3? = null,
    /** Göreli yükseklik (m): ARCore z ya da barometreden. Yoksa null. */
    val heightM: Double? = null,
    val pitchDeg: Double? = null,
    val rollDeg: Double? = null,
    /** Pusula yönü (kuzeyden saat yönünde). */
    val azimuthDeg: Double? = null,
    /** İvme büyüklüğü (m/s², yerçekimi dahil) — durgunluk ölçümü için. */
    val accelMagnitude: Double? = null,
    /** Manyetometre doğruluk durumu: high / medium / low / unreliable. */
    val magAccuracy: String? = null,
    /** (keşif) Uygulamanın haritadan seçtiği bir sonraki sınır; yoksa null. */
    val frontier: Vec3? = null,
    /** (keşif) Haritada gidilecek sınır kalmadı. */
    val frontierExhausted: Boolean = false,
)

/** Bir adımın ihtiyaç duyduğu veri. */
enum class Need { MOTION, ORIENTATION, HEIGHT, POSITION, ACOUSTIC }

enum class Dir(val deg: Double) { FORWARD(0.0), LEFT(90.0), RIGHT(-90.0) }

/** Görev adımları. Metinleri uygulamada [id] ve türe göre yerelleştirilir. */
sealed interface GuideStep {
    val id: String
    val needs: Set<Need>

    /** Telefonu kıpırdatmadan tut: ortam taban değerleri. */
    data class HoldStill(override val id: String, val seconds: Double = 3.0) : GuideStep {
        override val needs = setOf(Need.MOTION)
    }

    /** Havada 8 çizdir: manyetometre kalibrasyonu (tüm yönler ve eğimler). */
    data class Figure8(override val id: String, val sectors: Int = 8) : GuideStep {
        override val needs = setOf(Need.ORIENTATION)
    }

    /** Başlangıç yüksekliğine göre [relM] metrede tut, ölçüm al. */
    data class Height(override val id: String, val relM: Double, val tolM: Double = 0.12) : GuideStep {
        override val needs = setOf(Need.HEIGHT)
    }

    /** [dir] yönünde [distM] metre yürü; her [everyM] metrede ölçüm. */
    data class Move(override val id: String, val dir: Dir, val distM: Double, val everyM: Double = 0.5) : GuideStep {
        override val needs = setOf(Need.POSITION)
    }

    /** Görevin başladığı yere dön. */
    data class Return(override val id: String, val tolM: Double = 0.4) : GuideStep {
        override val needs = setOf(Need.POSITION)
    }

    /** Olduğun yerde yavaşça dön; her [captureEveryDeg] derecede ölçüm. */
    data class Turn(override val id: String, val degrees: Double = 360.0, val captureEveryDeg: Double = 90.0) : GuideStep {
        override val needs = setOf(Need.ORIENTATION)
    }

    /** Bir yere (ör. köşe) git ve dur; durduğun yer işaretlenir. */
    data class Mark(override val id: String, val minSeparationM: Double = 0.8, val holdS: Double = 2.0) : GuideStep {
        override val needs = setOf(Need.POSITION, Need.MOTION)
    }

    /** Akustik radar: olduğun yerde yavaşça dön; her ölçülmemiş yönde kısa durunca sonar alınır. */
    data class RadarSweep(override val id: String, val sectors: Int = 12, val holdS: Double = 0.6) : GuideStep {
        override val needs = setOf(Need.ORIENTATION, Need.ACOUSTIC)
    }

    /** Haritadaki bir sonraki keşif sınırına git (boş ile bilinmeyenin buluştuğu yer). Sınır kalmadıysa görev biter. */
    data class Frontier(override val id: String, val tolM: Double = 0.6) : GuideStep {
        override val needs = setOf(Need.POSITION)
    }

    /** Telefonu düz bir yüzeye bırak; durunca akustik ölçümler başlar (uygulama tamamlar). */
    data class PlaceFlat(override val id: String, val holdS: Double = 2.0) : GuideStep {
        override val needs = setOf(Need.ORIENTATION, Need.MOTION)
    }
}

/** Anlık yönlendirme. Metni ve oku uygulama seçer. */
enum class Hint {
    HOLD_STILL, MOVE_LESS, RAISE, LOWER, HOLD_HERE, KEEP_GOING, SLOW_DOWN, WRONG_WAY, DRIFT_LEFT, DRIFT_RIGHT,
    TURN_LEFT, TURN_RIGHT, TURN_SLOWER, TILT_UP, TILT_DOWN, LAY_FLAT, MOVE_AWAY, WORKING, LOST_TRACKING, NEED_CONFIRM,
}

enum class CaptureKind {
    /** Ölçüm noktası (konum + o anki sensör değerleri). */
    POINT,

    /** Dinlenme/taban ölçümü (konumlu nokta, "taban" etiketiyle). */
    BASELINE,

    /** Akustik ölçümleri başlat (oda yankısı, sonar, ses düzeyi); uygulama bitince [GuideEngine.completeExternal]. */
    ACOUSTIC_SUITE,

    /** Tek yönde sonar (radar dilimi); uygulama bitince [GuideEngine.completePing]. [Capture.yawDeg] yönü verir. */
    ACOUSTIC_PING,
}

/** Uygulamanın kaydetmesi istenen veri. [estimated]: konum ölçülmedi, adımın hedefinden varsayıldı. */
data class Capture(
    val kind: CaptureKind,
    val stepId: String,
    val index: Int,
    val position: Vec3?,
    val estimated: Boolean,
    /** (radar) Ölçümün yönü, proje çerçevesinde atan2(y, x) derece. */
    val yawDeg: Double? = null,
)

enum class StepOutcome { AUTO, MANUAL, SKIPPED }

data class GuideFrame(
    val stepIndex: Int,
    val step: GuideStep?,
    val progress: Double,
    val hint: Hint?,
    /** Bu adım gerekli sensör olmadığı için elle onay bekliyor. */
    val manual: Boolean,
    val captures: List<Capture>,
    val finished: Boolean,
)

enum class Quality { GOOD, FAIR, POOR }

data class MissionSummary(
    val outcomes: List<Pair<String, StepOutcome>>,
    val captures: Int,
    val trackingLosses: Int,
    val quality: Quality,
) {
    val auto: Int get() = outcomes.count { it.second == StepOutcome.AUTO }
    val manual: Int get() = outcomes.count { it.second == StepOutcome.MANUAL }
    val skipped: Int get() = outcomes.count { it.second == StepOutcome.SKIPPED }
}

data class Mission(val id: String, val steps: List<GuideStep>, val minutes: Int) {
    val needs: Set<Need> get() = steps.flatMap { it.needs }.toSet()
}

/** Hazır görevler. Kısa tutuldu (1–4 dk): dikkat süresi ve "bir şey başardım" duygusu için. */
object Missions {
    val FIRST = Mission(
        "first",
        listOf(
            GuideStep.HoldStill("still"),
            GuideStep.Figure8("figure8"),
            GuideStep.Height("low", -0.5),
            GuideStep.Height("mid", 0.0),
            GuideStep.Height("high", 0.5),
            GuideStep.Turn("turn"),
            GuideStep.Move("forward", Dir.FORWARD, 2.0, 0.5),
            GuideStep.Return("return"),
        ),
        minutes = 3,
    )

    val ROOM = Mission(
        "room",
        listOf(
            GuideStep.HoldStill("still", 2.0),
            GuideStep.Turn("turn", 360.0, 120.0),
            GuideStep.Mark("corner1"),
            GuideStep.Mark("corner2"),
            GuideStep.Mark("corner3"),
            GuideStep.Mark("corner4"),
            GuideStep.Return("return"),
        ),
        minutes = 3,
    )

    val MAGNETIC = Mission(
        "magnetic",
        listOf(
            GuideStep.Figure8("figure8"),
            GuideStep.Height("mid", 0.0),
            GuideStep.Move("wall_right", Dir.RIGHT, 2.0, 0.25),
            GuideStep.Height("low", -0.6),
            GuideStep.Move("wall_left", Dir.LEFT, 2.0, 0.25),
        ),
        minutes = 2,
    )

    val SOUND = Mission(
        "sound",
        listOf(
            GuideStep.HoldStill("still", 2.0),
            GuideStep.PlaceFlat("place"),
        ),
        minutes = 1,
    )

    /** Akustik radar: tek noktada 360° çevre profili. Karanlıkta ve tozda da çalışır. */
    val RADAR = Mission(
        "radar",
        listOf(GuideStep.HoldStill("still", 2.0), GuideStep.RadarSweep("radar")),
        minutes = 2,
    )

    /**
     * Serbest keşif: oda/köşe varsayımı yok. Radar → en umut verici sınıra git → radar … Sınır kalmayınca biter.
     * Mağara, tünel, düzensiz boşluklar için.
     */
    val EXPLORE = Mission(
        "explore",
        listOf(GuideStep.HoldStill("still", 2.0)) + (1..5).flatMap { k ->
            listOf(GuideStep.RadarSweep("radar$k"), GuideStep.Frontier("go$k"))
        },
        minutes = 8,
    )

    val ALL = listOf(FIRST, EXPLORE, RADAR, ROOM, MAGNETIC, SOUND)

    fun byId(id: String): Mission? = ALL.firstOrNull { it.id == id }
}

/** Son ~1 sn içindeki ivme büyüklüğü aralığına göre durgunluk. */
class StillnessMeter(private val windowS: Double = 1.0, private val maxRangeMs2: Double = 0.4) {
    private val buf = ArrayDeque<Pair<Double, Double>>()

    fun add(t: Double, mag: Double) {
        buf.addLast(t to mag)
        while (buf.isNotEmpty() && t - buf.first().first > windowS) buf.removeFirst()
    }

    /** En az 5 örnek ve aralık eşik altındaysa durgun. Bilgi yoksa null. */
    fun isStill(): Boolean? {
        if (buf.size < 5) return null
        val v = buf.map { it.second }
        return v.max() - v.min() < maxRangeMs2
    }

    /** Daha gevşek: el titremesini kabul eder, yürümeyi etmez. */
    fun isSteady(): Boolean? {
        if (buf.size < 5) return null
        val v = buf.map { it.second }
        return v.max() - v.min() < maxRangeMs2 * 4
    }
}

/**
 * Görev motoru. Uygulama ~10 Hz [update] çağırır; motor ilerlemeyi, ipucunu ve kaydedilecek veriyi döndürür.
 * Saf mantıktır (Android'e bağımlı değil), birim testleriyle doğrulanır.
 */
class GuideEngine(val mission: Mission, private val available: Set<Need>) {

    var index = 0
        private set
    private val outcomes = ArrayList<Pair<String, StepOutcome>>()
    private var captureCount = 0
    private var trackingLosses = 0
    private var wasTracking = true
    private val still = StillnessMeter()
    private var last: GuideSignals? = null
    private var dt = 0.0

    // Görev başlangıcı
    private var startPos: Vec3? = null
    private var startHeight: Double? = null

    // Adım durumu
    private var stepStartT = 0.0
    private var acc = 0.0
    private var sectorMask = 0L
    private var pitchUp = false
    private var pitchDown = false
    private var moveOrigin: Vec3? = null
    private var moveDir: Vec3? = null
    private var nextCaptureAt = 0.0
    private var lastYaw: Double? = null
    private var turned = 0.0
    private var nextTurnCapture = 0.0
    private var waitingExternal = false
    private var radar: com.eirahoutmoss.nexsense.core.spatial.RadarSweep? = null
    private var pingPending = false

    /** Şu anki radar adımının durumu (radar ekranı için). */
    val radarSweep: com.eirahoutmoss.nexsense.core.spatial.RadarSweep? get() = radar
    private val marks = ArrayList<Vec3>()
    private var stepCaptureIndex = 0

    val finished: Boolean get() = index >= mission.steps.size
    val current: GuideStep? get() = mission.steps.getOrNull(index)

    /** Adım gerekli veriyi alamıyorsa elle onay ister. */
    fun isManual(step: GuideStep? = current): Boolean = step != null && !available.containsAll(step.needs)

    fun update(s: GuideSignals): GuideFrame {
        dt = last?.let { (s.tS - it.tS).coerceIn(0.0, 1.0) } ?: 0.0
        last = s
        s.accelMagnitude?.let { still.add(s.tS, it) }
        if (startPos == null && s.tracking && s.position != null) startPos = s.position
        if (startHeight == null && s.heightM != null) startHeight = s.heightM
        if (wasTracking && !s.tracking && Need.POSITION in available) trackingLosses++
        wasTracking = s.tracking

        val step = current ?: return frame(1.0, null, emptyList())
        if (isManual(step)) return frame(0.0, Hint.NEED_CONFIRM, emptyList())
        val caps = ArrayList<Capture>()
        val (progress, hint, done) = when (step) {
            is GuideStep.HoldStill -> holdStill(step, s, caps)
            is GuideStep.Figure8 -> figure8(step, s)
            is GuideStep.Height -> height(step, s, caps)
            is GuideStep.Move -> move(step, s, caps)
            is GuideStep.Return -> returnTo(step, s)
            is GuideStep.Turn -> turn(step, s, caps)
            is GuideStep.Mark -> mark(step, s, caps)
            is GuideStep.PlaceFlat -> placeFlat(step, s, caps)
            is GuideStep.RadarSweep -> radarStep(step, s, caps)
            is GuideStep.Frontier -> frontierStep(step, s)
        }
        if (done) {
            val exhausted = step is GuideStep.Frontier && s.frontierExhausted
            advance(StepOutcome.AUTO)
            // Keşfedilecek sınır kalmadı: kalan radar/sınır adımları gereksiz, görev biter.
            if (exhausted) index = mission.steps.size
        }
        return frame(if (done) 0.0 else progress, if (done) null else hint, caps)
    }

    /** Elle onay: gerekli sensör yoksa ya da kullanıcı "yaptım" dediğinde. Varsayılan konumla kayıt üretir. */
    fun confirm(): List<Capture> {
        val step = current ?: return emptyList()
        val s = last
        val caps = ArrayList<Capture>()
        when (step) {
            is GuideStep.Height -> {
                val p = s?.position?.let { Vec3(it.x, it.y, (startPos?.z ?: it.z) + step.relM) }
                caps += cap(CaptureKind.POINT, step, p, estimated = true)
            }
            is GuideStep.Mark, is GuideStep.HoldStill -> caps += cap(
                if (step is GuideStep.HoldStill) CaptureKind.BASELINE else CaptureKind.POINT, step, s?.position, estimated = s?.tracking != true,
            )
            is GuideStep.PlaceFlat -> {
                if (!waitingExternal) {
                    waitingExternal = true
                    caps += cap(CaptureKind.ACOUSTIC_SUITE, step, s?.position, estimated = s?.tracking != true)
                    return caps // akustik bitince completeExternal
                }
            }
            else -> Unit
        }
        advance(StepOutcome.MANUAL)
        return caps
    }

    fun skip() {
        if (current != null) advance(StepOutcome.SKIPPED)
    }

    /** Uygulamanın yürüttüğü iş (akustik) bitti. */
    /** Radar dilimi ölçüldü ([reading] null = ölçüm başarısız; o yön "denendi" sayılır, ışın üretilmez). */
    fun completePing(yawDeg: Double, reading: com.eirahoutmoss.nexsense.core.spatial.RadarReading?) {
        val sw = radar ?: return
        if (!pingPending) return
        pingPending = false
        sw.add(reading ?: com.eirahoutmoss.nexsense.core.spatial.RadarReading(yawDeg, emptyList(), 0.0))
    }

    fun completeExternal(success: Boolean) {
        if (current is GuideStep.PlaceFlat && waitingExternal) advance(if (success) StepOutcome.AUTO else StepOutcome.SKIPPED)
    }

    fun summary(): MissionSummary {
        val n = outcomes.size.coerceAtLeast(1)
        val autoRatio = outcomes.count { it.second == StepOutcome.AUTO }.toDouble() / n
        val skippedRatio = outcomes.count { it.second == StepOutcome.SKIPPED }.toDouble() / n
        val q = when {
            autoRatio >= 0.75 && trackingLosses <= 2 -> Quality.GOOD
            skippedRatio <= 0.5 -> Quality.FAIR
            else -> Quality.POOR
        }
        return MissionSummary(outcomes.toList(), captureCount, trackingLosses, q)
    }

    // ---------------- adımlar ----------------

    private data class Eval(val progress: Double, val hint: Hint?, val done: Boolean)

    private fun holdStill(st: GuideStep.HoldStill, s: GuideSignals, caps: MutableList<Capture>): Eval {
        val st0 = still.isStill()
        if (st0 == true) acc += dt else acc = 0.0
        if (acc >= st.seconds) {
            caps += cap(CaptureKind.BASELINE, st, s.position, estimated = !s.tracking)
            return Eval(1.0, null, true)
        }
        return Eval(acc / st.seconds, if (st0 == false) Hint.MOVE_LESS else Hint.HOLD_STILL, false)
    }

    private fun figure8(st: GuideStep.Figure8, s: GuideSignals): Eval {
        val az = s.azimuthDeg
        val pitch = s.pitchDeg
        if (az != null) {
            val k = (floor(((az % 360.0) + 360.0) % 360.0 / (360.0 / st.sectors))).toInt().coerceIn(0, st.sectors - 1)
            sectorMask = sectorMask or (1L shl k)
        }
        if (pitch != null) {
            if (pitch < -35) pitchUp = true
            if (pitch > 35) pitchDown = true
        }
        val covered = java.lang.Long.bitCount(sectorMask) + (if (pitchUp) 1 else 0) + (if (pitchDown) 1 else 0)
        val progress = covered.toDouble() / (st.sectors + 2)
        val calibrated = s.magAccuracy == "high" && progress >= 0.5 && s.tS - stepStartT >= 5.0
        if (progress >= 1.0 || calibrated) return Eval(1.0, null, true)
        val hint = when {
            !pitchUp -> Hint.TILT_UP
            !pitchDown -> Hint.TILT_DOWN
            else -> Hint.TURN_RIGHT
        }
        return Eval(progress, hint, false)
    }

    private fun height(st: GuideStep.Height, s: GuideSignals, caps: MutableList<Capture>): Eval {
        val h = s.heightM ?: return Eval(0.0, Hint.LOST_TRACKING, false)
        val h0 = startHeight ?: h
        val err = (h - h0) - st.relM
        val span = max(abs(st.relM), 0.3)
        val progress = (1.0 - min(1.0, abs(err) / span)).coerceIn(0.0, 0.99)
        if (abs(err) <= st.tolM) {
            if (still.isSteady() != false) acc += dt else acc = 0.0
            if (acc >= 1.0) {
                caps += cap(CaptureKind.POINT, st, s.position, estimated = !s.tracking)
                return Eval(1.0, null, true)
            }
            return Eval(progress, Hint.HOLD_HERE, false)
        }
        acc = 0.0
        return Eval(progress, if (err < 0) Hint.RAISE else Hint.LOWER, false)
    }

    private fun move(st: GuideStep.Move, s: GuideSignals, caps: MutableList<Capture>): Eval {
        val p = s.position
        if (!s.tracking || p == null) return Eval(acc, Hint.LOST_TRACKING, false)
        if (moveOrigin == null) {
            val f = s.facing ?: return Eval(0.0, Hint.LOST_TRACKING, false)
            moveOrigin = p
            moveDir = rotate(flat(f), st.dir.deg)
            nextCaptureAt = 0.0
        }
        val o = moveOrigin!!
        val d = moveDir!!
        val rel = Vec3(p.x - o.x, p.y - o.y, 0.0)
        val along = rel dot d
        val lateral = d.x * rel.y - d.y * rel.x // + = yönün solunda
        val speed = last2Speed(s)
        while (along >= nextCaptureAt - 1e-9 && nextCaptureAt <= st.distM + 1e-9) {
            caps += cap(CaptureKind.POINT, st, p, estimated = false)
            nextCaptureAt += st.everyM
        }
        acc = (along / st.distM).coerceIn(0.0, 1.0)
        if (along >= st.distM) return Eval(1.0, null, true)
        val hint = when {
            along < -0.3 -> Hint.WRONG_WAY
            speed != null && speed > 1.3 -> Hint.SLOW_DOWN
            lateral > 0.4 -> Hint.DRIFT_RIGHT
            lateral < -0.4 -> Hint.DRIFT_LEFT
            else -> Hint.KEEP_GOING
        }
        return Eval(acc, hint, false)
    }

    private fun returnTo(st: GuideStep.Return, s: GuideSignals): Eval {
        val p = s.position
        val start = startPos
        if (!s.tracking || p == null || start == null) return Eval(0.0, Hint.LOST_TRACKING, false)
        val toTarget = Vec3(start.x - p.x, start.y - p.y, 0.0)
        val dist = toTarget.length
        if (moveOrigin == null) { moveOrigin = p; acc = max(dist, 0.01) }
        val progress = (1.0 - dist / acc).coerceIn(0.0, 1.0)
        if (dist <= st.tolM) return Eval(1.0, null, true)
        val f = s.facing?.let { flat(it) }
        val hint = if (f == null) Hint.KEEP_GOING else {
            val ang = signedAngleDeg(f, toTarget.normalized())
            when {
                ang > 35 -> Hint.TURN_LEFT
                ang < -35 -> Hint.TURN_RIGHT
                else -> Hint.KEEP_GOING
            }
        }
        return Eval(progress, hint, false)
    }

    private fun turn(st: GuideStep.Turn, s: GuideSignals, caps: MutableList<Capture>): Eval {
        // Yön: konum çerçevesindeki bakış; yoksa pusula.
        val yaw = s.facing?.let { Math.toDegrees(atan2(it.y, it.x)) } ?: s.azimuthDeg?.let { -it }
            ?: return Eval(0.0, Hint.LOST_TRACKING, false)
        val ly = lastYaw
        var hint = Hint.TURN_LEFT
        if (ly != null) {
            var d = yaw - ly
            while (d > 180) d -= 360
            while (d < -180) d += 360
            turned += abs(d)
            if (dt > 0 && abs(d) / dt > 120.0) hint = Hint.TURN_SLOWER
        } else {
            caps += cap(CaptureKind.POINT, st, s.position, estimated = !s.tracking)
            nextTurnCapture = st.captureEveryDeg
        }
        lastYaw = yaw
        while (turned >= nextTurnCapture && nextTurnCapture < st.degrees) {
            caps += cap(CaptureKind.POINT, st, s.position, estimated = !s.tracking)
            nextTurnCapture += st.captureEveryDeg
        }
        if (turned >= st.degrees) return Eval(1.0, null, true)
        return Eval(turned / st.degrees, hint, false)
    }

    private fun mark(st: GuideStep.Mark, s: GuideSignals, caps: MutableList<Capture>): Eval {
        val p = s.position
        if (!s.tracking || p == null) return Eval(0.0, Hint.LOST_TRACKING, false)
        val refs = marks + listOfNotNull(startPos)
        val nearest = refs.minOfOrNull { Vec3(it.x - p.x, it.y - p.y, 0.0).length } ?: Double.MAX_VALUE
        if (nearest < st.minSeparationM) { acc = 0.0; return Eval(0.0, Hint.MOVE_AWAY, false) }
        if (still.isSteady() == true) acc += dt else acc = 0.0
        if (acc >= st.holdS) {
            marks += p
            caps += cap(CaptureKind.POINT, st, p, estimated = false)
            return Eval(1.0, null, true)
        }
        return Eval(acc / st.holdS, if (acc > 0) Hint.HOLD_STILL else Hint.KEEP_GOING, false)
    }

    private fun placeFlat(st: GuideStep.PlaceFlat, s: GuideSignals, caps: MutableList<Capture>): Eval {
        if (waitingExternal) return Eval(0.9, Hint.WORKING, false)
        val pitch = s.pitchDeg; val roll = s.rollDeg
        val flat = pitch != null && roll != null && abs(pitch) < 10 && abs(roll) < 10
        if (flat && still.isStill() == true) acc += dt else acc = 0.0
        if (acc >= st.holdS) {
            waitingExternal = true
            caps += cap(CaptureKind.ACOUSTIC_SUITE, st, s.position, estimated = !s.tracking)
            return Eval(0.9, Hint.WORKING, false)
        }
        return Eval(acc / st.holdS * 0.5, if (!flat) Hint.LAY_FLAT else Hint.MOVE_LESS, false)
    }

    private fun radarStep(st: GuideStep.RadarSweep, s: GuideSignals, caps: MutableList<Capture>): Eval {
        val sw = radar ?: com.eirahoutmoss.nexsense.core.spatial.RadarSweep(st.sectors).also { radar = it }
        val progress = sw.measured.toDouble() / st.sectors
        if (sw.complete) return Eval(1.0, null, true)
        if (pingPending) return Eval(progress, Hint.WORKING, false)
        // Konum çerçevesindeki bakış; yoksa pusula (kuzey = +y varsayımıyla).
        val yaw = s.facing?.let { Math.toDegrees(atan2(it.y, it.x)) } ?: s.azimuthDeg?.let { 90.0 - it }
            ?: return Eval(progress, Hint.LOST_TRACKING, false)
        val k = sw.sectorOf(yaw)
        if (!sw.has(k)) {
            if (still.isSteady() == true) acc += dt else acc = 0.0
            if (acc >= st.holdS) {
                acc = 0.0
                pingPending = true
                caps += cap(CaptureKind.ACOUSTIC_PING, st, s.position, estimated = !s.tracking, yawDeg = yaw)
                return Eval(progress, Hint.WORKING, false)
            }
            return Eval(progress, Hint.HOLD_STILL, false)
        }
        acc = 0.0
        val turn = sw.turnToNext(yaw) ?: return Eval(1.0, null, true)
        return Eval(progress, if (turn > 0) Hint.TURN_LEFT else Hint.TURN_RIGHT, false)
    }

    private fun frontierStep(st: GuideStep.Frontier, s: GuideSignals): Eval {
        if (s.frontierExhausted) return Eval(1.0, null, true)
        val p = s.position
        if (!s.tracking || p == null) return Eval(0.0, Hint.LOST_TRACKING, false)
        val target = s.frontier ?: return Eval(0.0, Hint.WORKING, false)
        val to = Vec3(target.x - p.x, target.y - p.y, 0.0)
        val dist = to.length
        if (moveOrigin == null) { moveOrigin = p; acc = max(dist, 0.01) }
        val progress = (1.0 - dist / acc).coerceIn(0.0, 0.99)
        if (dist <= st.tolM) return Eval(1.0, null, true)
        val f = s.facing?.let { flat(it) } ?: return Eval(progress, Hint.KEEP_GOING, false)
        val ang = signedAngleDeg(f, to.normalized())
        val hint = when {
            ang > 35 -> Hint.TURN_LEFT
            ang < -35 -> Hint.TURN_RIGHT
            else -> Hint.KEEP_GOING
        }
        return Eval(progress, hint, false)
    }

    // ---------------- yardımcılar ----------------

    private var prevPos: Vec3? = null
    private var prevPosT = 0.0
    private var speedEst: Double? = null

    private fun last2Speed(s: GuideSignals): Double? {
        val p = s.position ?: return speedEst
        val pp = prevPos
        if (pp != null && s.tS - prevPosT >= 0.5) {
            speedEst = Vec3(p.x - pp.x, p.y - pp.y, 0.0).length / (s.tS - prevPosT)
            prevPos = p; prevPosT = s.tS
        } else if (pp == null) { prevPos = p; prevPosT = s.tS }
        return speedEst
    }

    private fun cap(kind: CaptureKind, step: GuideStep, p: Vec3?, estimated: Boolean, yawDeg: Double? = null): Capture {
        if (kind != CaptureKind.ACOUSTIC_PING) captureCount++
        return Capture(kind, step.id, stepCaptureIndex++, p, estimated, yawDeg)
    }

    private fun advance(outcome: StepOutcome) {
        current?.let { outcomes += it.id to outcome }
        index++
        stepStartT = last?.tS ?: 0.0
        acc = 0.0; sectorMask = 0; pitchUp = false; pitchDown = false
        moveOrigin = null; moveDir = null; nextCaptureAt = 0.0
        lastYaw = null; turned = 0.0; nextTurnCapture = 0.0
        waitingExternal = false; stepCaptureIndex = 0
        radar = null; pingPending = false
        prevPos = null; speedEst = null
    }

    private fun frame(progress: Double, hint: Hint?, caps: List<Capture>): GuideFrame =
        GuideFrame(index, current, progress, hint, isManual(), caps, finished)

    private fun flat(v: Vec3): Vec3 = Vec3(v.x, v.y, 0.0).normalized()

    /** z etrafında saat yönünün tersine [deg] derece. */
    private fun rotate(v: Vec3, deg: Double): Vec3 {
        val a = deg * PI / 180.0
        return Vec3(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a), 0.0)
    }

    /** a'dan b'ye işaretli açı (derece, + = sola / saat yönünün tersi). */
    private fun signedAngleDeg(a: Vec3, b: Vec3): Double =
        Math.toDegrees(atan2(a.x * b.y - a.y * b.x, a.x * b.x + a.y * b.y))
}
