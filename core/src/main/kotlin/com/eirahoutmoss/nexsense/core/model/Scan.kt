package com.eirahoutmoss.nexsense.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScanMethod {
    /** ARCore özellik noktaları (görsel izleme sırasında bulunan köşe/desen noktaları). */
    @SerialName("arcore") ARCORE,

    /** Adım sayımıyla tahmini iz (nokta bulutu yok, yalnızca yol). */
    @SerialName("pdr") PDR,

    /** Sabit noktadan, telefonun doğrultulduğu yönde sonar yankı mesafeleri. Deneysel. */
    @SerialName("acoustic_echo") ACOUSTIC_ECHO,

    @SerialName("demo") DEMO,
}

/** Tarama noktası (proje koordinatında, metre). [c]: 0..1 güven. [k]: tür (f=özellik, d=derinlik, a=akustik). */
@Serializable
data class ScanPoint(val x: Double, val y: Double, val z: Double, val c: Double, val k: String = "f")

/** ARCore düzlem türü. "Yukarı bakan yatay" zemin de olabilir masa da; uygulama bunu ayırt etmez. */
@Serializable
enum class PlaneType {
    @SerialName("horizontal_up") HORIZONTAL_UP,
    @SerialName("horizontal_down") HORIZONTAL_DOWN,
    @SerialName("vertical") VERTICAL,
}

/** Algılanan düzlem: proje koordinatında çokgen köşeleri. */
@Serializable
data class ScanPlane(val type: PlaneType, val polygon: List<List<Double>>, val areaM2: Double? = null)

/** Telefonun izlediği yol. [t]: başlangıçtan beri saniye. */
@Serializable
data class TrackPoint(val t: Double, val x: Double, val y: Double, val z: Double)

/**
 * Taramanın proje koordinatına nasıl yerleştirildiği.
 * Tarama çerçevesi (z yukarı) önce [yawDeg] kadar z ekseni etrafında döndürülür, sonra [offset] eklenir.
 */
@Serializable
data class ScanAlignment(
    val startPointId: String? = null,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val yawDeg: Double = 0.0,
    /** Ör. "start=reference", "start=point", "magnetic-north". */
    val note: String = "",
)

@Serializable
data class Scan(
    val id: String,
    val projectId: String,
    val sessionId: String? = null,
    val method: ScanMethod,
    val startedAt: String,
    val endedAt: String? = null,
    val points: List<ScanPoint> = emptyList(),
    val planes: List<ScanPlane> = emptyList(),
    val trajectory: List<TrackPoint> = emptyList(),
    val alignment: ScanAlignment = ScanAlignment(),
    val notes: String = "",
)

/** Eşik: değer [min]'in altına ya da [max]'ın üstüne çıkarsa uyarı. Katman adı LayerKind adıdır. */
@Serializable
data class Threshold(val layer: String, val min: Double? = null, val max: Double? = null)

@Serializable
data class AnalysisSettings(
    val thresholds: List<Threshold> = emptyList(),
    /** Ara değer (IDW) hücre boyu ve en fazla uzaklık (m). Bu uzaklıktan öte tahmin üretilmez. */
    val gridCellM: Double = 0.25,
    val maxEstimateDistanceM: Double = 1.5,
)
