package com.eirahoutmoss.nexsense.core.export

import com.eirahoutmoss.nexsense.core.model.SCHEMA_VERSION
import com.eirahoutmoss.nexsense.core.model.ValidationException
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import kotlinx.serialization.json.JsonObject

/**
 * Şema geçişleri. Her yeni şema sürümünde buraya `n -> n+1` adımı eklenir ve
 * docs/VERI-MODELI.md güncellenir. Şu an yalnızca sürüm 1 vardır.
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
        // 1 → güncel: değişiklik yok.
        return root
    }
}
