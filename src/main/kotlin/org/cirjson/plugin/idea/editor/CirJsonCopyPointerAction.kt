package org.cirjson.plugin.idea.editor

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonUtil
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameKind
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameProvider

class CirJsonCopyPointerAction : CopyReferenceAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = CirJsonBundle.message("copy.cirjson.pointer")
        val dataContext = e.dataContext
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val file = if (editor != null) {
            FileDocumentManager.getInstance().getFile(editor.document)
        } else {
            null
        }
        e.presentation.isVisible = file != null && CirJsonUtil.isCirJsonFile(file, editor!!.project)
    }

    override fun getPsiElements(dataContext: DataContext?, editor: Editor?): List<PsiElement> {
        val elements = super.getPsiElements(dataContext, editor)

        if (elements.isNotEmpty()) {
            return elements
        }

        val location =
                ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).psiLocation ?: return elements
        val parent = location.parent ?: return elements
        return listOf(parent)
    }

    override fun getQualifiedName(editor: Editor?, elements: List<PsiElement>): String? {
        if (elements.size != 1) {
            return null
        }

        return CirJsonQualifiedNameProvider.generateQualifiedName(elements[0], CirJsonQualifiedNameKind.CirJsonPointer)
    }

}