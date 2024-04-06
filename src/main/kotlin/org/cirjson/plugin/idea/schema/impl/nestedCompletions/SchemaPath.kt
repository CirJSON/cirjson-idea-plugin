package org.cirjson.plugin.idea.schema.impl.nestedCompletions

internal data class SchemaPath(val name: String, val previous: SchemaPath?) {

    fun accessor(): List<String> {
        return generateSequence(this) { it.previous }.toList().asReversed().map { it.name }
    }

    fun prefix(): String {
        return accessor().joinToString(".")
    }

}
