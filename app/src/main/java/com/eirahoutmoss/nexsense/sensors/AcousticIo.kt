package com.eirahoutmoss.nexsense.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Ses donanımı hatası; [code] arayüzde metne çevrilir. 1: biçim, 2: mikrofon açılamadı, 3: hoparlör, 4: okuma, 5: izin. */
class AcousticException(val code: Int) : IOException("acoustic error $code")

/**
 * Hoparlörden sinyal çalar ve aynı anda mikrofondan kaydeder (NexTools AcousticRecorder'dan uyarlandı).
 * Mümkünse işlenmemiş (UNPROCESSED) ses kaynağı kullanılır: otomatik kazanç ve gürültü bastırma yankıyı bozar.
 * Ses kaydı yalnızca bellekte tutulur ve ölçüm bitince atılır; diske yazılmaz.
 */
class AcousticIo(private val context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val sourceName: String get() = source().second

    fun mediaVolumeRatio(): Double {
        val m = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (m > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / m else 1.0
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun source(): Pair<Int, String> {
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
        else MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
    }

    /**
     * [signal]'i [repeats] kez çalar; her çalışta [takeS] saniye kaydeder.
     * [onProgress]: 0..1.
     */
    @SuppressLint("MissingPermission")
    suspend fun playAndRecord(
        signal: FloatArray,
        fs: Int,
        repeats: Int,
        takeS: Double,
        onProgress: (Float) -> Unit = {},
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw AcousticException(5)
        val rec = openRecord(fs)
        val pcm = ShortArray(signal.size) { (signal[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(fs).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            rec.release()
            throw AcousticException(3)
        }
        try {
            if (track.write(pcm, 0, pcm.size) != pcm.size) throw AcousticException(3)
            rec.startRecording()
            val takes = ArrayList<FloatArray>()
            val perTake = (fs * takeS).toInt()
            for (r in 0 until repeats) {
                coroutineContext.ensureActive()
                track.stop()
                track.reloadStaticData()
                track.play()
                takes += read(rec, perTake) { done -> onProgress((r + done) / repeats) }
            }
            rec.stop()
            takes
        } catch (e: SecurityException) {
            throw AcousticException(5)
        } catch (e: IllegalStateException) {
            throw AcousticException(4)
        } finally {
            runCatching { track.stop() }
            track.release()
            runCatching { rec.stop() }
            rec.release()
        }
    }

    /** Yalnızca kayıt (ses basıncı, spektrum). */
    @SuppressLint("MissingPermission")
    suspend fun record(fs: Int, seconds: Double, onProgress: (Float) -> Unit = {}): FloatArray = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw AcousticException(5)
        val rec = openRecord(fs)
        try {
            rec.startRecording()
            // İlk 0,2 sn atılır: mikrofonun açılış geçişi.
            read(rec, fs / 5)
            read(rec, (fs * seconds).toInt(), onProgress)
        } catch (e: SecurityException) {
            throw AcousticException(5)
        } catch (e: IllegalStateException) {
            throw AcousticException(4)
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    /**
     * Sürekli dinleme: iptal edilene kadar [blockN] örneklik blokları [onBlock]'a verir.
     * Bloklar yalnızca bellekte işlenir; ses saklanmaz.
     */
    @SuppressLint("MissingPermission")
    suspend fun stream(fs: Int, blockN: Int = 1600, onBlock: (FloatArray) -> Unit) = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw AcousticException(5)
        val rec = openRecord(fs)
        try {
            rec.startRecording()
            read(rec, fs / 5) // açılış geçişi
            val buf = ShortArray(blockN)
            while (true) {
                coroutineContext.ensureActive()
                val got = rec.read(buf, 0, blockN)
                if (got < 0) throw AcousticException(4)
                if (got > 0) onBlock(FloatArray(got) { buf[it] / 32768f })
            }
        } catch (e: SecurityException) {
            throw AcousticException(5)
        } catch (e: IllegalStateException) {
            throw AcousticException(4)
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    /** Yalnızca çalma (tatbikat sinyali). Çalma bitince döner. */
    suspend fun play(signal: FloatArray, fs: Int) = withContext(Dispatchers.IO) {
        val pcm = ShortArray(signal.size) { (signal[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(fs).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            throw AcousticException(3)
        }
        try {
            if (track.write(pcm, 0, pcm.size) != pcm.size) throw AcousticException(3)
            track.play()
            val ms = pcm.size * 1000L / fs
            var waited = 0L
            while (waited < ms + 200) {
                coroutineContext.ensureActive()
                kotlinx.coroutines.delay(50)
                waited += 50
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(fs: Int): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw AcousticException(1)
        val rec = try {
            AudioRecord(source().first, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 4, fs))
        } catch (e: SecurityException) {
            throw AcousticException(5)
        } catch (e: IllegalArgumentException) {
            throw AcousticException(1)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw AcousticException(2)
        }
        return rec
    }

    private suspend fun read(rec: AudioRecord, count: Int, progress: ((Float) -> Unit)? = null): FloatArray {
        val out = FloatArray(count)
        val buf = ShortArray(4096)
        var n = 0
        while (n < count) {
            coroutineContext.ensureActive()
            val got = rec.read(buf, 0, minOf(buf.size, count - n))
            if (got < 0) throw AcousticException(4)
            for (i in 0 until got) out[n + i] = buf[i] / 32768f
            n += got
            progress?.invoke(n.toFloat() / count)
        }
        return out
    }
}
