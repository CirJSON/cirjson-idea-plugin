package org.cirjson.plugin.idea.schema

data class CirJsonSchemaCatalogEntry(val fileMasks: Collection<String>, val url: String, val name: String,
        val description: String)
