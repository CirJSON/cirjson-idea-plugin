package org.cirjson.plugin.idea.editor

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ObjectUtils
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.psi.*

class CirJsonEnterHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref<Int>, caretAdvanceRef: Ref<Int>,
            dataContext: DataContext, originalHandler: EditorActionHandler?): Result {
        if (CirJsonEditorOptions.instance.COMMA_ON_ENTER) {
            return Result.Continue
        }

        val language = EnterHandler.getLanguage(dataContext)

        if (language !is CirJsonLanguage) {
            return Result.Continue
        }

        val caretOffset = caretOffsetRef.get()
        val psiAtOffset = file.findElementAt(caretOffset) ?: return Result.Continue

        if (psiAtOffset is LeafPsiElement && handleComma(caretOffsetRef, psiAtOffset, editor)) {
            return Result.Continue
        }

        val literal = ObjectUtils.tryCast(psiAtOffset.parent, CirJsonValue::class.java) ?: return Result.Continue

        if (literal !is CirJsonStringLiteral) {
            handleCirJsonValue(literal, editor, caretOffsetRef)
        }

        return Result.Continue
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun handleComma(caretOffsetRef: Ref<Int>, psiAtOffset: PsiElement, editor: Editor): Boolean {
            var nextSibling = psiAtOffset
            var hasNewLineBefore = false

            while (nextSibling is PsiWhiteSpace) {
                hasNewLineBefore = "\n" in nextSibling.text
                nextSibling = nextSibling.nextSibling
            }

            val leafPsiElement = ObjectUtils.tryCast(nextSibling, LeafPsiElement::class.java)
            val elementType = leafPsiElement?.elementType

            if (elementType == CirJsonElementTypes.COMMA || elementType == CirJsonElementTypes.R_CURLY) {
                var prevSibling = nextSibling.prevSibling

                while (prevSibling is PsiWhiteSpace) {
                    prevSibling = prevSibling.prevSibling
                }

                if (prevSibling is CirJsonProperty && prevSibling.value != null) {
                    val sibling = if (elementType == CirJsonElementTypes.COMMA) nextSibling else prevSibling
                    var offset = sibling.textRange.endOffset

                    if (offset < editor.document.textLength) {
                        if (elementType == CirJsonElementTypes.R_CURLY && hasNewLineBefore) {
                            editor.document.insertString(offset, ",")
                            offset++
                        }

                        caretOffsetRef.set(offset)
                    }

                    return true
                }

                return false
            }

            if (nextSibling is CirJsonProperty) {
                var prevSibling = nextSibling.prevSibling

                while (prevSibling is PsiWhiteSpace || prevSibling is PsiErrorElement) {
                    prevSibling = prevSibling.prevSibling
                }

                if (prevSibling is CirJsonProperty) {
                    val offset = prevSibling.textRange.endOffset

                    if (offset < editor.document.textLength) {
                        editor.document.insertString(offset, ",")
                        caretOffsetRef.set(offset + 1)
                    }

                    return true
                }
            }

            return false
        }

        private fun handleCirJsonValue(literal: CirJsonValue, editor: Editor, caretOffsetRef: Ref<Int>) {
            val parent = literal.parent

            if (parent !is CirJsonProperty || parent.value != literal) {
                return
            }

            var nextSibling = parent.nextSibling

            while (nextSibling is PsiWhiteSpace || nextSibling is PsiErrorElement) {
                nextSibling = nextSibling.nextSibling
            }

            var offset = literal.textRange.endOffset

            if (literal is CirJsonObject || literal is CirJsonArray) {
                if (nextSibling is LeafPsiElement && nextSibling.elementType == CirJsonElementTypes.COMMA ||
                        nextSibling !is CirJsonProperty) {
                    return
                }

                val document = editor.document

                if (offset < document.textLength) {
                    document.insertString(offset, ",")
                }

                return
            }

            if (nextSibling is LeafPsiElement && nextSibling.elementType == CirJsonElementTypes.COMMA) {
                offset = nextSibling.textRange.endOffset
            } else {
                val document = editor.document

                if (offset < document.textLength) {
                    document.insertString(offset, ",")
                }

                offset++
            }

            if (offset < editor.document.textLength) {
                caretOffsetRef.set(offset)
            }
        }

    }

}