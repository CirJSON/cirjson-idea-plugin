package org.cirjson.plugin.idea.editor.selection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonStringLiteralSelectionHandler : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean {
        if (e.parent !is CirJsonStringLiteral) {
            return false
        }

        return !InjectedLanguageManager.getInstance(e.project).isInjectedFragment(e.containingFile)
    }

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int,
            editor: Editor): MutableList<TextRange> {
        val type = e.node.elementType
        val quoteChar = if (type == CirJsonElementTypes.SINGLE_QUOTED_STRING) '\'' else '"'
        val lexer = StringLiteralLexer(quoteChar, type, false, "/", false, false)
        val result = arrayListOf<TextRange>()
        SelectWordUtil.addWordHonoringEscapeSequences(editorText, e.textRange, cursorOffset, lexer, result)

        val parent = e.parent
        result.add(ElementManipulators.getValueTextRange(parent).shiftRight(parent.textOffset))
        return result
    }

}