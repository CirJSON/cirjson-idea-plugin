package org.cirjson.plugin.idea.schema.impl.adapters

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonValue
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import java.util.*

class CirJsonCirJsonPropertyAdapter(private val myProperty: CirJsonProperty) : CirJsonPropertyAdapter {

    override val name: String
        get() = myProperty.name

    override val nameValueAdapter: CirJsonValueAdapter
        get() = createAdapterByType(myProperty.nameElement)

    override val values: Collection<CirJsonValueAdapter>
        get() {
            val value = myProperty.value ?: return emptyList()

            return Collections.singletonList(createAdapterByType(value))
        }

    override val delegate: PsiElement = myProperty

    override val parentObject: CirJsonObjectValueAdapter?
        get() {
            val parent = myProperty.parent
            return if (parent is CirJsonObject) CirJsonCirJsonObjectAdapter(parent) else null
        }

    companion object {

        fun createAdapterByType(value: CirJsonValue): CirJsonValueAdapter {
            return when (value) {
                is CirJsonObject -> CirJsonCirJsonObjectAdapter(value)
                is CirJsonArray -> CirJsonCirJsonArrayAdapter(value)
                else -> CirJsonCirJsonGenericValueAdapter(value)
            }
        }

    }

}