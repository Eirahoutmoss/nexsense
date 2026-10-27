package com.eirahoutmoss.nexsense.core.layers

import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorType

/**
 * Sahne katmanları. Her ölçüm katmanı tek bir sensör türünün tek bir bileşenini gösterir;
 * farklı sensörler aynı katmanda karıştırılmaz.
 *
 * Bilimsel dürüstlük: MVP'de interpolasyon YOKTUR. Yalnızca ölçülen noktalar renklenir;
 * aradaki boşluk "ölçülmedi" demektir ve boş bırakılır.
 */
enum class LayerKind(val sensor: SensorType?, val component: String?) {
    GEOMETRY(null, null),
    MAGNETIC(SensorType.MAGNETIC_FIELD, "magnitude"),
    MOTION(SensorType.VIBRATION_RMS, "rms"),
    ACOUSTIC(SensorType.SOUND_LEVEL, "rms"),
    SPL(SensorType.SOUND_SPL, "leq"),
    REVERB(SensorType.REVERB_TIME, "rt60_mid"),
    TEMPERATURE(SensorType.AMBIENT_TEMPERATURE, "value"),
    HUMIDITY(SensorType.RELATIVE_HUMIDITY, "value"),
    NOTES(null, null),

    /** Nokta bulutu, düzlemler ve izler (ARCore / adım / akustik yön taraması). */
    SCAN(null, null),
    ;

    val isMeasurement: Boolean get() = sensor != null
}

enum class LayerStatus { NO_DATA, HAS_DATA }

data class PointValue(val pointId: String, val value: Double, val isDemo: Boolean, val placed: Boolean)

data class LayerData(
    val kind: LayerKind,
    val status: LayerStatus,
    val values: List<PointValue>,
    val min: Double?,
    val max: Double?,
    val unit: String?,
    /** Katmanda en az bir demo değeri varsa arayüz "DEMO VERİSİ" etiketini gösterir. */
    val containsDemo: Boolean,
    /** Değeri olan ama koordinatı bilinmeyen (sahneye yerleştirilemeyen) nokta sayısı. */
    val unplacedCount: Int,
) {
    /** Değeri 0..1 aralığına taşır (renk için). Tüm değerler eşitse 0.5. */
    fun normalized(v: Double): Double {
        val lo = min ?: return 0.5
        val hi = max ?: return 0.5
        if (hi - lo < 1e-12) return 0.5
        return ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    }

    fun valueOf(pointId: String): PointValue? = values.firstOrNull { it.pointId == pointId }
}

object LayerAnalysis {

    fun compute(bundle: ProjectBundle, kind: LayerKind): LayerData {
        val pointsById = bundle.points.associateBy { it.id }
        return when (kind) {
            LayerKind.GEOMETRY -> {
                val vals = bundle.points.map {
                    PointValue(it.id, 0.0, it.positionSource == com.eirahoutmoss.nexsense.core.model.PositionSource.DEMO, it.isPlaced)
                }
                LayerData(
                    kind, if (vals.any { it.placed }) LayerStatus.HAS_DATA else LayerStatus.NO_DATA, vals,
                    null, null, null, vals.any { it.isDemo }, vals.count { !it.placed },
                )
            }
            LayerKind.NOTES -> {
                val vals = bundle.points.filter { it.notes.isNotBlank() }.map {
                    PointValue(it.id, 0.0, it.positionSource == com.eirahoutmoss.nexsense.core.model.PositionSource.DEMO, it.isPlaced)
                }
                LayerData(
                    kind, if (vals.isNotEmpty()) LayerStatus.HAS_DATA else LayerStatus.NO_DATA, vals,
                    null, null, null, vals.any { it.isDemo }, vals.count { !it.placed },
                )
            }
            LayerKind.SCAN -> {
                val has = bundle.scans.any { it.points.isNotEmpty() || it.planes.isNotEmpty() || it.trajectory.isNotEmpty() }
                LayerData(
                    kind, if (has) LayerStatus.HAS_DATA else LayerStatus.NO_DATA, emptyList(),
                    null, null, null, bundle.scans.any { it.method == com.eirahoutmoss.nexsense.core.model.ScanMethod.DEMO }, 0,
                )
            }
            else -> {
                val sensor = kind.sensor!!
                val comp = kind.component!!
                // Aynı noktada aynı sensörden birden çok örnek varsa en sonuncusu alınır.
                val latest = bundle.samples
                    .filter { it.sensorType == sensor && it.pointId != null && it.pointId in pointsById }
                    .groupBy { it.pointId!! }
                    .mapValues { (_, list) -> list.maxBy { it.timestamp } }
                val vals = latest.mapNotNull { (pid, s) ->
                    val v = s.values[comp] ?: return@mapNotNull null // null değer: ölçülmedi, sıfır değil
                    PointValue(pid, v, s.source == SampleSource.DEMO, pointsById.getValue(pid).isPlaced)
                }
                LayerData(
                    kind = kind,
                    status = if (vals.isEmpty()) LayerStatus.NO_DATA else LayerStatus.HAS_DATA,
                    values = vals,
                    min = vals.minOfOrNull { it.value },
                    max = vals.maxOfOrNull { it.value },
                    unit = sensor.unit,
                    containsDemo = vals.any { it.isDemo },
                    unplacedCount = vals.count { !it.placed },
                )
            }
        }
    }

    fun computeAll(bundle: ProjectBundle): Map<LayerKind, LayerData> =
        LayerKind.entries.associateWith { compute(bundle, it) }
}

/** Açık/kapalı katmanlar. Ölçüm katmanlarından aynı anda yalnızca biri renk verir. */
data class LayerSelection(
    val visible: Set<LayerKind> = setOf(LayerKind.GEOMETRY, LayerKind.MAGNETIC, LayerKind.NOTES, LayerKind.SCAN),
    val colorLayer: LayerKind? = LayerKind.MAGNETIC,
) {
    fun toggle(kind: LayerKind): LayerSelection {
        val on = kind !in visible
        val nextVisible = if (on) visible + kind else visible - kind
        val nextColor = when {
            on && kind.isMeasurement -> kind
            !on && colorLayer == kind -> nextVisible.firstOrNull { it.isMeasurement }
            else -> colorLayer
        }
        return LayerSelection(nextVisible, nextColor)
    }

    fun isVisible(kind: LayerKind) = kind in visible
}
