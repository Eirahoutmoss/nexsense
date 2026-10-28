package com.eirahoutmoss.nexsense.core.listen

import com.eirahoutmoss.nexsense.core.scene.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Telefon saatleri arasındaki farkı olay dizilerinden kestirir.
 *
 * Telefonlar saatini ağdan eşitler; yine de yüzlerce ms, ağsız ortamda saniyeler sapabilir. Aynı vuruş dizisini
 * duyan iki telefonun olayları sabit bir kaymayla örtüşür: o kayma aranır (± [maxShiftS], tolerans [tolS]).
 * En az 3 olay örtüşmezse kayma 0 kabul edilir ve [Alignment.aligned] false olur.
 */
object ClockAlign {
    data class Alignment(val offsetS: Double, val matched: Int, val aligned: Boolean)

    fun absTimes(r: ListenRecord): List<Double> =
        r.events.filter { it.kind.acoustic }.map { r.startEpochMs / 1000.0 + it.t }.sorted()

    fun offsets(records: List<ListenRecord>, maxShiftS: Double = 2.0, tolS: Double = 0.08): List<Alignment> {
        if (records.isEmpty()) return emptyList()
        // Hesap yükü sınırlı kalsın: ilk 200 olay yeterli.
        val times = records.map { absTimes(it).take(200) }
        val ref = times.indices.maxBy { times[it].size }
        return records.indices.map { i ->
            if (i == ref) return@map Alignment(0.0, times[i].size, true)
            val a = times[ref]
            val b = times[i]
            if (a.size < 3 || b.size < 3) return@map Alignment(0.0, 0, false)
            var bestShift = 0.0
            var bestCount = 0
            for (x in a) for (y in b) {
                val d = x - y
                if (abs(d) > maxShiftS) continue
                val c = b.count { t -> a.any { abs(it - (t + d)) <= tolS } }
                if (c > bestCount || (c == bestCount && abs(d) < abs(bestShift))) { bestCount = c; bestShift = d }
            }
            if (bestCount < 3) return@map Alignment(0.0, bestCount, false)
            // İnce ayar: eşleşen çiftlerin ortalama artığı.
            val res = b.mapNotNull { t -> a.minByOrNull { abs(it - (t + bestShift)) }?.let { it - (t + bestShift) }?.takeIf { abs(it) <= tolS } }
            Alignment(round3(bestShift + (if (res.isEmpty()) 0.0 else res.average())), bestCount, true)
        }
    }
}

/** Bir olayın bir dinleyicideki karşılığı. */
data class GroupMember(val record: Int, val event: ListenEvent)

/** Birden çok telefonun aynı anda (tolerans içinde) duyduğu olay. */
data class EventGroup(val tEpochS: Double, val members: List<GroupMember>)

/**
 * Genlik tabanlı yaklaşık konum. Ses, kaynaktan uzaklaştıkça serbest alanda 20·log10(r) dB zayıflar:
 * L_i = L0 − 20·log10(r_i). Kaynak düzeyi L0 bilinmediği için yalnızca düzey **farkları** kullanılır.
 * Izgara araması: her aday nokta için L0 en küçük kareyle kestirilir; artık (dB) en küçük olan aday en iyisidir.
 * [region]: artığı en iyinin [toleranceDb] fazlasını aşmayan hücreler (belirsizlik bölgesi).
 */
data class SourceEstimate(
    val best: Vec3,
    val residualDb: Double,
    val region: List<Vec3>,
    val cellM: Double,
    val listeners: Int,
    val groups: Int,
    val sourceLevelDb: Double,
) {
    val regionAreaM2: Double get() = region.size * cellM * cellM

    /** Bölge büyük: konum yalnızca kaba bir yöndür. */
    val ambiguous: Boolean get() = regionAreaM2 > 4.0
}

data class DrillCheck(val source: Vec3, val emitted: Int, val errorM: Double?)

data class FusionResult(
    val alignments: List<ClockAlign.Alignment>,
    val groups: List<EventGroup>,
    val estimate: SourceEstimate?,
    /** Konumu bilinen dinleyicilerden en yüksek düzeyle duyanın kaydı (konum bulunamasa da yön verir). */
    val loudestRecord: Int?,
    val drill: DrillCheck?,
    val gainsDb: Map<Int, Double>,
) {
    val isDrill: Boolean get() = drill != null
}

object Fusion {
    /** Kaydın i. olayının karşılaştırılabilir düzeyi: tepe (dBFS) + SPL ofseti + telefon kazanç düzeltmesi. */
    fun level(r: ListenRecord, e: ListenEvent, gainDb: Double = 0.0): Double = e.peakDb + (r.splOffsetDb ?: 0.0) + gainDb

    /**
     * Olayları eşler: zaman sırasıyla her eşleşmemiş olay bir çapa olur; diğer her kayıttan çapaya [tolS]
     * içindeki en yakın eşleşmemiş olay eklenir. En az iki telefonun duyduğu gruplar döner.
     * Tatbikat kaynağının kendi kaydı (sinyali çalan) gruplara katılmaz.
     */
    fun group(records: List<ListenRecord>, offsets: List<Double>, tolS: Double = 0.25): List<EventGroup> {
        data class Item(val t: Double, val rec: Int, val ev: ListenEvent)
        val items = records.flatMapIndexed { i, r ->
            if (r.drillSource != null) emptyList()
            else r.events.filter { it.kind.acoustic }.map { Item(r.startEpochMs / 1000.0 + it.t + offsets.getOrElse(i) { 0.0 }, i, it) }
        }.sortedBy { it.t }
        val used = HashSet<Item>()
        val out = ArrayList<EventGroup>()
        for (anchor in items) {
            if (anchor in used) continue
            used += anchor
            val members = arrayListOf(GroupMember(anchor.rec, anchor.ev))
            val times = arrayListOf(anchor.t)
            for (i in records.indices) {
                if (i == anchor.rec) continue
                val m = items.filter { it.rec == i && it !in used && abs(it.t - anchor.t) <= tolS }.minByOrNull { abs(it.t - anchor.t) } ?: continue
                used += m
                members += GroupMember(m.rec, m.ev)
                times += m.t
            }
            if (members.size >= 2) out += EventGroup(round3(times.average()), members)
        }
        return out
    }

    /**
     * Birleşik ızgara araması: her grubun kendi kaynak düzeyi olur; artıkların karesi gruplar boyunca toplanır.
     * Konumu bilinen en az 3 dinleyicinin duyduğu gruplar kullanılır.
     */
    fun locate(
        groups: List<List<Pair<Vec3, Double>>>,
        cellM: Double = 0.25,
        marginM: Double = 5.0,
        toleranceDb: Double = 1.5,
    ): SourceEstimate? {
        val usable = groups.filter { g -> g.size >= 3 }
        if (usable.isEmpty()) return null
        val pts = usable.flatMap { g -> g.map { it.first } }
        val minX = pts.minOf { it.x } - marginM; val maxX = pts.maxOf { it.x } + marginM
        val minY = pts.minOf { it.y } - marginM; val maxY = pts.maxOf { it.y } + marginM
        var step = cellM
        while (ceil((maxX - minX) / step) * ceil((maxY - minY) / step) > 160_000) step *= 2
        val nx = ceil((maxX - minX) / step).toInt()
        val ny = ceil((maxY - minY) / step).toInt()
        val res = DoubleArray(nx * ny)
        val l0 = DoubleArray(nx * ny)
        var bestK = 0
        val nObs = usable.sumOf { it.size }
        for (j in 0 until ny) for (i in 0 until nx) {
            val x = minX + (i + 0.5) * step
            val y = minY + (j + 0.5) * step
            var sse = 0.0
            var lsum = 0.0
            for (g in usable) {
                // r en az 0,5 m: telefonun dibindeki kaynakta 20·log10(r) patlamasın.
                val att = g.map { 20 * log10(max(0.5, hypot(it.first.x - x, it.first.y - y))) }
                val est = g.indices.sumOf { g[it].second + att[it] } / g.size
                lsum += est
                for (k in g.indices) { val e = g[k].second - (est - att[k]); sse += e * e }
            }
            val k = j * nx + i
            res[k] = sqrt(sse / nObs)
            l0[k] = lsum / usable.size
            if (res[k] < res[bestK]) bestK = k
        }
        val best = res[bestK]
        val region = ArrayList<Vec3>()
        for (k in res.indices) if (res[k] <= best + toleranceDb) {
            region += Vec3(minX + (k % nx + 0.5) * step, minY + (k / nx + 0.5) * step, 0.0)
        }
        val bx = minX + (bestK % nx + 0.5) * step
        val by = minY + (bestK / nx + 0.5) * step
        return SourceEstimate(Vec3(bx, by, 0.0), round2(best), region, step, usable.flatMap { g -> g.map { it.first } }.distinct().size, usable.size, round2(l0[bestK]))
    }

    /**
     * Tatbikatla telefon kazanç farkı: kaynak konumu biliniyorsa her telefonun beklenen düzeyden ortalama sapması.
     * Ortalama sıfıra çekilir (mutlak düzey değil, telefonlar arası fark). Dönen değer düzeye **eklenecek** düzeltmedir.
     */
    fun calibrateGains(records: List<ListenRecord>, groups: List<EventGroup>, source: Vec3): Map<Int, Double> {
        val dev = HashMap<Int, MutableList<Double>>()
        for (g in groups) {
            val ms = g.members.filter { records[it.record].position != null }
            if (ms.size < 2) continue
            val obs = ms.map { m ->
                val p = records[m.record].position!!
                val att = 20 * log10(max(0.5, hypot(p.x - source.x, p.y - source.y)))
                Triple(m.record, level(records[m.record], m.event), att)
            }
            val est = obs.sumOf { it.second + it.third } / obs.size
            for ((rec, l, att) in obs) dev.getOrPut(rec) { ArrayList() } += l - (est - att)
        }
        if (dev.isEmpty()) return emptyMap()
        val means = dev.mapValues { it.value.average() }
        val m = means.values.average()
        return means.mapValues { round2(-(it.value - m)) }
    }

    /** Tüm akış: saat hizalama → eşleme → (varsa tatbikat kazancı) → konum. */
    fun run(records: List<ListenRecord>, useDrillGains: Boolean = true, tolS: Double = 0.25): FusionResult {
        val al = ClockAlign.offsets(records.map { if (it.drillSource != null) it.copy(events = emptyList()) else it })
        val offsets = al.map { it.offsetS }
        val groups = group(records, offsets, tolS)
        val src = records.firstOrNull { it.drillSource != null }?.drillSource
        val source = src?.let { Vec3(it.x, it.y, it.z) }
        val gains = if (useDrillGains && source != null) calibrateGains(records, groups, source) else emptyMap()
        val located = groups.map { g ->
            g.members.mapNotNull { m ->
                val p = records[m.record].position ?: return@mapNotNull null
                Vec3(p.x, p.y, p.z) to level(records[m.record], m.event, gains[m.record] ?: 0.0)
            }
        }
        val estimate = locate(located)
        val loud = HashMap<Int, MutableList<Double>>()
        for (g in groups) {
            val ms = g.members.filter { records[it.record].position != null }
            if (ms.size < 2) continue
            val top = ms.maxBy { level(records[it.record], it.event, gains[it.record] ?: 0.0) }
            loud.getOrPut(top.record) { ArrayList() } += 1.0
        }
        val loudest = loud.maxByOrNull { it.value.size }?.key
        val drill = if (src != null && source != null) {
            DrillCheck(source, src.emitT.size, estimate?.let { round2(hypot(it.best.x - source.x, it.best.y - source.y)) })
        } else null
        return FusionResult(al, groups, estimate, loudest, drill, gains)
    }
}
