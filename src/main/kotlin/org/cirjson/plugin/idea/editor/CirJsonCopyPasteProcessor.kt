package org.cirjson.plugin.idea.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

open class CirJsonCopyPasteProcessor : CopyPastePreProcessor {

    override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
        if (!CirJsonEditorOptions.instance.ESCAPE_PASTED_TEXT) {
            return null
        }

        if (!isSupportedFile(file) || startOffsets.size > 1 || endOffsets.size > 1) {
            return null
        }

        val selectionStart = startOffsets[0]
        val selectionEnd = endOffsets[0]
        val literalExpression = getSingleElementFromSelectionOrNull(file, selectionStart, selectionEnd) ?: return null

        return StringUtil.unescapeStringCharacters(StringUtil.replaceUnicodeEscapeSequences(text))
    }

    override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String,
            rawText: RawText): String {
        if (!CirJsonEditorOptions.instance.ESCAPE_PASTED_TEXT) {
            return text
        }

        if (!isSupportedFile(file)) {
            return text
        }

        val selectionModel = editor.selectionModel
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd

        val literalExpression = getSingleElementFromSelectionOrNull(file, selectionStart, selectionEnd) ?: return text

        return StringUtil.escapeStringCharacters(text)
    }

    protected fun isSupportedFile(file: PsiFile): Boolean {
        return file is CirJsonFile && file.isPhysical
    }

    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean {
        return false
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun getSingleElementFromSelectionOrNull(file: PsiFile, start: Int, end: Int): CirJsonStringLiteral? {
            val element = file.findElementAt(start)
            val literalExpression =
                    PsiTreeUtil.getParentOfType(element, CirJsonStringLiteral::class.java) ?: return null
            val textRange = literalExpression.textRange

            if (start <= textRange.startOffset || end >= textRange.endOffset) {
                return null
            }

            val text = literalExpression.text

            if (!text.startsWith("\"") || !text.endsWith("\"")) {
                return null
            }

            return literalExpression
        }

    }

}