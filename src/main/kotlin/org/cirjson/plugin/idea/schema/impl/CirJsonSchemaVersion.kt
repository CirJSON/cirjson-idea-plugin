package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.util.text.StringUtil

enum class CirJsonSchemaVersion {

    SCHEMA_4,

    SCHEMA_6,

    SCHEMA_7;

    companion object {

        private const val ourSchemaV4Schema = "http://json-schema.org/draft-04/schema"

        private const val ourSchemaV6Schema = "http://json-schema.org/draft-06/schema"

        private const val ourSchemaV7Schema = "http://json-schema.org/draft-07/schema"

        private const val ourSchemaOrgPrefix = "http://json-schema.org/"

        fun byId(id: String): CirJsonSchemaVersion? {
            var id = id
            if (id.startsWith("https://")) {
                id = "http://" + id.substring("https://".length)
            }
            return when (StringUtil.trimEnd(id, '#')) {
                ourSchemaV4Schema -> SCHEMA_4
                ourSchemaV6Schema -> SCHEMA_6
                ourSchemaV7Schema -> SCHEMA_7
                else -> if (id.startsWith(ourSchemaOrgPrefix)) SCHEMA_7 else null
            }
        }

        fun isSchemaSchemaId(id: String?): Boolean {
            return id != null && byId(id) != null
        }

    }

}