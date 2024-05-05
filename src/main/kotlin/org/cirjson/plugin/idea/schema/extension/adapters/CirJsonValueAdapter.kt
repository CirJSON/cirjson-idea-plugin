package org.cirjson.plugin.idea.schema.extension.adapters

import com.intellij.psi.PsiElement

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

}