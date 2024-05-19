package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.util.text.StringUtil

enum class CirJsonSchemaVersion {

    SCHEMA_1;

    companion object {

        private const val ourSchemaV1Schema = "http://cirjson.org/draft-01/schema"

        private const val ourSchemaOrgPrefix = "http://cirjson.org/"

        fun byId(id: String): CirJsonSchemaVersion? {
            var realId = id
            if (realId.startsWith("https://")) {
                realId = "http://" + realId.substring("https://".length)
            }
            return when (StringUtil.trimEnd(realId, '#')) {
                ourSchemaV1Schema -> SCHEMA_1
                else -> if (realId.startsWith(ourSchemaOrgPrefix)) SCHEMA_1 else null
            }
        }

        fun isSchemaSchemaId(id: String?): Boolean {
            return id != null && byId(id) != null
        }

    }

}