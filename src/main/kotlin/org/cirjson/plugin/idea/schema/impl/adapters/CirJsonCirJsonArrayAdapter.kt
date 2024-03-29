package org.cirjson.plugin.idea.schema.impl.adapters

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter

class CirJsonCirJsonArrayAdapter(private val myArray: CirJsonArray) : CirJsonArrayValueAdapter {

    override val isObject: Boolean = false

    override val isArray: Boolean = true

    override val isStringLiteral: Boolean = false

    override val isNumberLiteral: Boolean = false

    override val isBooleanLiteral: Boolean = false

    override val delegate: PsiElement = myArray

    override val asObject: CirJsonObjectValueAdapter? = null

    override val asArray: CirJsonArrayValueAdapter = this

    override val elements: List<CirJsonValueAdapter>
        get() {
            return myArray.valueList.mapNotNull { CirJsonCirJsonPropertyAdapter.createAdapterByType(it) }
        }

}