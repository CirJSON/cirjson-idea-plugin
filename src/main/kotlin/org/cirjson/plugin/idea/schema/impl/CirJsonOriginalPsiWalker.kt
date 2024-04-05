package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonDialectUtil
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
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

    override fun isQuotedString(element: PsiElement): Boolean {
        return element is CirJsonStringLiteral
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

    companion object {

        val INSTANCE = CirJsonOriginalPsiWalker()

    }

}