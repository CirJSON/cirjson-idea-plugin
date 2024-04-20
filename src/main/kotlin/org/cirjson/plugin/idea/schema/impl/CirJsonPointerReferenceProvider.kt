package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonPointerReferenceProvider {

    class CirJsonSchemaIdReference(element: CirJsonValue, private val myText: String) :
            CirJsonSchemaBaseReference<CirJsonValue>(element, getRange(element)) {

        override fun resolveInner(): PsiElement? {
            TODO()
        }

        companion object {

            private fun getRange(element: CirJsonValue): TextRange {
                val range = element.textRange.shiftLeft(element.textOffset)
                return TextRange(range.startOffset + 1, range.endOffset - 1)
            }

        }

    }

    internal class CirJsonPointerReference(element: CirJsonValue, textRange: TextRange,
            private val myFullPath: String) : CirJsonSchemaBaseReference<CirJsonValue>(element, textRange) {

        override fun resolveInner(): PsiElement? {
            TODO()
        }

    }

}