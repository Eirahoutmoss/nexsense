package com.eirahoutmoss.nexsense.core.listen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val LISTEN_FORMAT = "nexlisten"
const val LISTEN_VERSION = 1

/** Dinlemede algılanan olayın türü. Ad bir sınıflandırma iddiası değil, sinyalin biçimidir. */
@Serializable
enum class ListenKind {
    /** Kısa, keskin başlangıçlı ses (vuruş, darbe). */
    @SerialName("knock") KNOCK,

    /** İnsan sesi bandında, hecemsi dalgalanan ses. "İnsan sesi" olduğu kanıtlanmış değildir. */
    @SerialName("voice_like") VOICE_LIKE,

    /** Diğer belirgin ses. */
    @SerialName("sound") SOUND,

    /** İvmeölçerde ani sarsıntı (telefon bir yüzeye dayalıyken yapıdan gelen vuruş). */
    @SerialName("vibration") VIBRATION,
    ;

    val acoustic: Boolean get() = this != VIBRATION
}

/**
 * Bir olay. [t]: kayıt başından saniye. [peakDb]: mikrofon olaylarında dBFS (en yüksek 10 ms çerçeve),
 * titreşimde dB (1 m/s² referans). [aboveFloorDb]: o andaki gürültü tabanının üstü. [voiceScore]: 0..1.
 */
@Serializable
data class ListenEvent(
    val t: Double,
    val kind: ListenKind,
    val durationS: Double,
    val peakDb: Double,
    val aboveFloorDb: Double,
    val voiceScore: Double = 0.0,
)

/** Dinleyicinin konumu (proje koordinatı, m). [estimated]: elle girildi / ölçülmedi. */
@Serializable
data class ListenPosition(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val pointId: String? = null,
    val estimated: Boolean = true,
)

/** Tatbikat kaynağı: sahte sinyali çalan telefonun konumu ve darbe başlangıçları (kayıt başından saniye). */
@Serializable
data class DrillSource(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val emitT: List<Double>,
)

/**
 * Bir telefonun dinleme kaydı (.nexlisten.json). **Ses kaydı içermez**: yalnızca olay zamanları ve düzeyleri.
 * Kayıtlar dosya olarak paylaşılır ve bir koordinatör telefonda birleştirilir.
 */
@Serializable
data class ListenRecord(
    val format: String = LISTEN_FORMAT,
    val version: Int = LISTEN_VERSION,
    val id: String,
    val label: String,
    /** Kaydın başladığı an, telefon saatine göre (Unix ms). Telefon saatleri birbirinden sapabilir. */
    val startEpochMs: Long,
    val durationS: Double,
    val fs: Int,
    val micSource: String = "",
    /** Dinleme başındaki gürültü tabanı (dBFS); kalibrasyon tamamlanmadıysa null. */
    val floorDbfs: Double? = null,
    /** Kullanıcının referans ölçerle bulduğu ofset (dBFS → yaklaşık dB SPL); yoksa null. */
    val splOffsetDb: Double? = null,
    val position: ListenPosition? = null,
    /** Tatbikat sırasında alındı (olaylar gerçek bir kişiden gelmiyor olabilir). */
    val drill: Boolean = false,
    /** Bu kayıt tatbikat kaynağının kaydıdır (dinleme değil, sinyal çalma). */
    val drillSource: DrillSource? = null,
    val projectId: String? = null,
    val events: List<ListenEvent> = emptyList(),
    val notes: String = "",
)

class ListenFormatException(val reason: String) : Exception(reason)

object ListenCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    const val MAX_EVENTS = 5000

    fun encode(r: ListenRecord): String = json.encodeToString(ListenRecord.serializer(), r)

    /** @throws ListenFormatException biçim, sürüm ya da değerler tutarsızsa. */
    fun decode(text: String): ListenRecord {
        val r = try {
            json.decodeFromString(ListenRecord.serializer(), text)
        } catch (e: SerializationException) {
            throw ListenFormatException("format")
        } catch (e: IllegalArgumentException) {
            throw ListenFormatException("format")
        }
        validate(r)
        return r
    }

    fun validate(r: ListenRecord) {
        fun bad(why: String): Nothing = throw ListenFormatException(why)
        fun fin(v: Double) = !v.isNaN() && !v.isInfinite()
        if (r.format != LISTEN_FORMAT) bad("format")
        if (r.version > LISTEN_VERSION) bad("version")
        if (r.id.isBlank() || r.label.isBlank() || r.label.length > 60) bad("label")
        if (!fin(r.durationS) || r.durationS < 0 || r.durationS > 86_400) bad("duration")
        if (r.fs !in 8_000..96_000) bad("fs")
        if (r.events.size > MAX_EVENTS) bad("events")
        r.floorDbfs?.let { if (!fin(it)) bad("floor") }
        r.splOffsetDb?.let { if (!fin(it)) bad("offset") }
        r.position?.let { if (!fin(it.x) || !fin(it.y) || !fin(it.z)) bad("position") }
        r.drillSource?.let { d ->
            if (!fin(d.x) || !fin(d.y) || !fin(d.z) || d.emitT.any { !fin(it) || it < 0 }) bad("drill")
        }
        for (e in r.events) {
            if (!fin(e.t) || e.t < -1 || e.t > r.durationS + 1) bad("event")
            if (!fin(e.durationS) || e.durationS < 0 || !fin(e.peakDb) || !fin(e.aboveFloorDb)) bad("event")
            if (!fin(e.voiceScore) || e.voiceScore < 0 || e.voiceScore > 1) bad("event")
        }
    }

    /** Dosya adı için güvenli parça. */
    fun fileName(r: ListenRecord): String {
        val safe = r.label.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("").take(30)
        return "$safe-${r.id.take(8)}.nexlisten.json"
    }
}
