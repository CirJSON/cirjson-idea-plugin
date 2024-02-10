package org.cirjson.plugin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.util.DocumentUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.impl.CirJsonRecursiveElementVisitor

class CirJsonTrailingCommaRemover : PreFormatProcessor {

    override fun process(element: ASTNode, range: TextRange): TextRange {
        val rootPsi = element.psi

        if (rootPsi.language != CirJsonLanguage.INSTANCE) {
            return range
        }

        val settings = CodeStyle.getCustomSettings(rootPsi.containingFile, CirJsonCodeStyleSettings::class.java)

        if (settings.KEEP_TRAILING_COMMA) {
            return range
        }

        val psiDocumentManager = PsiDocumentManager.getInstance(rootPsi.project)
        val document = psiDocumentManager.getDocument(rootPsi.containingFile) ?: return range

        DocumentUtil.executeInBulk(document) {
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
            val visitor = Visitor(document)
            rootPsi.accept(visitor)
            psiDocumentManager.commitDocument(document)
        }

        return range
    }

    private class Visitor(private val myDocument: Document) : CirJsonRecursiveElementVisitor() {

        private var myOffsetDelta = 0

        override fun visitArray(o: CirJsonArray) {
            super.visitArray(o)
            val lastChild: PsiElement? = o.lastChild

            if (lastChild == null || lastChild.node.elementType != CirJsonElementTypes.R_BRACKET) {
                return
            }

            deleteTrailingCommas(ObjectUtils.coalesce(ContainerUtil.getLastItem(o.valueList), o.firstChild))
        }

        override fun visitObject(o: CirJsonObject) {
            super.visitObject(o)
            val lastChild: PsiElement? = o.lastChild

            if (lastChild == null || lastChild.node.elementType != CirJsonElementTypes.R_CURLY) {
                return
            }

            deleteTrailingCommas(ObjectUtils.coalesce(ContainerUtil.getLastItem(o.propertyList), o.firstChild))
        }

        private fun deleteTrailingCommas(lastElementOrOpeningBrace: PsiElement?) {
            var element = lastElementOrOpeningBrace?.nextSibling

            while (element != null) {
                if (element.node.elementType != CirJsonElementTypes.COMMA || element is PsiErrorElement && "," == element.text) {
                    deleteNode(element.node)
                } else if (element !is PsiComment && element !is PsiWhiteSpace) {
                    break
                }

                element = element.nextSibling
            }
        }

        private fun deleteNode(node: ASTNode) {
            val length = node.textLength
            myDocument.deleteString(node.startOffset + myOffsetDelta, node.startOffset + length + myOffsetDelta)
            myOffsetDelta -= length
        }

    }

}