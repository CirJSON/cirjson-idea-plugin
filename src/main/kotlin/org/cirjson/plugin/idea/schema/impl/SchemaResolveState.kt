package org.cirjson.plugin.idea.schema.impl

enum class SchemaResolveState {

    normal,

    conflict,

    brokenDefinition,

    cyclicDefinition

}