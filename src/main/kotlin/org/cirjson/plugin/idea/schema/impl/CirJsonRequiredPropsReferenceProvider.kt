package org.cirjson.plugin.idea.schema.impl

import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonRequiredPropsReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        return arrayOf(CirJsonRequiredPropReference(element as CirJsonStringLiteral))
    }

    private class CirJsonRequiredPropReference(element: CirJsonStringLiteral) :
            CirJsonSchemaBaseReference<CirJsonStringLiteral>(element, ElementManipulators.getValueTextRange(element)) {

        override fun resolveInner(): PsiElement? {
            val propertiesObject = findPropertiesObject(element) ?: return null
            val name = element.name

            for (property in propertiesObject.propertyList) {
                if (property.name == name) {
                    return property
                }
            }

            return null
        }

    }

    companion object {

        fun findPropertiesObject(element: PsiElement): CirJsonObject? {
            val parent = getParentSafe(getParentSafe(getParentSafe(element)))

            if (parent !is CirJsonObject) {
                return null
            }

            val propertiesProp = parent.propertyList.firstOrNull { "properties" == it.name } ?: return null
            val value = propertiesProp.value

            if (value is CirJsonObject) {
                return value
            }

            return null
        }

        private fun getParentSafe(element: PsiElement?): PsiElement? {
            return element?.parent
        }

    }

}