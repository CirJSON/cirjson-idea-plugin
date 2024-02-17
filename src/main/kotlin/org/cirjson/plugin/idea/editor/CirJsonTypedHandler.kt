package org.cirjson.plugin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.cirjson.plugin.idea.CirJsonDialectUtil
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonReferenceExpression
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonTypedHandler : TypedHandlerDelegate() {

    private var myWhitespaceAdded = false

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file is CirJsonFile) {
            processPairedBracesComma(c, editor, file)
            addWhiteSpaceAfterColonIfNeeded(c, editor, file)
            removeRedundantWhitespaceIfAfterColon(c, editor, file)
            handleMoveOutsideQuotes(c, editor, file)
        }

        return Result.CONTINUE
    }

    private fun removeRedundantWhitespaceIfAfterColon(c: Char, editor: Editor, file: PsiFile) {
        if (!myWhitespaceAdded || c != ' ' || CirJsonEditorOptions.instance.AUTO_WHITESPACE_AFTER_COLON) {
            if (c != ':') {
                myWhitespaceAdded = false
            }

            return
        }

        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
        val element = file.findElementAt(offset)

        if (element is PsiWhiteSpace) {
            editor.document.deleteString(offset - 1, offset)
        }

        myWhitespaceAdded = false
    }

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file is CirJsonFile) {
            addPropertyNameQuotesIfNeeded(c, editor, file)
        }

        return Result.CONTINUE
    }

    private fun addWhiteSpaceAfterColonIfNeeded(c: Char, editor: Editor, file: PsiFile) {
        if (c != ':' || !CirJsonEditorOptions.instance.AUTO_WHITESPACE_AFTER_COLON) {
            if (c != ' ') {
                myWhitespaceAdded = false
            }

            return
        }

        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
        val element = PsiTreeUtil.getParentOfType(PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(offset)),
                CirJsonProperty::class.java, false)

        if (element == null) {
            myWhitespaceAdded = false
            return
        }

        val children = element.node.getChildren(TokenSet.create(CirJsonElementTypes.COLON))

        if (children.isEmpty()) {
            myWhitespaceAdded = false
            return
        }

        val colon = children[0]
        val next = colon.treeNext
        val text = next.text

        if (text.isEmpty() || !StringUtil.isEmptyOrSpaces(text) || StringUtil.isLineBreak(text[0])) {
            val insOffset = colon.startOffset + 1
            editor.document.insertString(insOffset, " ")
            editor.caretModel
            myWhitespaceAdded = true
        } else {
            myWhitespaceAdded = false
        }
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun handleMoveOutsideQuotes(c: Char, editor: Editor, file: PsiFile) {
            val options = CirJsonEditorOptions.instance

            if (!(c == ':' && options.COLON_MOVE_OUTSIDE_QUOTES || c == ',' && options.COMMA_MOVE_OUTSIDE_QUOTES)) {
                return
            }

            val offset = editor.caretModel.offset
            val sequence = editor.document.charsSequence
            val length = sequence.length

            if (offset >= length || offset < 0) {
                return
            }

            val charAtOffset = sequence[offset]

            if (charAtOffset != '.' || (offset + 1 < length && sequence[offset + 1] == c)) {
                return
            }

            val element = file.findElementAt(offset)

            if (element == null || !validatePositionToMoveOutOfQuotes(c, element)) {
                return
            }

            PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
            editor.document.deleteString(offset - 1, offset)
            editor.document.insertString(offset, c.toString())
            val newSequence = editor.document.charsSequence
            var nextOffset = offset + 1

            if (c == ':' && options.AUTO_WHITESPACE_AFTER_COLON) {
                val nextChar = if (nextOffset > newSequence.length) 'a' else newSequence[nextOffset]

                if (!nextChar.isWhitespace() || nextChar == '\n') {
                    editor.document.insertString(nextOffset, " ")
                    nextOffset++
                }
            }

            editor.caretModel.moveToOffset(nextOffset)
        }

        private fun validatePositionToMoveOutOfQuotes(c: Char, element: PsiElement): Boolean {
            // comma can be after the element, but only the comma
            if (PsiUtilCore.getElementType(element) == CirJsonElementTypes.R_CURLY) {
                return c == ',' && element.prevSibling is CirJsonProperty
            }

            if (PsiUtilCore.getElementType(element) == CirJsonElementTypes.R_BRACKET) {
                return c == ',' && element.prevSibling is CirJsonStringLiteral
            }

            // we can have a whitespace in the position, but again - only for the comma
            val parent = element.parent

            if (element is PsiWhiteSpace && c == ',') {
                val sibling = element.prevSibling
                return sibling is CirJsonProperty || sibling is CirJsonStringLiteral
            }

            // the most ordinary case - literal property key or value
            val grandParent = if (parent is CirJsonStringLiteral) parent.parent else null
            return grandParent is CirJsonProperty && (c != ':' || grandParent.nameElement == parent)
                    && (c != ',' || grandParent.value == parent)
        }

        private fun addPropertyNameQuotesIfNeeded(c: Char, editor: Editor, file: PsiFile) {
            if (c != ':' || !CirJsonDialectUtil.isStandardCirJson(file)
                    || CirJsonEditorOptions.instance.AUTO_QUOTE_PROP_NAME) {
                return
            }

            val offset = editor.caretModel.offset
            val element = PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(offset))

            if (element !is CirJsonProperty) {
                return
            }

            val nameElement = element.nameElement

            if (nameElement is CirJsonReferenceExpression) {
                element.name = nameElement.text
                PsiDocumentManager.getInstance(file.project).doPostponedOperationsAndUnblockDocument(editor.document)
            }
        }

        private fun processPairedBracesComma(c: Char, editor: Editor, file: PsiFile) {
            if (!CirJsonEditorOptions.instance.COMMA_ON_MATCHING_BRACES
                    || (c != '[' && c != '{' && c != '"' && c != '\'')) {
                return
            }

            SmartEnterProcessor.commitDocument(editor)
            val offset = editor.caretModel.offset
            val element = file.findElementAt(offset) ?: return

            val parent = element.parent

            val codeInsightSettings = CodeInsightSettings.getInstance()

            if ((((c == '[' && parent is CirJsonArray) || (c == '{' && parent is CirJsonObject))
                            && codeInsightSettings.AUTOINSERT_PAIR_BRACKET)
                    || ((c == '"' || c == '\'') && parent is CirJsonStringLiteral
                            && codeInsightSettings.AUTOINSERT_PAIR_QUOTE)) {
                if (shouldAddCommaInParentContainer(parent as CirJsonValue)) {
                    editor.document.insertString(parent.textRange.endOffset, ",")
                }
            }
        }

        private fun shouldAddCommaInParentContainer(item: CirJsonValue): Boolean {
            val parent = item.parent

            if (parent is CirJsonArray || parent is CirJsonProperty) {
                val nextElement = PsiTreeUtil.skipWhitespacesForward(if (parent is CirJsonProperty) parent else item)

                if (nextElement is PsiErrorElement) {
                    val forward = PsiTreeUtil.skipWhitespacesForward(nextElement)

                    return if (parent is CirJsonProperty) {
                        forward is CirJsonProperty
                    } else {
                        forward is CirJsonValue
                    }
                }
            }

            return false
        }

    }

}