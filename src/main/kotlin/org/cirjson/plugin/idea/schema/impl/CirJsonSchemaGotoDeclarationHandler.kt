package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaGotoDeclarationSuppressor

class CirJsonSchemaGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int,
            editor: Editor?): Array<PsiElement>? {
        sourceElement ?: return null
        val shouldSuppressNavigation = CirJsonSchemaGotoDeclarationSuppressor.EP_NAME.extensionList.any {
            it.shouldSuppressGotoDeclaration(sourceElement)
        }

        if (shouldSuppressNavigation) {
            return null
        }

        val elementType = PsiUtilCore.getElementType(sourceElement)

        if (elementType != CirJsonElementTypes.DOUBLE_QUOTED_STRING && elementType != CirJsonElementTypes.SINGLE_QUOTED_STRING) {
            return null
        }

        val literal = PsiTreeUtil.getParentOfType(sourceElement, CirJsonStringLiteral::class.java) ?: return null
        val parent = literal.parent

        if (literal.references.isNotEmpty() || parent !is CirJsonProperty || parent.nameElement !== literal || !canNavigateToSchema(
                        parent)) {
            return null
        }

        val containingFile = literal.containingFile
        val service = CirJsonSchemaService.get(literal.project)
        val file = containingFile.virtualFile

        if (file == null || !service.isApplicableToFile(file)) {
            return null
        }

        val steps = CirJsonOriginalPsiWalker.INSTANCE.findPosition(literal, true) ?: return null
        val schemaObject = service.getSchemaObject(containingFile) ?: return null
        val target =
                CirJsonSchemaResolver(sourceElement.project, schemaObject, steps).findNavigationTarget(parent.value)
                        ?: return null
        return arrayOf(target)
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun canNavigateToSchema(parent: PsiElement): Boolean {
            return parent.references.none { it is FileReference }
        }

    }

}