package org.cirjson.plugin.idea.schema.extension.adapters

interface CirJsonArrayValueAdapter : CirJsonValueAdapter {

    val elements: List<CirJsonValueAdapter>

    override val isNull: Boolean
        get() = false

}