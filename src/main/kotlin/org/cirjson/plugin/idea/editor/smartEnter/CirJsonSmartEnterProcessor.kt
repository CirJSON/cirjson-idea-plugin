package org.cirjson.plugin.idea.editor.smartEnter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.CirJsonElementTypes.COLON
import org.cirjson.plugin.idea.CirJsonElementTypes.COMMA
import org.cirjson.plugin.idea.psi.*

/**
 * This processor allows to:
 * * Insert colon after key inside object property;
 * * Insert comma after array element, object ID, or object property.
 */
class CirJsonSmartEnterProcessor : SmartEnterProcessorWithFixers() {

    private var myShouldAddNewline = false

    init {
        addFixers(CirJsonObjectIdElementFixer(), CirJsonObjectPropertyFixer(), CirJsonArrayElementFixer())
        addEnterProcessors(CirJsonEnterProcessor())
    }

    override fun collectAdditionalElements(element: PsiElement, result: MutableList<PsiElement>) {
        // include all parents as well
        var parent: PsiElement? = element.parent

        while (parent != null && parent !is CirJsonFile) {
            result.add(parent)
            parent = parent.parent
        }
    }

    private class CirJsonArrayElementFixer : Fixer<CirJsonSmartEnterProcessor>() {

        override fun apply(editor: Editor, processor: CirJsonSmartEnterProcessor, element: PsiElement) {
            if (element is CirJsonValue && element.parent is CirJsonArray) {
                if (terminatedOnCurrentLine(editor, element) && !isFollowedByTerminal(element, COMMA)) {
                    editor.document.insertString(element.textRange.endOffset, ",")
                    processor.myShouldAddNewline = true
                }
            }
        }

    }

    private class CirJsonObjectPropertyFixer : Fixer<CirJsonSmartEnterProcessor>() {

        override fun apply(editor: Editor, processor: CirJsonSmartEnterProcessor, element: PsiElement) {
            if (element !is CirJsonProperty) {
                return
            }

            val propertyValue = element.value

            if (propertyValue != null) {
                if (terminatedOnCurrentLine(editor, propertyValue) && !isFollowedByTerminal(propertyValue, COMMA)) {
                    editor.document.insertString(propertyValue.textRange.endOffset, ",")
                    processor.myShouldAddNewline = true
                }

                return
            }

            val propertyKey = element.nameElement
            val keyRange = propertyKey.textRange
            val keyStartOffset = keyRange.startOffset
            var keyEndOffset = keyRange.endOffset

            if (terminatedOnCurrentLine(editor, propertyKey) && !isFollowedByTerminal(propertyKey, COLON)) {
                val shouldQuoteKey = propertyKey is CirJsonReferenceExpression

                if (shouldQuoteKey) {
                    editor.document.insertString(keyStartOffset, "\"")
                    keyEndOffset++
                    editor.document.insertString(keyEndOffset, "\"")
                    keyEndOffset++
                }

                processor.myFirstErrorOffset = keyEndOffset + 2
                editor.document.insertString(keyEndOffset, ": ")
            }
        }

    }

    private class CirJsonObjectIdElementFixer : Fixer<CirJsonSmartEnterProcessor>() {

        override fun apply(editor: Editor, processor: CirJsonSmartEnterProcessor, element: PsiElement) {
            if (element !is CirJsonObjectIdElement) {
                return
            }

            val propertyValue = element.stringLiteral

            if (terminatedOnCurrentLine(editor, propertyValue) && !isFollowedByTerminal(propertyValue, COMMA)) {
                editor.document.insertString(propertyValue.textRange.endOffset, ",")
                processor.myShouldAddNewline = true
            }
        }

    }

    private inner class CirJsonEnterProcessor : FixEnterProcessor() {

        override fun doEnter(atCaret: PsiElement?, file: PsiFile?, editor: Editor, modified: Boolean): Boolean {
            if (myShouldAddNewline) {
                try {
                    plainEnter(editor)
                } finally {
                    myShouldAddNewline = false
                }
            }

            return true
        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun terminatedOnCurrentLine(editor: Editor, element: PsiElement): Boolean {
            val document = editor.document
            val caretOffset = editor.caretModel.currentCaret.offset
            val elementEndOffset = element.textRange.endOffset

            if (document.getLineNumber(elementEndOffset) != document.getLineNumber(caretOffset)) {
                return false
            }

            // Skip empty PsiError elements if comma is missing
            val nextLeaf = PsiTreeUtil.nextLeaf(element, true)
            return nextLeaf == null || (nextLeaf is PsiWhiteSpace && "\n" in nextLeaf.text)
        }

        private fun isFollowedByTerminal(element: PsiElement, type: IElementType): Boolean {
            val nextLeaf = PsiTreeUtil.nextVisibleLeaf(element)
            return nextLeaf != null && nextLeaf.node.elementType == type
        }

    }

}