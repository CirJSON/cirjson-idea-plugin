package org.cirjson.plugin.idea.navigation

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.CirJsonUtil
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonElement
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil

class CirJsonQualifiedNameProvider : QualifiedNameProvider {

    override fun adjustElementToCopy(element: PsiElement): PsiElement? {
        return null
    }

    override fun getQualifiedName(element: PsiElement): String? {
        return generateQualifiedName(element, CirJsonQualifiedNameKind.Qualified)
    }

    override fun qualifiedNameToElement(fqn: String, project: Project): PsiElement? {
        return null
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        fun generateQualifiedName(element: PsiElement, qualifiedNameKind: CirJsonQualifiedNameKind): String? {
            if (element !is CirJsonElement) {
                return null
            }

            var realElement = element
            var parentProperty = PsiTreeUtil.getNonStrictParentOfType(realElement, CirJsonProperty::class.java,
                    CirJsonArray::class.java)
            val builder = StringBuilder()

            while (parentProperty != null) {
                if (parentProperty is CirJsonProperty) {
                    var name = parentProperty.name

                    if (qualifiedNameKind == CirJsonQualifiedNameKind.CirJsonPointer) {
                        name = CirJsonPointerUtil.escapeForCirJsonPointer(name)
                    }

                    builder.insert(0, name)
                    builder.insert(0, if (qualifiedNameKind == CirJsonQualifiedNameKind.CirJsonPointer) "/" else ".")
                } else {
                    val index = CirJsonUtil.getArrayIndexOfItem(
                            if (realElement is CirJsonProperty) realElement.parent else realElement)

                    if (index == -1) {
                        return null
                    }

                    builder.insert(0,
                            if (qualifiedNameKind == CirJsonQualifiedNameKind.CirJsonPointer) "/$index" else "[$index]")
                }

                realElement = parentProperty
                parentProperty = PsiTreeUtil.getNonStrictParentOfType(realElement, CirJsonProperty::class.java,
                        CirJsonArray::class.java)
            }

            return StringUtil.trimStart(builder.toString(), ".")
        }

    }

}