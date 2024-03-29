package org.cirjson.plugin.idea.schema.extension.adapters

interface CirJsonObjectValueAdapter : CirJsonValueAdapter {

    val propertyList: List<CirJsonPropertyAdapter>

    override val isNull: Boolean
        get() = false

}