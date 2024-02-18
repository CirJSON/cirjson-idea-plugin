package org.cirjson.plugin.idea

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonValue

object CirJsonUtil {

    /**
     * Clone of C# "as" operator.
     *
     * Checks if expression has correct type and casts it if it has. Returns null otherwise. It saves coder from
     * "instanceof / cast" chains.
     *
     * Copied from PyCharm's `PyUtil`.
     *
     * @param expression expression to check
     * @param cls class to cast
     * @param T class to cast
     * @return The expression cast to the appropriate type (if it could be cast). `null` otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> asClass(expression: Any?, cls: Class<T?>): T? {
        if (expression == null) {
            return null
        }

        if (cls.isAssignableFrom(expression::class.java)) {
            return expression as T?
        }

        return null
    }

    fun isArrayElement(element: PsiElement): Boolean {
        return element is CirJsonValue && element.parent is CirJsonArray
    }

    fun getArrayIndexOfItem(element: PsiElement): Int {
        val parent = element.parent

        if (parent !is CirJsonArray) {
            return -1
        }

        val elements = parent.valueList

        for (indexedValue in elements.withIndex()) {
            if (element === indexedValue.value) {
                return indexedValue.index
            }
        }

        return -1
    }

}