package org.cirjson.plugin.idea.schema.extension.adapters

interface CirJsonPropertyAdapter {

    val name: String?

    val values: Collection<CirJsonValueAdapter>

}