package org.cirjson.plugin.idea.schema.extension.adapters

import com.intellij.psi.PsiElement

interface CirJsonPropertyAdapter {

    val name: String?

    val nameValueAdapter: CirJsonValueAdapter?

    val values: Collection<CirJsonValueAdapter>

    val delegate: PsiElement

    val parentObject: CirJsonObjectValueAdapter?

}