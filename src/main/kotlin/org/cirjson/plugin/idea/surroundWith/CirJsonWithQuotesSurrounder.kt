package org.cirjson.plugin.idea.surroundWith

import com.intellij.openapi.util.text.StringUtil
import org.cirjson.plugin.idea.CirJsonBundle

class CirJsonWithQuotesSurrounder : CirJsonSurrounderBase() {

    override fun getTemplateDescription(): String {
        return CirJsonBundle.message("surround.with.quotes.desc")
    }

    override fun createReplacementText(textInRange: String): String {
        return "\"${StringUtil.escapeStringCharacters(textInRange)}\""
    }

}