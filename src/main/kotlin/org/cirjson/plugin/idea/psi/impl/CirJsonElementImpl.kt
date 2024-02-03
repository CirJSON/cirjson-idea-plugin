package org.cirjson.plugin.idea.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation

open class CirJsonElementImpl(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun toString(): String {
        return this::class.simpleName!!.removeSuffix("Impl")
    }

    override fun getName(): String {
        return super.getName()!!
    }

    override fun getPresentation(): ItemPresentation {
        return super.getPresentation()!!
    }

}