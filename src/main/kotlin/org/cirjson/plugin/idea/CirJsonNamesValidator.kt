package org.cirjson.plugin.idea

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.cirjson.plugin.idea.CirJsonTokenSets.CIRJSON_KEYWORDS

class CirJsonNamesValidator : NamesValidator {

    private val myLexer = CirJsonLexer()

    override fun isKeyword(name: String, project: Project?): Boolean {
        myLexer.start(name)

        return myLexer.tokenType in CIRJSON_KEYWORDS && myLexer.tokenEnd == name.length
    }

    override fun isIdentifier(name: String, project: Project?): Boolean {
        var realName = name

        if (!StringUtil.startsWithChar(realName, '"')) {
            realName = "\"$realName"
        }

        if (!StringUtil.endsWithChar(realName, '"')) {
            realName = "$realName\""
        }

        myLexer.start(name)
        val type = myLexer.tokenType

        return myLexer.tokenEnd == realName.length && type == CirJsonElementTypes.DOUBLE_QUOTED_STRING
    }

}