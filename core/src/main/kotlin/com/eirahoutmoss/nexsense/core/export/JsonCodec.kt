package com.eirahoutmoss.nexsense.core.export

import com.eirahoutmoss.nexsense.core.model.BUNDLE_FORMAT
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.Validation
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** JSON proje dosyası: diskte saklama, dışa aktarma ve içe alma aynı biçimi kullanır. */
object JsonCodec {

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        // Daha yeni bir alt sürümün eklediği bilinmeyen alanlar içe almayı bozmasın.
        ignoreUnknownKeys = true
    }

    fun encode(bundle: ProjectBundle): String = json.encodeToString(ProjectBundle.serializer(), bundle)

    /**
     * JSON metnini okur, şemayı denetler ve doğrular.
     * @throws ValidationException dosya NexSense projesi değilse, daha yeni şemadaysa ya da veri tutarsızsa.
     */
    fun decode(text: String): ProjectBundle {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, e.message ?: "json")))
        } catch (e: IllegalArgumentException) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, e.message ?: "json")))
        }
        val format = root["format"]?.jsonPrimitive?.content
        if (format != BUNDLE_FORMAT) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, format ?: "(yok)")))
        }
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 0
        val migrated = Migrations.upgrade(version, root)
        val bundle = try {
            json.decodeFromJsonElement(ProjectBundle.serializer(), migrated)
        } catch (e: SerializationException) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, e.message ?: "json")))
        } catch (e: IllegalArgumentException) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, e.message ?: "json")))
        }
        val issues = Validation.bundle(bundle)
        if (issues.isNotEmpty()) throw ValidationException(issues)
        return bundle
    }
}
