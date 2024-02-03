package org.cirjson.plugin.idea.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import org.cirjson.plugin.idea.psi.CirJsonLiteral

abstract class CirJsonLiteralMixin(node: ASTNode) : CirJsonElementImpl(node), CirJsonLiteral {

    override fun getReferences(): Array<PsiReference> {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this)
    }

}