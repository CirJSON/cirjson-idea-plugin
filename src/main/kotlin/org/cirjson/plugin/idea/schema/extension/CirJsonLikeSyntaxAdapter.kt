package org.cirjson.plugin.idea.schema.extension

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

interface CirJsonLikeSyntaxAdapter {

    fun getPropertyValue(property: PsiElement): PsiElement?

    fun adjustValue(value: PsiElement): PsiElement {
        return value
    }

    fun getPropertyName(property: PsiElement): String?

    fun getDefaultValueFromType(type: CirJsonSchemaType?): String

    fun createProperty(name: String, value: String, element: PsiElement): PsiElement

    fun adjustPropertyAnchor(element: LeafPsiElement): PsiElement

    fun adjustNewProperty(element: PsiElement): PsiElement

    fun ensureComma(self: PsiElement, newElement: PsiElement): Boolean

    fun removeIfComma(forward: PsiElement?)

    fun fixWhitespaceBefore(initialElement: PsiElement, element: PsiElement): Boolean

}