package org.cirjson.plugin.idea.schema.impl.adapters

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter

class CirJsonCirJsonObjectAdapter(private val myValue: CirJsonObject) : CirJsonObjectValueAdapter {

    override val isObject: Boolean = true

    override val isArray: Boolean = false

    override val isStringLiteral: Boolean = false

    override val isNumberLiteral: Boolean = false

    override val isBooleanLiteral: Boolean = false

    override val delegate: PsiElement = myValue

    override val asObject: CirJsonObjectValueAdapter = this

    override val asArray: CirJsonArrayValueAdapter? = null

    override val propertyList: List<CirJsonPropertyAdapter>
        get() {
            return myValue.propertyList.mapNotNull { CirJsonCirJsonPropertyAdapter(it) }
        }

}