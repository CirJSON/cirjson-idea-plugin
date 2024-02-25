package org.cirjson.plugin.idea.surroundWith

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonElementGenerator
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil
import org.cirjson.plugin.idea.psi.CirJsonValue

abstract class CirJsonSurrounderBase : Surrounder {

    override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        return elements.isNotEmpty() && elements[0] is CirJsonValue && !CirJsonPsiUtil.isPropertyKey(elements[0])
    }

    override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        if (!isApplicable(elements)) {
            return null
        }

        val generator = CirJsonElementGenerator(project)

        if (elements.size == 1) {
            val replacement = generator.createValue<CirJsonValue>(createReplacementText(elements[0].text))
            elements[0].replace(replacement)
        } else {
            val propertiesText = getTextAndRemoveMisc(elements[0], elements[elements.size - 1])
            val replacement = generator.createValue<CirJsonValue>(createReplacementText(propertiesText))
            elements[0].replace(replacement)
        }

        return null
    }

    protected abstract fun createReplacementText(textInRange: String): String

    protected companion object {

        fun getTextAndRemoveMisc(firstProperty: PsiElement, lastProperty: PsiElement): String {
            val replacedRange = TextRange(firstProperty.textOffset, lastProperty.textRange.endOffset)
            val propertiesTest = replacedRange.substring(firstProperty.containingFile.text)

            if (firstProperty !== lastProperty) {
                val parent = firstProperty.parent
                parent.deleteChildRange(firstProperty.nextSibling, lastProperty)
            }

            return propertiesTest
        }

    }

}