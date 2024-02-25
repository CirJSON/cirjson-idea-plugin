package org.cirjson.plugin.idea.editor.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil

class CirJsonFoldingBuilder : FoldingBuilder, DumbAware {

    override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
        val descriptors = arrayListOf<FoldingDescriptor>()
        collectDescriptorsRecursively(node, document, descriptors)
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY)
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val type = node.elementType

        return when (type) {
            CirJsonElementTypes.OBJECT -> {
                val obj = node.getPsi(CirJsonObject::class.java)
                val id = obj.id

                if (id != null) {
                    "{\"__cirJsonId__\": $id...}"
                } else {
                    "{...}"
                }
            }

            CirJsonElementTypes.ARRAY -> "[...]"
            CirJsonElementTypes.LINE_COMMENT -> "//..."
            CirJsonElementTypes.BLOCK_COMMENT -> "/*...*/"
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(p0: ASTNode): Boolean {
        return false
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        fun expandLineCommentsRange(anchor: PsiElement): Couple<PsiElement> {
            return Couple.of(CirJsonPsiUtil.findFurthestSiblingOfSameType(anchor, false),
                    CirJsonPsiUtil.findFurthestSiblingOfSameType(anchor, true))
        }

        private fun collectDescriptorsRecursively(node: ASTNode, document: Document,
                descriptors: MutableList<FoldingDescriptor>) {
            val type = node.elementType

            if ((type == CirJsonElementTypes.OBJECT || type == CirJsonElementTypes.ARRAY)
                    && spanMultipleLines(node, document)) {
                descriptors.add(FoldingDescriptor(node, node.textRange))
            } else if (type == CirJsonElementTypes.BLOCK_COMMENT) {
                descriptors.add(FoldingDescriptor(node, node.textRange))
            } else if (type == CirJsonElementTypes.LINE_COMMENT) {
                val commentRange = expandLineCommentsRange(node.psi)
                val startOffset = commentRange.first.textRange.startOffset
                val endOffset = commentRange.second.textRange.startOffset

                if (document.getLineNumber(startOffset) != document.getLineNumber(endOffset)) {
                    descriptors.add(FoldingDescriptor(node, TextRange(startOffset, endOffset)))
                }
            }

            for (child in node.getChildren(null)) {
                collectDescriptorsRecursively(child, document, descriptors)
            }
        }

        private fun spanMultipleLines(node: ASTNode, document: Document): Boolean {
            val range = node.textRange
            val endOffset = range.endOffset
            val startLine = document.getLineNumber(range.startOffset)

            return if (endOffset < document.textLength) {
                startLine < document.getLineNumber(endOffset)
            } else {
                startLine < document.lineCount - 1
            }
        }

    }

}