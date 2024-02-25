package org.cirjson.plugin.idea.surroundWith

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import org.cirjson.plugin.idea.CirJsonElementTypes.COMMA
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonSurroundDescriptor : SurroundDescriptor {

    override fun getElementsToSurround(file: PsiFile, start: Int, end: Int): Array<PsiElement> {
        var startOffset = start
        var endOffset = end
        var firstElement = file.findElementAt(startOffset)
        var lastElement = file.findElementAt(endOffset - 1)

        // Extend selection beyond possible delimiters
        while (firstElement != null && (firstElement is PsiWhiteSpace || firstElement.node.elementType == COMMA)) {
            firstElement = firstElement.nextSibling
        }

        while (lastElement != null && (lastElement is PsiWhiteSpace || lastElement.node.elementType == COMMA)) {
            lastElement = lastElement.prevSibling
        }

        if (firstElement != null) {
            startOffset = firstElement.textRange.startOffset
        }

        if (lastElement != null) {
            endOffset = lastElement.textRange.endOffset
        }

        val property = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, CirJsonProperty::class.java)

        if (property != null) {
            return collectElements(endOffset, property, CirJsonProperty::class.java)
        }

        val value = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, CirJsonValue::class.java)

        if (value != null) {
            return collectElements(endOffset, value, CirJsonValue::class.java)
        }

        return PsiElement.EMPTY_ARRAY
    }

    override fun getSurrounders(): Array<Surrounder> {
        return ourSurrenders
    }

    override fun isExclusive(): Boolean {
        return false
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val ourSurrenders = arrayOf<Surrounder>(CirJsonWithObjectLiteralSurrounder(),
                CirJsonWithArrayLiteralSurrounder(), CirJsonWithQuotesSurrounder())

        private fun <T : PsiElement?> collectElements(endOffset: Int, property: T, kind: Class<T>): Array<PsiElement> {
            val properties = SmartList(property)
            var nextSibling: PsiElement? = property!!.nextSibling

            while (nextSibling != null && nextSibling.textRange.endOffset <= endOffset) {
                if (kind.isInstance(nextSibling)) {
                    properties.add(kind.cast(nextSibling))
                }

                nextSibling = nextSibling.nextSibling
            }

            return properties.toArray(PsiElement.EMPTY_ARRAY)
        }

    }

}