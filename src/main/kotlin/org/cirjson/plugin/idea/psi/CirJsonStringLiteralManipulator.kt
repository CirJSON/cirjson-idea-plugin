package org.cirjson.plugin.idea.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class CirJsonStringLiteralManipulator : AbstractElementManipulator<CirJsonStringLiteral>() {

    override fun handleContentChange(element: CirJsonStringLiteral, range: TextRange,
            newContent: String?): CirJsonStringLiteral? {
        assert(range in TextRange(0, element.textLength))

        val originalContent = element.text
        val withoutQuotes = getRangeInElement(element)
        val generator = CirJsonElementGenerator(element.project)
        val replacement = originalContent.substring(withoutQuotes.startOffset, range.startOffset) + newContent +
                originalContent.substring(range.endOffset, withoutQuotes.endOffset)
        return element.replace(generator.createStringLiteral(replacement)) as CirJsonStringLiteral?
    }

    override fun getRangeInElement(element: CirJsonStringLiteral): TextRange {
        val content = element.text
        val startOffset = if (content.startsWith("'") || content.startsWith("\"")) 1 else 0
        val endOffset = if (content.length > 1 && (content.endsWith("'") || content.endsWith("\""))) -1 else 0
        return TextRange(startOffset, content.length + endOffset)
    }

}