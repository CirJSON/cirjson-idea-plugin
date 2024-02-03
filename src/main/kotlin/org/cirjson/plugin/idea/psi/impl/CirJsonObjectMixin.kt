package org.cirjson.plugin.idea.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty

abstract class CirJsonObjectMixin(node: ASTNode) : CirJsonContainerImpl(node), CirJsonObject {

    private val myPropertyCache = CachedValueProvider<Map<String, CirJsonProperty>> {
        val cache = hashMapOf<String, CirJsonProperty>()

        for (property in propertyList) {
            val propertyName = property.name
            if (!cache.containsKey(propertyName)) {
                cache[propertyName] = property
            }
        }

        return@CachedValueProvider CachedValueProvider.Result.createSingleDependency(cache, this)
    }

    override fun findProperty(name: String): CirJsonProperty? {
        return CachedValuesManager.getCachedValue(this, myPropertyCache)[name]
    }

}