package org.cirjson.plugin.idea

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import org.cirjson.plugin.idea.editor.CirJsonTypedHandler
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonQuoteHandler : SimpleTokenSetQuoteHandler(CirJsonTokenSets.STRING_LITERALS), MultiCharQuoteHandler {

    override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence {
        val tokenType = iterator.tokenType

        if (tokenType == TokenType.WHITE_SPACE) {
            val index = iterator.start - 1

            if (index >= 0) {
                return iterator.document.charsSequence[index].toString()
            }
        }

        return if (tokenType == CirJsonElementTypes.SINGLE_QUOTED_STRING) "'" else "\""
    }

    override fun insertClosingQuote(editor: Editor, offset: Int, file: PsiFile, closingQuote: CharSequence) {
        val element = file.findElementAt(offset - 1)
        val parent = element?.parent

        if (parent is CirJsonStringLiteral) {
            PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
            val range = parent.textRange

            if (offset - 1 != range.startOffset || !"\"".contentEquals(closingQuote)) {
                val endOffset = range.endOffset

                if (offset < endOffset) {
                    return
                }

                if (offset == endOffset && parent.value.isNotEmpty()) {
                    return
                }
            }

            editor.document.insertString(offset, closingQuote)
            CirJsonTypedHandler.processPairedBracesComma(closingQuote[0], editor, file)
        }

    }

}