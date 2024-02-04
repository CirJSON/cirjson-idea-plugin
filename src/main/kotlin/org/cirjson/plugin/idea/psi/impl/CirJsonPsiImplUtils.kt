package org.cirjson.plugin.idea.psi.impl

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import kotlinx.collections.immutable.toImmutableList
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonTokenSets.STRING_LITERALS
import org.cirjson.plugin.idea.psi.*
import javax.swing.Icon

object CirJsonPsiImplUtils {

    @JvmStatic
    fun getName(property: CirJsonProperty): String {
        return StringUtil.unescapeStringCharacters(CirJsonPsiUtil.stripQuotes(property.nameElement.text))
    }

    @JvmStatic
    fun getNameElement(property: CirJsonProperty): CirJsonValue {
        val firstChild = property.firstChild
        assert(firstChild is CirJsonLiteral || firstChild is CirJsonReferenceExpression)
        return firstChild as CirJsonValue
    }

    @JvmStatic
    fun getValue(property: CirJsonProperty): CirJsonValue? {
        return PsiTreeUtil.getNextSiblingOfType(getNameElement(property), CirJsonValue::class.java)
    }

    @JvmStatic
    fun getPresentation(property: CirJsonProperty): ItemPresentation {
        return object : ItemPresentation {

            override fun getPresentableText(): String {
                return property.name
            }

            override fun getLocationString(): String? {
                val value = property.value
                return if (value is CirJsonLiteral) value.text else null
            }

            override fun getIcon(p0: Boolean): Icon? {
                return when (property.value) {
                    is CirJsonArray -> AllIcons.Json.Array
                    is CirJsonObject -> AllIcons.Json.Object
                    else -> PlatformIcons.PROPERTY_ICON
                }
            }

        }
    }

    @JvmStatic
    fun getPresentation(array: CirJsonArray): ItemPresentation {
        return object : ItemPresentation {

            override fun getPresentableText(): String {
                val result = StringBuilder(CirJsonBundle.message("cirjson.array") + " ")
                val id = array.id
                if (id != null) {
                    result.append(CirJsonBundle.message("cirjson.with_id") + " ")
                    result.append("\"$id\"")
                } else {
                    result.append(CirJsonBundle.message("cirjson.no_id"))
                }
                return result.toString()
            }

            override fun getIcon(p0: Boolean): Icon {
                return AllIcons.Json.Array
            }

        }
    }

    @JvmStatic
    fun getId(array: CirJsonArray): String? {
        val values = array.valueList
        if (values.isEmpty()) {
            return null
        }

        val firstElement = array.valueList[0]
        if (firstElement !is CirJsonStringLiteral || !firstElement.isId) {
            return null
        }

        return firstElement.value
    }

    @JvmStatic
    fun getId(obj: CirJsonObject): String? {
        return obj.objectIdElement?.id
    }

    @JvmStatic
    fun getId(objectIdElement: CirJsonObjectIdElement): String {
        return objectIdElement.stringLiteral.value
    }

    @JvmStatic
    fun getPresentation(obj: CirJsonObject): ItemPresentation {
        return object : ItemPresentation {

            override fun getPresentableText(): String {
                val result = StringBuilder(CirJsonBundle.message("cirjson.object") + " ")
                if (obj.id != null) {
                    result.append(CirJsonBundle.message("cirjson.with_id") + " ")
                    result.append("\"${obj.id}\"")
                } else {
                    result.append(CirJsonBundle.message("cirjson.no_id"))
                }
                return result.toString()
            }

            override fun getIcon(p0: Boolean): Icon {
                return AllIcons.Json.Array
            }

        }
    }

    @JvmStatic
    fun getTextFragments(literal: CirJsonStringLiteral): List<Pair<TextRange, String>> {
        val result = (literal.getUserData(STRING_FRAGMENTS).also {
            if (it != null) {
                return it
            }
        } ?: arrayListOf()).toMutableList()

        val text = literal.text
        val length = text.length
        var pos = 1
        var unescapedSequenceStart = 1

        while (pos < length) {
            if (text[pos] != '\\') {
                pos++
                continue
            }

            if (unescapedSequenceStart != pos) {
                result.add(TextRange(unescapedSequenceStart, pos) to text.substring(unescapedSequenceStart, pos))
            }

            if (pos == length - 1) {
                result.add(TextRange(pos, pos + 1) to "\\")
                break
            }

            when (val next = text[pos + 1]) {
                '"', '\\', '/', 'b', 'f', 'n', 't', 'r' -> {
                    val idx = ourEscapesTable.indexOf(next)
                    result.add(TextRange(pos, pos + 2) to ourEscapesTable.substring(idx, idx + 2))
                    pos += 2
                }

                'u' -> {
                    var i = pos + 2

                    while (i < pos + 6) {
                        if (i == length || !StringUtil.isHexDigit(text[i])) {
                            break
                        }
                        i++
                    }

                    result.add(TextRange(pos, i) to text.substring(pos, i))
                    pos = i
                }

                else -> {
                    result.add(TextRange(pos, pos + 2) to text.substring(pos, pos + 2))
                    pos += 2
                }
            }
            unescapedSequenceStart = pos
        }

        val contentEnd = if (text[0] == text[length - 1]) length - 1 else length

        if (unescapedSequenceStart < contentEnd) {
            result.add(
                    TextRange(unescapedSequenceStart, contentEnd) to text.substring(unescapedSequenceStart, contentEnd))
        }

        return result.toImmutableList().also { literal.putUserData(STRING_FRAGMENTS, it) }
    }

    @JvmStatic
    fun getValue(literal: CirJsonStringLiteral): String {
        return StringUtil.unescapeStringCharacters(CirJsonPsiUtil.stripQuotes(literal.text))
    }

    @JvmStatic
    fun isPropertyName(literal: CirJsonStringLiteral): Boolean {
        val parent = literal.parent
        return parent is CirJsonProperty && parent.nameElement === literal
    }

    @JvmStatic
    fun isId(literal: CirJsonStringLiteral): Boolean {
        return getValue(literal).isNotEmpty()
    }

    @JvmStatic
    fun getValue(literal: CirJsonBooleanLiteral): Boolean {
        return literal.textMatches("true")
    }

    @JvmStatic
    fun getValue(literal: CirJsonNumberLiteral): Double {
        return literal.text.toDouble()
    }

    @JvmStatic
    fun isQuotedString(literal: CirJsonLiteral): Boolean {
        return literal.node.findChildByType(STRING_LITERALS) != null
    }

    internal val STRING_FRAGMENTS = Key<List<Pair<TextRange, String>>>("CirJSON string fragments")

    private const val ourEscapesTable = "\"\"\\\\//b\bf\u000cn\nr\rt\t"

}