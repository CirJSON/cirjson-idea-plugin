package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonDialectUtil
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.adapters.CirJsonCirJsonPropertyAdapter

class CirJsonOriginalPsiWalker private constructor() : CirJsonLikePsiWalker {

    fun handles(element: PsiElement): Boolean {
        val parent = element.parent
        return element is CirJsonFile && CirJsonDialectUtil.isStandardCirJson(element)
                || parent != null
                && (element is CirJsonElement || element is LeafPsiElement && parent is CirJsonElement)
                && CirJsonDialectUtil.isStandardCirJson(CompletionUtil.getOriginalOrSelf(parent))
    }

    override fun isName(element: PsiElement): ThreeState {
        val parent = element.parent

        return if (parent is CirJsonObject) {
            ThreeState.YES
        } else if (parent is CirJsonProperty) {
            if (PsiTreeUtil.isAncestor(parent.nameElement, element, false)) {
                ThreeState.YES
            } else {
                ThreeState.NO
            }
        } else {
            ThreeState.NO
        }
    }

    override fun isPropertyWithValue(element: PsiElement): Boolean {
        var realElement = element

        if (realElement is CirJsonStringLiteral || realElement is CirJsonReferenceExpression) {
            val parent = realElement.parent

            if (parent !is CirJsonProperty || parent.nameElement !== realElement) {
                return false
            }

            realElement = parent
        }

        return realElement is CirJsonProperty && realElement.value != null
    }

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        var current: PsiElement? = element

        while (current != null && current !is PsiFile) {
            if (current is CirJsonValue || current is CirJsonProperty) {
                return current
            }

            current = current.parent
        }

        return null
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): CirJsonPointerPosition? {
        val pos = CirJsonPointerPosition()
        var current = element

        while (current !is PsiFile) {
            val position = current
            current = current.parent

            if (current is CirJsonArray) {
                val list = current.valueList
                var idx = -1

                for (i in list.indices) {
                    val value = list[i]

                    if (value == position) {
                        idx = i
                        break
                    }
                }

                pos.addPrecedingStep(idx)
            } else if (current is CirJsonProperty) {
                val propertyName = current.name
                current = current.parent

                if (current !is CirJsonObject) {
                    return null
                }

                if (position !== element || forceLastTransition) {
                    pos.addPrecedingStep(propertyName)
                }
            } else if (current is CirJsonObject && position is CirJsonProperty) {
                if (position !== element || forceLastTransition) {
                    val propertyName = position.name
                    pos.addPrecedingStep(propertyName)
                }
            } else if (current is PsiFile) {
                break
            } else {
                return null
            }
        }

        return pos
    }

    override val isRequiringNameQuote: Boolean
        get() = true

    override val isAllowingSingleQuotes: Boolean = false

    override fun isQuotedString(element: PsiElement): Boolean {
        return element is CirJsonStringLiteral
    }

    override fun hasMissingCommaAfter(element: PsiElement): Boolean {
        var current: PsiElement? = if (element is CirJsonProperty) {
            element
        } else {
            PsiTreeUtil.getParentOfType(element, CirJsonProperty::class.java)
        }

        while (current != null && current.node.elementType != CirJsonElementTypes.COMMA) {
            current = current.nextSibling
        }

        val commaOffset = current?.textRange?.startOffset ?: Int.MAX_VALUE
        val offset = element.textRange.startOffset
        val obj = PsiTreeUtil.getParentOfType(element, CirJsonObject::class.java) ?: return false

        for (property in obj.propertyList) {
            val pOffset = property.textRange.startOffset

            if (pOffset >= offset && !PsiTreeUtil.isAncestor(property, element, false)) {
                return pOffset < commaOffset
            }
        }

        return false
    }

    override fun getPropertyNamesOfParentObject(originalPosition: PsiElement,
            computedPosition: PsiElement): Set<String> {
        val obj = PsiTreeUtil.getParentOfType(originalPosition, CirJsonObject::class.java) ?: return emptySet()
        return obj.propertyList.filter { !isRequiringNameQuote || it.nameElement is CirJsonStringLiteral }
                .map { StringUtil.unquoteString(it.name) }.toSet()
    }

    override fun getParentPropertyAdapter(element: PsiElement): CirJsonPropertyAdapter? {
        val property = PsiTreeUtil.getParentOfType(element, CirJsonProperty::class.java, false) ?: return null
        return CirJsonCirJsonPropertyAdapter(property)
    }

    override fun createValueAdapter(element: PsiElement): CirJsonValueAdapter? {
        return if (element is CirJsonValue) CirJsonCirJsonPropertyAdapter.createAdapterByType(element) else null
    }

    override fun getRoots(file: PsiFile): Collection<PsiElement> {
        return if (file is CirJsonFile) {
            ContainerUtil.createMaybeSingletonList(file.topLevelValue)
        } else {
            emptyList()
        }
    }

    override fun getParentContainer(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, CirJsonProperty::class.java),
                CirJsonObject::class.java, CirJsonArray::class.java)
    }

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? {
        return (property as? CirJsonProperty)?.nameElement
    }

    companion object {

        val INSTANCE = CirJsonOriginalPsiWalker()

    }

}