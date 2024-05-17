package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

interface CirJsonSchemaGotoDeclarationSuppressor {

    fun shouldSuppressGotoDeclaration(element: PsiElement?): Boolean

    companion object {

        val EP_NAME = ExtensionPointName.create<CirJsonSchemaGotoDeclarationSuppressor>(
                "org.cirjson.plugin.idea.cirJsonSchemaGotoDeclarationSuppressor")

    }

}