package org.cirjson.plugin.idea.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonProperty

abstract class CirJsonPropertyMixin(node: ASTNode): CirJsonElementImpl(node), CirJsonProperty {

    override fun setName(name: String): PsiElement {
        TODO("Not yet implemented")
    }

}