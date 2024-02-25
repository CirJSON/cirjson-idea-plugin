package org.cirjson.plugin.idea.surroundWith

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.*

class CirJsonWithObjectLiteralSurrounder : CirJsonSurrounderBase() {

    override fun getTemplateDescription(): String {
        return CirJsonBundle.message("surround.with.object.literal.desc")
    }

    override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        return !CirJsonPsiUtil.isPropertyKey(elements[0]) && (elements[0] is CirJsonProperty || elements.size == 1)
    }

    override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        if (!isApplicable(elements)) {
            return null
        }

        val generator = CirJsonElementGenerator(project)

        val firstElement = elements[0]
        val newNameElement: CirJsonElement

        if (firstElement is CirJsonValue) {
            assert(elements.size == 1) {
                "Only single CirJSON value can be wrapped in object literal"
            }

            var replacement = generator.createValue<CirJsonObject>(createReplacementText(firstElement.text))
            replacement = firstElement.replace(replacement) as CirJsonObject
            newNameElement = replacement.propertyList[0].nameElement
        } else {
            assert(firstElement is CirJsonProperty)
            val propertiesText = getTextAndRemoveMisc(elements[0], elements[elements.size - 1])
            val tempJsonObject = generator.createValue<CirJsonObject>(
                    "${createReplacementText("{\n$propertiesText")}\n}")
            var replacement = tempJsonObject.propertyList[0]
            replacement = firstElement.replace(replacement) as CirJsonProperty
            newNameElement = replacement.nameElement
        }

        val rangeWithQuotes = newNameElement.textRange
        return TextRange(rangeWithQuotes.startOffset + 1, rangeWithQuotes.endOffset - 1)
    }

    override fun createReplacementText(textInRange: String): String {
        return "\n\"property\": $textInRange\n}"
    }

}