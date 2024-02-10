package org.cirjson.plugin.idea.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonTokenSets.CIRJSON_COMMENTS

object CirJsonPsiUtil {

    /**
     * Checks that PSI element represents item of CirJSON array.
     *
     * @param element PSI element to check
     * @return whether this PSI element is array element
     */
    fun isArrayElement(element: PsiElement): Boolean {
        return element is CirJsonValue && element.parent is CirJsonArray
    }

    /**
     * Checks that PSI element represents key of CirJSON property (key-value pair of CirJSON object)
     *
     * @param element PSI element to check
     * @return whether this PSI element is property key
     */
    fun isPropertyKey(element: PsiElement): Boolean {
        val parent = element.parent
        return parent is CirJsonProperty && element === parent.nameElement
    }

    /**
     * Checks that PSI element represents value of CirJSON property (key-value pair of CirJSON object)
     *
     * @param element PSI element to check
     *
     * @return whether this PSI element is property value
     */
    fun isPropertyValue(element: PsiElement): Boolean {
        val parent = element.parent
        return parent is CirJsonProperty && element === parent.nameElement
    }

    /**
     * Find the furthest sibling element with the same type as given anchor.
     *
     * Ignore white spaces for any type of element except [CirJsonElementTypes.LINE_COMMENT] where non indentation white
     * space (that has new line in the middle) will stop the search.
     *
     * @param anchor element to start from
     *
     * @param after  whether to scan through sibling elements forward or backward
     *
     * @return described element or anchor if search stops immediately
     */
    fun findFurthestSiblingOfSameType(anchor: PsiElement, after: Boolean): PsiElement {
        var node: ASTNode? = anchor.node
        // Compare by node type to distinguish between different types of comments
        val expectedType = node!!.elementType
        var lastSeen: ASTNode = node

        while (node != null) {
            val elementType = node.elementType

            if (elementType == expectedType) {
                lastSeen = node
            } else if (elementType == TokenType.WHITE_SPACE) {
                if (expectedType == CirJsonElementTypes.LINE_COMMENT &&
                        node.text.indexOf('\n', 1) != -1) {
                    break
                }
            } else if (elementType !in CIRJSON_COMMENTS || expectedType in CIRJSON_COMMENTS) {
                break
            }
            node = if (after) node.treeNext else node.treePrev
        }

        return lastSeen.psi
    }

    /**
     * Check that element type of the given AST node belongs to the token set.
     *
     * It slightly less verbose than `set.contains(node.getElementType())` and overloaded methods with the same name
     * allow check ASTNode/PsiElement against both concrete element types and token sets in uniform way.
     */
    fun hasElementType(node: ASTNode, set: TokenSet): Boolean {
        return node.elementType in set
    }

    /**
     * @see hasElementType
     */
    fun hasElementType(node: ASTNode, vararg types: IElementType): Boolean {
        return hasElementType(node, TokenSet.create(*types))
    }

    /**
     * @see hasElementType
     */
    fun hasElementType(element: PsiElement, set: TokenSet): Boolean {
        return hasElementType(element.node ?: return false, set)
    }

    /**
     * @see hasElementType
     */
    fun hasElementType(element: PsiElement, vararg types: IElementType): Boolean {
        return hasElementType(element.node ?: return false, TokenSet.create(*types))
    }

    /**
     * Returns content of the string literal (without escaping) striving to preserve as much of user data as possible.
     *
     * * If literal length is greater than one, it starts and ends with the same quote, and the last quote is not
     * escaped, returns text without first and last characters.
     * * Otherwise if literal still begins with a quote, returns text without first character only.
     * * Returns unmodified text in all other cases.
     *
     * @param text presumably result of [CirJsonStringLiteral.getText]
     */
    fun stripQuotes(text: String): String {
        if (!text.isEmpty()) {
            val firstChar = text.first()
            val lastChar = text.last()

            if (firstChar == '\'' || firstChar == '"') {
                if (text.length > 1 && firstChar == lastChar && !isEscapedChar(text, text.length - 1)) {
                    return text.substring(1, text.length - 1)
                }

                return text.substring(1)
            }
        }

        return text
    }

    /**
     * Checks that character in given position is escaped with backslashes.
     *
     * @param text text character belongs to
     * @param position position of the character
     * @return whether character at given position is escaped, i.e. preceded by odd number of backslashes
     */
    fun isEscapedChar(text: String, position: Int): Boolean {
        var count = 0

        var i = position - 1
        while (i >= 0 && text[i] == '\\') {
            count++
            i--
        }

        return count % 2 != 0
    }


}