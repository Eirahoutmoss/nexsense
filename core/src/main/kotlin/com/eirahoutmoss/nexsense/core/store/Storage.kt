package com.eirahoutmoss.nexsense.core.store

import java.io.File
import java.io.IOException

/** Proje dosyalarının saklandığı yer. Uygulamada cihazın özel klasörüdür; testte geçici klasör. */
interface ProjectStorage {
    fun listIds(): List<String>
    fun read(id: String): String?
    fun write(id: String, text: String)
    fun delete(id: String)
}

/**
 * Her proje `<dir>/<id>.json` dosyasıdır. Yazma atomiktir: önce `.tmp` yazılır, sonra yerine taşınır;
 * yazma yarıda kesilirse eski dosya bozulmadan kalır.
 */
class FileProjectStorage(private val dir: File) : ProjectStorage {

    init {
        dir.mkdirs()
    }

    override fun listIds(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty().map { it.name.removeSuffix(EXT) }.sorted()

    override fun read(id: String): String? {
        val f = fileOf(id)
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    override fun write(id: String, text: String) {
        val target = fileOf(id)
        val tmp = File(dir, "$id$EXT.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            // Bazı dosya sistemlerinde hedef varken renameTo başarısız olur.
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Proje dosyası kaydedilemedi: ${target.name}")
            }
        }
    }

    override fun delete(id: String) {
        fileOf(id).delete()
    }

    private fun fileOf(id: String): File {
        require(SAFE_ID.matches(id)) { "Geçersiz proje kimliği: $id" }
        return File(dir, "$id$EXT")
    }

    private companion object {
        const val EXT = ".json"
        val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}

/** Testler için bellek içi depolama. */
class InMemoryProjectStorage : ProjectStorage {
    private val map = linkedMapOf<String, String>()
    override fun listIds(): List<String> = synchronized(map) { map.keys.sorted() }
    override fun read(id: String): String? = synchronized(map) { map[id] }
    override fun write(id: String, text: String) { synchronized(map) { map[id] = text } }
    override fun delete(id: String) { synchronized(map) { map.remove(id) } }
}
