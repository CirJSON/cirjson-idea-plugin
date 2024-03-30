package org.cirjson.plugin.idea.codeinsight

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonDialectUtil
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class StandardCirJsonLiteralChecker : CirJsonLiteralChecker {

    override fun getErrorForNumericLiteral(literalText: String): String? {
        if (INF != literalText && MINUS_INF != literalText && NAN != literalText
                && !VALID_NUMBER_LITERAL.matches(literalText)) {
            return CirJsonBundle.message("syntax.error.illegal.floating.point.literal")
        }

        return null
    }

    override fun getErrorForStringFragment(fragmentText: Pair<TextRange, String>,
            stringLiteral: CirJsonStringLiteral): Pair<TextRange, String>? {
        if (fragmentText.second.any { it.code <= '\u001F'.code }) {
            val text = stringLiteral.text

            if (fragmentText.first in TextRange(0, text.length)) {
                val startOffset = fragmentText.first.startOffset
                val part = text.substring(startOffset, fragmentText.first.endOffset)
                val array = part.toCharArray()

                for (i in array.indices) {
                    val c = array[i]

                    if (c.code <= '\u001F'.code) {
                        return Pair.create(TextRange(startOffset + i, startOffset + i + 1),
                                CirJsonBundle.message("syntax.error.control.char.in.string",
                                        "\\u${Integer.toHexString(c.code or 0x10000).substring(1)}"))
                    }
                }
            }
        }

        val error = getStringError(fragmentText.second) ?: return null
        return Pair.create(fragmentText.first, error)
    }

    fun getStringError(fragmentText: String): String? {
        if (!fragmentText.startsWith("\\") || fragmentText.length <= 1 || VALID_ESCAPE.matches(fragmentText)) {
            return null
        }

        return if (fragmentText.startsWith("\\u")) {
            CirJsonBundle.message("syntax.error.illegal.unicode.escape.sequence")
        } else {
            CirJsonBundle.message("syntax.error.illegal.escape.sequence")
        }
    }

    override fun isApplicable(element: PsiElement): Boolean {
        return CirJsonDialectUtil.isStandardCirJson(element)
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        val VALID_ESCAPE = Regex("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})")

        val VALID_NUMBER_LITERAL = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

        const val INF: String = "Infinity"

        const val MINUS_INF: String = "-Infinity"

        const val NAN: String = "NaN"

    }

}