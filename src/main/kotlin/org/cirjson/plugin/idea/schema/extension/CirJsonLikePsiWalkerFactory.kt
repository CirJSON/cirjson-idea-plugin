package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

/**
 * Implement to contribute a CirJSON-adapter for your language. This allows to run CirJSON Schemas on non CirJSON
 * languages.
 */
interface CirJsonLikePsiWalkerFactory {

    fun handles(element: PsiElement): Boolean

    fun create(schemaObject: CirJsonSchemaObject): CirJsonLikePsiWalker

    companion object {

        val EXTENSION_POINT_NAME = ExtensionPointName.create<CirJsonLikePsiWalkerFactory>(
                "org.cirjson.plugin.idea.cirJsonLikePsiWalkerFactory")

    }

}