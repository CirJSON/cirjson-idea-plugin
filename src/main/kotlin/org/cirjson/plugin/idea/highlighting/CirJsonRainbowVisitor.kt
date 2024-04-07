package org.cirjson.plugin.idea.highlighting

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.impl.CirJsonOriginalPsiWalker

class CirJsonRainbowVisitor : RainbowVisitor() {

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is CirJsonFile
    }

    override fun visit(element: PsiElement) {
        if (element !is CirJsonProperty) {
            return
        }

        val file = element.containingFile
        val fileName = file.name

        if (fileName in BLACKLIST) {
            val position = CirJsonOriginalPsiWalker.INSTANCE.findPosition(element, false)

            if (position != null && position.toCirJsonPointer() in BLACKLIST[fileName]!!) {
                return
            }
        }

        val name = element.name
        addInfo(getInfo(file, element.nameElement, name, CirJsonSyntaxHighlighterFactory.CIRJSON_PROPERTY_KEY))
        val value = element.value

        if (value is CirJsonObject) {
            addInfo(getInfo(file, value.firstChild, name, CirJsonSyntaxHighlighterFactory.CIRJSON_BRACES))
            addInfo(getInfo(file, value.lastChild, name, CirJsonSyntaxHighlighterFactory.CIRJSON_BRACES))
        } else if (value is CirJsonArray) {
            addInfo(getInfo(file, value.firstChild, name, CirJsonSyntaxHighlighterFactory.CIRJSON_BRACKETS))
            addInfo(getInfo(file, value.lastChild, name, CirJsonSyntaxHighlighterFactory.CIRJSON_BRACKETS))
            for (cirJsonValue in value.valueList) {
                addSimpleValueInfo(name, file, cirJsonValue)
            }
        } else {
            addSimpleValueInfo(name, file, value)
        }
    }

    private fun addSimpleValueInfo(name: String, file: PsiFile, value: CirJsonValue?) {
        when (value) {
            is CirJsonStringLiteral -> {
                addInfo(getInfo(file, value, name, CirJsonSyntaxHighlighterFactory.CIRJSON_STRING))
            }

            is CirJsonNumberLiteral -> {
                addInfo(getInfo(file, value, name, CirJsonSyntaxHighlighterFactory.CIRJSON_NUMBER))
            }

            is CirJsonLiteral -> {
                addInfo(getInfo(file, value, name, CirJsonSyntaxHighlighterFactory.CIRJSON_KEYWORD))
            }
        }
    }

    override fun clone(): HighlightVisitor {
        return CirJsonRainbowVisitor()
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val BLACKLIST =
                mapOf("package.json" to setOf("/dependencies", "/devDependencies", "/peerDependencies", "/scripts",
                        "/directories", "/optionalDependencies"))

    }

}