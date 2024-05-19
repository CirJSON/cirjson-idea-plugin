package org.cirjson.plugin.idea

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.WebReference
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonWebReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val pattern = PlatformPatterns.psiElement(CirJsonStringLiteral::class.java)
        val priority = PsiReferenceRegistrar.LOWER_PRIORITY

        val provider = object : PsiReferenceProvider() {

            override fun acceptsTarget(target: PsiElement): Boolean {
                return false
            }

            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                if (element !is CirJsonStringLiteral) {
                    return PsiReference.EMPTY_ARRAY
                }

                val parent = element.parent

                if (parent !is CirJsonProperty) {
                    return PsiReference.EMPTY_ARRAY
                }

                val cirJsonValueElement = parent.value

                if (element !== cirJsonValueElement) {
                    return PsiReference.EMPTY_ARRAY
                }

                if (element.textLength > 1000 || !element.textContains(':')) {
                    return PsiReference.EMPTY_ARRAY
                }

                val textValue = element.text

                if (GlobalPathReferenceProvider.isWebReferenceUrl(textValue)) {
                    val valueTextRange = ElementManipulators.getValueTextRange(element)

                    if (valueTextRange.isEmpty) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    return arrayOf(WebReference(element, valueTextRange, textValue))
                }

                return PsiReference.EMPTY_ARRAY
            }

        }

        registrar.registerReferenceProvider(pattern, provider, priority)
    }

}