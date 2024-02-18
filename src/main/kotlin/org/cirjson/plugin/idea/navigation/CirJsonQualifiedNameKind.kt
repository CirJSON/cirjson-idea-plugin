package org.cirjson.plugin.idea.navigation

import org.cirjson.plugin.idea.CirJsonBundle

enum class CirJsonQualifiedNameKind(private val description: String) {

    Qualified(CirJsonBundle.message("qualified.name.qualified")),

    CirJsonPointer(CirJsonBundle.message("qualified.name.pointer"));

    override fun toString(): String {
        return description
    }


}