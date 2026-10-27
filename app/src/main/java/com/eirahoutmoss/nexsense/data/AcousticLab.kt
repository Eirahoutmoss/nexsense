package com.eirahoutmoss.nexsense.data

import com.eirahoutmoss.nexsense.core.acoustic.AcousticsResult
import com.eirahoutmoss.nexsense.core.acoustic.Quality
import com.eirahoutmoss.nexsense.core.acoustic.RoomAcoustics
import com.eirahoutmoss.nexsense.core.acoustic.RoomModes
import com.eirahoutmoss.nexsense.core.acoustic.Sonar
import com.eirahoutmoss.nexsense.core.acoustic.SoundPressure
import com.eirahoutmoss.nexsense.core.acoustic.Sweep
import com.eirahoutmoss.nexsense.core.analysis.RoomEstimate
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.store.SampleDraft
import com.eirahoutmoss.nexsense.sensors.AcousticIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Sabit noktada akustik ölçümler (Faz 4). Telefon bir yere konur; hoparlör çalar, mikrofon dinler.
 * Ses kaydı yalnızca bellekte işlenir ve atılır.
 */
class AcousticLab(private val io: AcousticIo) {

    data class RoomResult(
        val result: AcousticsResult,
        val modes: List<RoomModes.Mode>,
        val room: RoomEstimate?,
        val volumeRatio: Double,
        val source: String,
    )

    data class EchoResult(val echoes: List<Sonar.Echo>, val speedMs: Double, val tempC: Double, val tempMeasured: Boolean)

    data class LevelResult(val leqDbfs: Double, val peakDbfs: Double, val dominantHz: Double?, val splOffset: Double?) {
        val leqSpl: Double? get() = splOffset?.let { SoundPressure.spl(leqDbfs, it) }
        val peakSpl: Double? get() = splOffset?.let { SoundPressure.spl(peakDbfs, it) }
    }

    /** Oda yankı süresi: üstel tarama ×2, dürtü yanıtı, oktav bantlarında T20 → RT60. */
    suspend fun room(room: RoomEstimate?, onProgress: (Float) -> Unit): RoomResult {
        val sweep = Sweep.create(fs = FS, durationS = 3.0)
        val takes = io.playAndRecord(sweep.samples, FS, repeats = 2, takeS = 3.0 + 1.8, onProgress = onProgress)
        val result = withContext(Dispatchers.Default) {
            val irs = takes.map { RoomAcoustics.impulseResponse(it, sweep, lengthS = 1.6) }
            RoomAcoustics.analyze(RoomAcoustics.average(irs), FS, externalSpeaker = false)
        }
        val modes = room?.let { RoomModes.axial(it.lengthM, it.widthM, it.heightM) }.orEmpty()
        return RoomResult(result, modes, room, io.mediaVolumeRatio(), io.sourceName)
    }

    /** Sonar: 8 kısa taramalık dizi, darbeler hizalanıp ortalanır; en güçlü 3 yankı. */
    suspend fun echo(ambientTempC: Double?, onProgress: (Float) -> Unit): EchoResult {
        val chirp = Sonar.chirp(FS, 3000.0, 9000.0, 0.004)
        val play = Sonar.pingTrain(chirp, FS, PINGS, SPACING_S)
        val take = io.playAndRecord(play, FS, repeats = 1, takeS = play.size.toDouble() / FS + 0.6, onProgress = onProgress).single()
        val temp = ambientTempC ?: 20.0
        val c = Sonar.soundSpeed(temp)
        val echoes = withContext(Dispatchers.Default) {
            val x = DoubleArray(take.size) { take[it].toDouble() }
            val corr = Sonar.correlate(x, chirp, x.size - chirp.size)
            Sonar.echoes(Sonar.alignPings(corr, FS, SPACING_S, PINGS, c, MAX_ECHO_M), FS, c, maxDistM = MAX_ECHO_M)
        }
        return EchoResult(echoes, c, temp, ambientTempC != null)
    }

    /** Ses düzeyi (3 sn), baskın frekans; ofset varsa yaklaşık dB SPL. */
    suspend fun level(splOffset: Double?, onProgress: (Float) -> Unit): LevelResult {
        val pcm = io.record(FS, 3.0, onProgress)
        return withContext(Dispatchers.Default) {
            LevelResult(
                leqDbfs = SoundPressure.leqDbfs(pcm) ?: -120.0,
                peakDbfs = SoundPressure.peakDbfs(pcm) ?: -120.0,
                dominantHz = SoundPressure.dominantHz(pcm, FS),
                splOffset = splOffset,
            )
        }
    }

    // ---------- Kayda dönüştürme ----------

    fun roomDraft(r: RoomResult, orientation: DeviceOrientation?): SampleDraft {
        fun band(hz: Double) = r.result.bands.firstOrNull { it.centerHz == hz }?.takeIf { it.quality != Quality.UNRELIABLE }?.rt60S?.let { round(it, 3) }
        return SampleDraft(
            sensorType = SensorType.REVERB_TIME,
            values = linkedMapOf(
                "rt60_mid" to r.result.rt60MidS?.let { round(it, 3) },
                "rt60_125" to band(125.0), "rt60_250" to band(250.0), "rt60_500" to band(500.0),
                "rt60_1000" to band(1000.0), "rt60_2000" to band(2000.0), "rt60_4000" to band(4000.0),
            ),
            timestamp = Instant.now().toString(),
            source = SampleSource.DERIVED,
            quality = r.result.quality.name.lowercase(),
            deviceOrientation = orientation,
            calibrationInfo = "exp sweep 50-16000 Hz x2, Schroeder T20->RT60, octave bands; phone speaker/mic; source=${r.source}; volume=${(r.volumeRatio * 100).roundToInt()}%",
        )
    }

    fun echoDraft(e: EchoResult, orientation: DeviceOrientation?): SampleDraft {
        val d = e.echoes.map { round(it.distanceM, 3) }
        return SampleDraft(
            sensorType = SensorType.ECHO_DISTANCE,
            values = linkedMapOf("first" to d.getOrNull(0), "second" to d.getOrNull(1), "third" to d.getOrNull(2)),
            timestamp = Instant.now().toString(),
            source = SampleSource.DERIVED,
            confidence = e.echoes.firstOrNull()?.let { round(Sonar.confidence01(it.confidence), 2) },
            deviceOrientation = orientation,
            calibrationInfo = "chirp 3-9 kHz x$PINGS, matched filter, c=${round(e.speedMs, 1)} m/s @ ${round(e.tempC, 1)} °C" +
                if (e.tempMeasured) " (measured)" else " (assumed)",
        )
    }

    fun levelDrafts(l: LevelResult, orientation: DeviceOrientation?): List<SampleDraft> {
        val ts = Instant.now().toString()
        return buildList {
            add(
                SampleDraft(
                    SensorType.SOUND_LEVEL, linkedMapOf("rms" to round(l.leqDbfs, 2), "peak" to round(l.peakDbfs, 2)), ts,
                    SampleSource.DERIVED, deviceOrientation = orientation, calibrationInfo = "Leq 3 s, uncalibrated dBFS",
                ),
            )
            if (l.splOffset != null) {
                add(
                    SampleDraft(
                        SensorType.SOUND_SPL, linkedMapOf("leq" to l.leqSpl?.let { round(it, 1) }, "peak" to l.peakSpl?.let { round(it, 1) }), ts,
                        SampleSource.DERIVED, deviceOrientation = orientation,
                        calibrationInfo = "dBFS + user offset ${round(l.splOffset, 1)} dB (reference meter); approximate, unweighted",
                    ),
                )
            }
            l.dominantHz?.let {
                add(
                    SampleDraft(
                        SensorType.DOMINANT_FREQUENCY, linkedMapOf("frequency" to round(it, 1)), ts,
                        SampleSource.DERIVED, deviceOrientation = orientation, calibrationInfo = "FFT 32768, Hann, 40-8000 Hz",
                    ),
                )
            }
        }
    }

    private fun round(v: Double, d: Int): Double {
        var f = 1.0
        repeat(d) { f *= 10 }
        return Math.round(v * f) / f
    }

    companion object {
        const val FS = 48_000
        const val PINGS = 8
        const val SPACING_S = 0.1
        const val MAX_ECHO_M = 5.0
    }
}
