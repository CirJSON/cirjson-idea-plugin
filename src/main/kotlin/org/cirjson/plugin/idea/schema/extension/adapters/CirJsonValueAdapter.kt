package org.cirjson.plugin.idea.schema.extension.adapters

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

interface CirJsonValueAdapter {

    val shouldBeIgnored: Boolean
        get() = false

    val isObject: Boolean

    val isArray: Boolean

    val isStringLiteral: Boolean

    val isNumberLiteral: Boolean

    val isBooleanLiteral: Boolean

    val isNull: Boolean

    val delegate: PsiElement

    val asObject: CirJsonObjectValueAdapter?

    val asArray: CirJsonArrayValueAdapter?

    val shouldCheckAsValue: Boolean
        get() = true

    /**
     * For some languages, the same node may represent values of different types depending on the context.
     *
     * This happens, for instance, in YAML, where empty objects and null values are the same thing
     */
    fun getAlternateType(type: CirJsonSchemaType?): CirJsonSchemaType? {
        return type
    }

}