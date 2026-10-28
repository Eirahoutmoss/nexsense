package com.eirahoutmoss.nexsense.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.eirahoutmoss.nexsense.core.listen.ListenCodec
import com.eirahoutmoss.nexsense.core.listen.ListenFormatException
import com.eirahoutmoss.nexsense.core.listen.ListenRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dinleme kayıtları (.nexlisten.json): uygulamanın özel klasöründe. Ses içermez; yalnızca olay zamanları ve düzeyleri.
 * Telefonlar arası alışveriş dosya paylaşımıyla, kullanıcının seçtiği kanaldan yapılır; uygulama kendisi ağa göndermez.
 */
class ListenStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "listen").apply { mkdirs() }

    private fun fileFor(id: String) = File(dir, "${id.filter { it.isLetterOrDigit() || it == '-' }}.nexlisten.json")

    suspend fun save(r: ListenRecord) = withContext(Dispatchers.IO) {
        ListenCodec.validate(r)
        fileFor(r.id).writeText(ListenCodec.encode(r))
    }

    /** En yeniler önce. Bozuk dosyalar atlanır. */
    suspend fun list(): List<ListenRecord> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".nexlisten.json") }.orEmpty()
            .mapNotNull { f -> runCatching { ListenCodec.decode(f.readText()) }.getOrNull() }
            .sortedByDescending { it.startEpochMs }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { fileFor(id).delete() }

    /** İçe alma. @return kayıt; biçim hatalıysa ListenFormatException. */
    suspend fun import(uri: Uri): ListenRecord = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            // Makul üst sınır: olay sayısı sınırlı olduğundan 4 MB yeter.
            val limit = 4 * 1024 * 1024
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) throw ListenFormatException("size")
            }
            out.toString("UTF-8")
        } ?: throw ListenFormatException("open")
        val r = ListenCodec.decode(text)
        fileFor(r.id).writeText(ListenCodec.encode(r))
        r
    }

    /** Paylaşım için önbelleğe kopyalar (yalnızca seçilen kayıtlar). */
    suspend fun shareUris(records: List<ListenRecord>): ArrayList<Uri> = withContext(Dispatchers.IO) {
        val share = File(context.cacheDir, "share").apply { mkdirs() }
        share.listFiles()?.filter { it.name.endsWith(".nexlisten.json") }?.forEach { it.delete() }
        ArrayList(
            records.map { r ->
                val f = File(share, ListenCodec.fileName(r))
                f.writeText(ListenCodec.encode(r))
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            },
        )
    }
}
