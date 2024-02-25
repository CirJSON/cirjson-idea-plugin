package org.cirjson.plugin.idea.surroundWith

import org.cirjson.plugin.idea.CirJsonBundle

class CirJsonWithArrayLiteralSurrounder : CirJsonSurrounderBase() {

    override fun getTemplateDescription(): String {
        return CirJsonBundle.message("surround.with.array.literal.desc")
    }

    override fun createReplacementText(textInRange: String): String {
        return "[$textInRange]"
    }

}