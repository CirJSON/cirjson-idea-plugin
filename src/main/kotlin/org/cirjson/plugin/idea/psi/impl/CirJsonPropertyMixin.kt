package org.cirjson.plugin.idea.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.util.ArrayUtil
import org.cirjson.plugin.idea.psi.CirJsonElementGenerator
import org.cirjson.plugin.idea.psi.CirJsonProperty

abstract class CirJsonPropertyMixin(node: ASTNode) : CirJsonElementImpl(node), CirJsonProperty {

    override fun setName(name: String): PsiElement {
        val generator = CirJsonElementGenerator(project)
        nameElement.replace(generator.createStringLiteral(StringUtil.unquoteString(name)))
        return this
    }

    override fun getReference(): PsiReference? {
        return CirJsonPropertyNameReference(this)
    }

    override fun getReferences(): Array<PsiReference> {
        val fromProviders = ReferenceProvidersRegistry.getReferencesFromProviders(this)
        return ArrayUtil.prepend(CirJsonPropertyNameReference(this), fromProviders)
    }

}