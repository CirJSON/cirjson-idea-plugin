package org.cirjson.plugin.idea.psi.impl

import com.intellij.psi.PsiRecursiveVisitor
import org.cirjson.plugin.idea.psi.CirJsonElement
import org.cirjson.plugin.idea.psi.CirJsonElementVisitor

open class CirJsonRecursiveElementVisitor : CirJsonElementVisitor(), PsiRecursiveVisitor {

    override fun visitElement(element: CirJsonElement) {
        element.acceptChildren(this)
    }

}