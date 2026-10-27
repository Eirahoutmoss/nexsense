package com.eirahoutmoss.nexsense.core.export

import com.eirahoutmoss.nexsense.core.model.SCHEMA_VERSION
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Şema geçişleri. Her yeni şema sürümünde buraya `n -> n+1` adımı eklenir ve
 * docs/VERI-MODELI.md güncellenir. Sürümler: 1 (ilk MVP), 2 (taramalar, analiz ayarları).
 */
object Migrations {
    fun upgrade(fromVersion: Int, root: JsonObject): JsonObject {
        if (fromVersion > SCHEMA_VERSION) {
            throw ValidationException(
                listOf(ValidationIssue(ValidationIssue.Code.NEWER_SCHEMA, "$fromVersion > $SCHEMA_VERSION")),
            )
        }
        if (fromVersion < 1) {
            throw ValidationException(listOf(ValidationIssue(ValidationIssue.Code.BAD_FORMAT, "schemaVersion=$fromVersion")))
        }
        var r = root
        if (fromVersion == 1) {
            // 1 → 2: yeni alanlar (scans, analysis, northAngleDeg, rotationMatrix) varsayılanla gelir;
            // yalnızca sürüm numaraları yükseltilir. Ölçüm verisi değişmez.
            val project = (r["project"] as? JsonObject)?.let { JsonObject(it + ("schemaVersion" to JsonPrimitive(2))) }
            r = JsonObject(r + ("schemaVersion" to JsonPrimitive(2)) + (if (project != null) mapOf("project" to project) else emptyMap()))
        }
        return r
    }
}
