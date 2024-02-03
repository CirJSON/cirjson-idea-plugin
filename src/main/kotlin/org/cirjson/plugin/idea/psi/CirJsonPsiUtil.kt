package org.cirjson.plugin.idea.psi

import com.intellij.psi.PsiElement

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
     * @return whether this PSI element is property value
     */
    fun isPropertyValue(element: PsiElement): Boolean {
        val parent = element.parent
        return parent is CirJsonProperty && element === parent.nameElement
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