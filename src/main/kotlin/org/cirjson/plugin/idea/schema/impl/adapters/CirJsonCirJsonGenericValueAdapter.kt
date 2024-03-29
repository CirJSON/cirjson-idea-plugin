package org.cirjson.plugin.idea.schema.impl.adapters

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter

class CirJsonCirJsonGenericValueAdapter(private val myValue: CirJsonValue) : CirJsonValueAdapter {

    override val isObject: Boolean = false

    override val isArray: Boolean = false

    override val isStringLiteral: Boolean = myValue is CirJsonStringLiteral

    override val isNumberLiteral: Boolean = myValue is CirJsonNumberLiteral

    override val isBooleanLiteral: Boolean = myValue is CirJsonBooleanLiteral

    override val isNull: Boolean = myValue is CirJsonNullLiteral

    override val delegate: PsiElement = myValue

    override val asObject: CirJsonObjectValueAdapter? = null

    override val asArray: CirJsonArrayValueAdapter? = null

}