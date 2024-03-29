package org.cirjson.plugin.idea.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.cirjson.plugin.idea.psi.CirJsonProperty

class CirJsonPropertyNameReference(private val myProperty: CirJsonProperty) : PsiReference {

    override fun getElement(): PsiElement {
        return myProperty
    }

    override fun getRangeInElement(): TextRange {
        val nameElement = myProperty.nameElement
        return ElementManipulators.getValueTextRange(nameElement)
    }

    override fun resolve(): PsiElement {
        return myProperty
    }

    override fun getCanonicalText(): String {
        return myProperty.name
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        return myProperty.setName(newElementName)
    }

    override fun bindToElement(element: PsiElement): PsiElement? {
        return null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is CirJsonProperty) {
            return false
        }

        val selfResolve = resolve()
        return element.name == canonicalText && selfResolve !== element
    }

    override fun isSoft(): Boolean {
        return true
    }

}