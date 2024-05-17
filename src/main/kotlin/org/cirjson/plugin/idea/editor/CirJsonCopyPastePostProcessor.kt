package org.cirjson.plugin.idea.editor

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonProperty
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.util.*

class CirJsonCopyPastePostProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {

    override fun collectTransferableData(file: PsiFile, editor: Editor, startOffsets: IntArray,
            endOffsets: IntArray): MutableList<TextBlockTransferableData> {
        return ContainerUtil.emptyList()
    }

    override fun extractTransferableData(content: Transferable): MutableList<TextBlockTransferableData> {
        return DATA_LIST
    }

    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean {
        return false
    }

    override fun processTransferableData(project: Project, editor: Editor, bounds: RangeMarker, caretOffset: Int,
            indented: Ref<in Boolean>, values: MutableList<out TextBlockTransferableData>) {
        fixCommasOnPaste(project, editor, bounds)
    }

    internal class DumbData : TextBlockTransferableData {

        override fun getFlavor(): DataFlavor {
            return DATA_FLAVOR
        }

        companion object {

            private val DATA_FLAVOR =
                    DataFlavor(CirJsonCopyPastePostProcessor::class.java, "class: CirJsonCopyPastePostProcessor")

        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        internal val DATA_LIST = Collections.singletonList<TextBlockTransferableData>(DumbData())

        private fun fixCommasOnPaste(project: Project, editor: Editor, bounds: RangeMarker) {
            if (!CirJsonEditorOptions.instance.COMMA_ON_PASTE) {
                return
            }

            if (!isCirJsonEditor(project, editor)) {
                return
            }

            val manager = PsiDocumentManager.getInstance(project)
            manager.commitDocument(editor.document)
            val psiFile = manager.getPsiFile(editor.document) ?: return
            fixTrailingComma(bounds, psiFile, manager)
            fixLeadingComma(bounds, psiFile, manager)
        }

        private fun isCirJsonEditor(project: Project, editor: Editor): Boolean {
            val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
            val fileType = file.fileType

            if (fileType is CirJsonFileType) {
                return true
            }

            if (!ScratchUtil.isScratch(file)) {
                return false
            }

            return PsiDocumentManager.getInstance(project).getPsiFile(editor.document) is CirJsonFile
        }

        private fun fixTrailingComma(bounds: RangeMarker, psiFile: PsiFile, manager: PsiDocumentManager) {
            var endElement = skipWhitespaces(psiFile.findElementAt(bounds.endOffset - 1))

            if (endElement != null && endElement.textOffset >= bounds.endOffset) {
                endElement = PsiTreeUtil.skipWhitespacesBackward(endElement)
            }

            if (endElement is LeafPsiElement && endElement.elementType == CirJsonElementTypes.COMMA) {
                val nextNext = skipWhitespaces(endElement.nextSibling)

                if (nextNext is LeafPsiElement && (nextNext.elementType == CirJsonElementTypes.R_CURLY
                                || nextNext.elementType == CirJsonElementTypes.R_BRACKET)) {
                    val finalEndElement = endElement
                    ApplicationManager.getApplication().runWriteAction { finalEndElement.delete() }
                }
            } else {
                val property = getParentPropertyOrArrayItem(endElement)

                if (endElement is PsiErrorElement || property != null && skipWhitespaces(
                                property.nextSibling) is PsiErrorElement) {
                    val finalEndElement = endElement
                    ApplicationManager.getApplication()
                            .runWriteAction { bounds.document.insertString(getOffset(property, finalEndElement), ",") }
                    manager.commitDocument(bounds.document)
                }
            }
        }

        private fun skipWhitespaces(element: PsiElement?): PsiElement? {
            var realElement = element

            while (realElement is PsiWhiteSpace) {
                realElement = realElement.nextSibling
            }

            return realElement
        }

        private fun getParentPropertyOrArrayItem(startElement: PsiElement?): PsiElement? {
            startElement ?: return null

            val propertyOrArrayItem =
                    PsiTreeUtil.getParentOfType(startElement, CirJsonProperty::class.java, CirJsonArray::class.java)

            if (propertyOrArrayItem !is CirJsonArray) {
                return propertyOrArrayItem
            }

            for (value in propertyOrArrayItem.valueList) {
                if (PsiTreeUtil.isAncestor(value, startElement, false)) {
                    return value
                }
            }

            return null
        }

        private fun getOffset(property: PsiElement?, finalEndElement: PsiElement?): Int {
            if (finalEndElement is PsiErrorElement) {
                return finalEndElement.textOffset
            }

            return property?.textRange?.endOffset ?: finalEndElement!!.textOffset
        }

        private fun fixLeadingComma(bounds: RangeMarker, psiFile: PsiFile, manager: PsiDocumentManager) {
            val startElement = skipWhitespaces(psiFile.findElementAt(bounds.startOffset))
            val propertyOrArrayItem =
                    startElement as? CirJsonProperty ?: getParentPropertyOrArrayItem(startElement) ?: return

            val prevSibling = PsiTreeUtil.skipWhitespacesBackward(propertyOrArrayItem)

            if (prevSibling is PsiErrorElement) {
                val offset = prevSibling.textRange.endOffset
                ApplicationManager.getApplication().runWriteAction { bounds.document.insertString(offset, ",") }
                manager.commitDocument(bounds.document)
            }
        }

    }

}