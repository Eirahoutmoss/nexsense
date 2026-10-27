package com.eirahoutmoss.nexsense.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NexSense veri modeli — şema sürümü 2 (1 → 2: taramalar, AR/adım konum kaynakları, akustik türler, analiz ayarları).
 *
 * Kurallar (ayrıntı: docs/VERI-MODELI.md):
 *  - Zaman damgaları ISO 8601, UTC ("2026-10-27T18:00:00.123Z").
 *  - Her ölçüm değeri bir birim taşır; birim, sensör türünün birimiyle aynı olmalıdır.
 *  - Eksik değer `null`'dır; sıfır ile karıştırılmaz. NaN/sonsuz değer kaydedilmez.
 *  - Koordinat bilinmiyorsa x/y/z `null` kalır; uygulama sahte koordinat üretmez.
 *  - Veri kaynağı (cihaz / haricî / türetilmiş / demo) ve kalite bilgisi korunur.
 */
const val SCHEMA_VERSION = 2

/** Dışa aktarılan ve diskte saklanan dosyanın biçim adı. */
const val BUNDLE_FORMAT = "nexsense-project"

@Serializable
enum class SampleSource {
    /** Telefonun yerleşik sensörü. */
    @SerialName("device") DEVICE,

    /** Haricî sensör (ör. BLE). MVP'de yok; şemada yer ayrıldı. */
    @SerialName("external") EXTERNAL,

    /** Ham ölçümden hesaplanan değer (ör. titreşim RMS, ses seviyesi). Yöntem calibrationInfo'da yazar. */
    @SerialName("derived") DERIVED,

    /** Örnek/deneme verisi. Arayüzde her zaman "DEMO VERİSİ" etiketiyle görünür. */
    @SerialName("demo") DEMO,
}

@Serializable
enum class PositionSource {
    /** Kullanıcının kendi referans noktasına göre elle girdiği göreli koordinat. */
    @SerialName("manual") MANUAL,

    /** Konum bilinmiyor; x/y/z null. */
    @SerialName("none") NONE,

    /** ARCore hareket izleme (görsel-ataletsel odometri). Başlangıç noktası projenin referansına hizalanır. */
    @SerialName("arcore") ARCORE,

    /** Adım sayımı + yön (PDR). Kaba tahmindir; adım sayısı arttıkça güven düşer. z kullanıcının tuttuğu yüksekliktir. */
    @SerialName("pdr") PDR,

    /** Demo verisinin uydurma konumu. */
    @SerialName("demo") DEMO,
}

/**
 * Projenin göreli koordinat sistemi.
 * Varsayılan: sağ el kuralı, metre; x = referans duvar boyunca sağa, y = içeri/ileri, z = yukarı.
 * Orijin, kullanıcının tarif ettiği referans noktasıdır (ör. "salon kapısının sol alt köşesi").
 */
@Serializable
data class CoordinateSystem(
    val type: String = "relative-cartesian",
    val unit: String = "m",
    val handedness: String = "right",
    val axes: String = "x: right along reference wall, y: forward into room, z: up",
    val origin: String = "",
    /**
     * Manyetik kuzeyin proje +y ekseninden saat yönünde açısı (derece). Yalnızca akustik yön taraması
     * gibi pusula yönü kullanan ölçümler için gerekir; bilinmiyorsa null (o ölçümler "hizalanmamış" işaretlenir).
     */
    val northAngleDeg: Double? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: String,
    val updatedAt: String,
    val coordinateSystem: CoordinateSystem = CoordinateSystem(),
    val schemaVersion: Int = SCHEMA_VERSION,
)

@Serializable
data class Session(
    val id: String,
    val projectId: String,
    val startedAt: String,
    val endedAt: String? = null,
    val notes: String = "",
) {
    val isOpen: Boolean get() = endedAt == null
}

@Serializable
data class MeasurementPoint(
    val id: String,
    val projectId: String,
    val sessionId: String,
    val label: String,
    val notes: String = "",
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val createdAt: String,
    val positionSource: PositionSource = PositionSource.NONE,
    /** 0..1; elle girilen koordinatta bilinmediği için null. */
    val positionConfidence: Double? = null,
) {
    /** Üç bileşen de biliniyorsa sahneye yerleştirilebilir. */
    val isPlaced: Boolean get() = x != null && y != null && z != null
}

/** Ölçüm anında cihazın yönelimi (derece). Hesaplanamadıysa alanlar null. */
@Serializable
data class DeviceOrientation(
    val azimuthDeg: Double? = null,
    val pitchDeg: Double? = null,
    val rollDeg: Double? = null,
    /** Ör. "rotation-matrix(accelerometer+magnetometer)". */
    val method: String = "",
    /** Cihaz → dünya (Doğu, Kuzey, Yukarı) dönüşüm matrisi, satır sıralı 9 değer. Yoksa null. */
    val rotationMatrix: List<Double>? = null,
)

@Serializable
data class SensorSample(
    val id: String,
    val pointId: String? = null,
    val sessionId: String,
    val sensorType: SensorType,
    /** Bileşen adı → değer. Sıra korunur (ör. x, y, z, magnitude). Eksik bileşen null. */
    val values: Map<String, Double?>,
    val unit: String,
    val timestamp: String,
    val source: SampleSource,
    /** Android'in bildirdiği doğruluk durumu: high / medium / low / unreliable; bilinmiyorsa null. */
    val quality: String? = null,
    /** 0..1 güven; hesaplanamıyorsa null. */
    val confidence: Double? = null,
    /** Eksenler cihaz koordinat sistemindedir (Android SensorEvent). */
    val deviceOrientation: DeviceOrientation? = null,
    /** Kalibrasyon / türetme yöntemi notu. */
    val calibrationInfo: String? = null,
)

/** Bir projenin tüm verisi. Diskteki dosya ve JSON dışa aktarma aynı biçimdedir. */
@Serializable
data class ProjectBundle(
    val format: String = BUNDLE_FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val project: Project,
    val sessions: List<Session> = emptyList(),
    val points: List<MeasurementPoint> = emptyList(),
    val samples: List<SensorSample> = emptyList(),
    /** Şema 2: nokta bulutu / düzlem / iz taramaları. */
    val scans: List<Scan> = emptyList(),
    /** Şema 2: projeye özel analiz ayarları (eşikler). */
    val analysis: AnalysisSettings = AnalysisSettings(),
) {
    val openSession: Session? get() = sessions.lastOrNull { it.isOpen }
    fun samplesOf(pointId: String): List<SensorSample> = samples.filter { it.pointId == pointId }
    val hasDemoData: Boolean
        get() = samples.any { it.source == SampleSource.DEMO } || points.any { it.positionSource == PositionSource.DEMO } ||
            scans.any { it.method == ScanMethod.DEMO }
}
