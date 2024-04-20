package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache

abstract class CirJsonSchemaBaseReference<T : PsiElement>(element: T, textRange: TextRange) :
        PsiReferenceBase<T>(element, textRange, false) {

    override fun resolve(): PsiElement? {
        return ResolveCache.getInstance(element.project).resolveWithCaching(this, MyResolver.INSTANCE, false, false)
    }

    abstract fun resolveInner(): PsiElement?

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null || this.javaClass !== other.javaClass) {
            return false
        }

        return isIdenticalTo(other as CirJsonSchemaBaseReference<*>)
    }

    protected open fun isIdenticalTo(that: CirJsonSchemaBaseReference<*>): Boolean {
        return this.myElement == that.myElement
    }

    override fun hashCode(): Int {
        return myElement.hashCode()
    }

    private class MyResolver : ResolveCache.Resolver {

        override fun resolve(ref: PsiReference, incompleteCode: Boolean): PsiElement? {
            return (ref as CirJsonSchemaBaseReference<*>).resolveInner()
        }

        companion object {

            val INSTANCE = MyResolver()

        }

    }

}