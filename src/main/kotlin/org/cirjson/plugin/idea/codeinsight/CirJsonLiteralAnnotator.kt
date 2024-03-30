package org.cirjson.plugin.idea.codeinsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.kotlin.toIntelliJPair
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory
import org.cirjson.plugin.idea.psi.CirJsonNumberLiteral
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil
import org.cirjson.plugin.idea.psi.CirJsonReferenceExpression
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonLiteralAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is CirJsonReferenceExpression -> highlightPropertyKey(element, holder)
            is CirJsonStringLiteral -> annotateStringLiteral(element, holder)
            is CirJsonNumberLiteral -> annotateNumberLiteral(element, holder)
        }
    }

    private fun annotateStringLiteral(element: CirJsonStringLiteral, holder: AnnotationHolder) {
        val extensions = CirJsonLiteralChecker.EP_NAME.extensionList

        val elementOffset = element.textOffset
        highlightPropertyKey(element, holder)
        val text = CirJsonPsiUtil.getElementTextWithoutHostEscaping(element)
        val length = text.length

        if (length <= 1 || text[0] != text[length - 1] || CirJsonPsiUtil.isEscapedChar(text, length - 1)) {
            holder.newAnnotation(HighlightSeverity.ERROR, CirJsonBundle.message("syntax.error.missing.closing.quote"))
                    .create()
        }

        val fragments = element.textFragments

        for (fragment in fragments) {
            for (checker in extensions) {
                if (!checker.isApplicable(element)) {
                    continue
                }

                val error = checker.getErrorForStringFragment(fragment.toIntelliJPair(), element)

                if (error != null) {
                    holder.newAnnotation(HighlightSeverity.ERROR, error.second)
                            .range(error.first.shiftRight(elementOffset)).create()
                }
            }
        }
    }

    private fun annotateNumberLiteral(element: CirJsonNumberLiteral, holder: AnnotationHolder) {
        val extensions = CirJsonLiteralChecker.EP_NAME.extensionList

        var text: String? = null

        for (checker in extensions) {
            if (!checker.isApplicable(element)) {
                continue
            }

            if (text == null) {
                text = CirJsonPsiUtil.getElementTextWithoutHostEscaping(element)
            }

            val error = checker.getErrorForNumericLiteral(text)

            if (error != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, error).create()
            }
        }
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val DEBUG = ApplicationManager.getApplication().isUnitTestMode

        private fun highlightPropertyKey(element: PsiElement, holder: AnnotationHolder) {
            if (CirJsonPsiUtil.isPropertyKey(element)) {
                if (DEBUG) {
                    holder.newAnnotation(HighlightSeverity.INFORMATION,
                            CirJsonBundle.message("annotation.property.key"))
                            .textAttributes(CirJsonSyntaxHighlighterFactory.CIRJSON_PROPERTY_KEY).create()
                } else {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .textAttributes(CirJsonSyntaxHighlighterFactory.CIRJSON_PROPERTY_KEY).create()
                }
            }
        }

    }

}