package org.cirjson.plugin.idea.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

abstract class CirJsonReferenceLiteralMixin(node: ASTNode) : CirJsonValueImpl(node), ContributedReferenceHost {

    override fun getReferences(): Array<PsiReference> {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this)
    }

}