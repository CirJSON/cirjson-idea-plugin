package org.cirjson.plugin.idea.schema.extension

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonOriginalPsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

interface CirJsonLikePsiWalker {

    /**
     * Returns YES in place where a property name is expected,
     *         NO in place where a property value is expected,
     *         UNSURE where both property name and property value can be present
     */
    fun isName(element: PsiElement): ThreeState

    fun findElementToCheck(element: PsiElement): PsiElement?

    fun findPosition(element: PsiElement, forceLastTransition: Boolean): CirJsonPointerPosition?

    fun createValueAdapter(element: PsiElement): CirJsonValueAdapter?

    fun getRoots(file: PsiFile): Collection<PsiElement>?

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