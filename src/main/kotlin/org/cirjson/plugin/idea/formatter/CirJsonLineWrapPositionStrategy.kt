package org.cirjson.plugin.idea.formatter

import com.intellij.openapi.editor.DefaultLineWrapPositionStrategy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.cirjson.plugin.idea.CirJsonElementTypes
import kotlin.math.max

class CirJsonLineWrapPositionStrategy : DefaultLineWrapPositionStrategy() {

    override fun calculateWrapPosition(document: Document, project: Project?, startOffset: Int, endOffset: Int,
            maxPreferredOffset: Int, allowToBeyondMaxPreferredOffset: Boolean, isSoftWrap: Boolean): Int {
        if (isSoftWrap) {
            return super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset,
                    allowToBeyondMaxPreferredOffset, true)
        }

        if (project == null) {
            return -1
        }

        val wrapPosition = getMinWrapPosition(document, project, maxPreferredOffset)

        if (wrapPosition == SKIP_WRAPPING) {
            return -1
        }

        val minWrapPosition = max(startOffset, wrapPosition)
        return super.calculateWrapPosition(document, project, minWrapPosition, endOffset, maxPreferredOffset,
                allowToBeyondMaxPreferredOffset, false)
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private const val SKIP_WRAPPING = -2

        private fun getMinWrapPosition(document: Document, project: Project, offset: Int): Int {
            val manager = PsiDocumentManager.getInstance(project)

            if (manager.isUncommited(document)) {
                manager.commitDocument(document)
            }

            val psiFile = manager.getPsiFile(document) ?: return -1
            val currElement = psiFile.findElementAt(offset)
            val elementType = PsiUtilCore.getElementType(currElement)

            when (elementType) {
                CirJsonElementTypes.DOUBLE_QUOTED_STRING, CirJsonElementTypes.SINGLE_QUOTED_STRING,
                CirJsonElementTypes.LITERAL, CirJsonElementTypes.BOOLEAN_LITERAL, CirJsonElementTypes.TRUE,
                CirJsonElementTypes.FALSE, CirJsonElementTypes.IDENTIFIER, CirJsonElementTypes.NULL,
                CirJsonElementTypes.NULL_LITERAL -> {
                    return currElement!!.textRange.endOffset
                }

                CirJsonElementTypes.COLON -> return SKIP_WRAPPING
            }

            if (currElement != null) {
                if (currElement is PsiComment || PsiUtilCore.getElementType(
                                PsiTreeUtil.skipWhitespacesForward(currElement)) == CirJsonElementTypes.COMMA) {
                    return SKIP_WRAPPING
                }
            }

            return -1
        }

    }

}