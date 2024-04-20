package org.cirjson.plugin.idea.schema.impl

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference
import com.intellij.util.ObjectUtils
import org.cirjson.plugin.idea.schema.CirJsonSchemaService

class CirJsonSchemaDocumentationProvider {

    companion object {

        fun findSchemaAndGenerateDoc(element: PsiElement, originalElement: PsiElement?, preferShort: Boolean,
                forcedPropName: String?): String? {
            var realElement = element

            if (realElement is FakePsiElement) {
                return null
            }

            realElement =
                    if (isWhitespaceOrComment(originalElement)) realElement else ObjectUtils.coalesce(originalElement,
                            realElement)

            if (originalElement != null && hasFileOrPointerReferences(originalElement.references)) {
                return null
            }

            val containingFile = realElement.containingFile ?: return null
            val service = CirJsonSchemaService.get(realElement.project)
            val virtualFile = containingFile.viewProvider.virtualFile

            if (!service.isApplicableToFile(virtualFile)) {
                return null
            }

            val rootSchema = service.getSchemaObject(containingFile) ?: return null

            return generateDoc(realElement, rootSchema, preferShort, forcedPropName)
        }

        private fun isWhitespaceOrComment(originalElement: PsiElement?): Boolean {
            return originalElement is PsiWhiteSpace || originalElement is PsiComment
        }

        private fun hasFileOrPointerReferences(references: Array<PsiReference>): Boolean {
            for (reference in references) {
                if (reference is PsiFileReference
                        || reference is CirJsonPointerReferenceProvider.CirJsonSchemaIdReference
                        || reference is CirJsonPointerReferenceProvider.CirJsonPointerReference) {
                    return true
                }
            }

            return false
        }

        fun generateDoc(element: PsiElement, rootSchema: CirJsonSchemaObject, preferShort: Boolean,
                forcedPropName: String?): String? {
            TODO()
        }

        fun getBestDocumentation(preferShort: Boolean, schema: CirJsonSchemaObject): String? {
            TODO()
        }

    }

}