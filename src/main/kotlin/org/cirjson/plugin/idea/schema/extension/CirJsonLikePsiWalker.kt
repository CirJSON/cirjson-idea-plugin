package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonOriginalPsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

interface CirJsonLikePsiWalker {

    /**
     * Returns YES in place where a property name is expected,
     *         NO in place where a property value is expected,
     *         UNSURE where both property name and property value can be present
     */
    fun isName(element: PsiElement): ThreeState

    fun isPropertyWithValue(element: PsiElement): Boolean

    fun findElementToCheck(element: PsiElement): PsiElement?

    fun findPosition(element: PsiElement, forceLastTransition: Boolean): CirJsonPointerPosition?

    val isRequiringNameQuote: Boolean

    val isRequiringValueQuote: Boolean
        get() = true

    val isAllowingSingleQuotes: Boolean

    fun isValidIdentifier(string: String, project: Project): Boolean = true

    fun isQuotedString(element: PsiElement): Boolean {
        return false
    }

    fun hasMissingCommaAfter(element: PsiElement): Boolean

    fun getPropertyNamesOfParentObject(originalPosition: PsiElement, computedPosition: PsiElement): Set<String>

    fun indentOf(element: PsiElement): Int = 0

    fun indentOf(file: PsiFile): Int = 4

    fun getParentPropertyAdapter(element: PsiElement): CirJsonPropertyAdapter?

    fun isTopCirJsonElement(element: PsiElement): Boolean

    fun createValueAdapter(element: PsiElement): CirJsonValueAdapter?

    fun adjustErrorHighlightingRange(element: PsiElement): TextRange {
        return element.textRange
    }

    val isAcceptsEmptyRoot: Boolean
        get() = false

    fun getRoots(file: PsiFile): Collection<PsiElement>?

    val isRequiringReformatAfterArrayInsertion: Boolean
        get() = true

    val defaultObjectValue: String
        get() = "{}"

    val defaultArrayValue: String
        get() = "[]"

    val hasWhitespaceDelimitedCodeBlocks: Boolean
        get() = false

    fun getNodeTextForValidation(element: PsiElement): String {
        return element.text
    }

    fun getSyntaxAdapter(project: Project): CirJsonLikeSyntaxAdapter? {
        return null
    }

    fun getParentContainer(element: PsiElement): PsiElement? {
        return null
    }

    fun getPropertyNameElement(property: PsiElement?): PsiElement?

    fun getPropertyValueSeparator(valueType: CirJsonSchemaType?): String {
        return ":"
    }

    companion object {

        fun getWalker(element: PsiElement, schemaObject: CirJsonSchemaObject): CirJsonLikePsiWalker? {
            if (CirJsonOriginalPsiWalker.INSTANCE.handles(element)) {
                return CirJsonOriginalPsiWalker.INSTANCE
            }

            return CirJsonLikePsiWalkerFactory.EXTENSION_POINT_NAME.extensionList.firstOrNull { it.handles(element) }
                    ?.create(schemaObject)
        }

    }

}