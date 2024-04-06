package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.NestedCompletionsNode

/**
 * Extension point used for extending completion provided by
 * [org.cirjson.plugin.idea.schema.impl.CirJsonSchemaCompletionContributor]. Provided instance of
 * [NestedCompletionsNode] will be converted into a completion item with a several level cirJson/yaml tree to insert.
 * See [NestedCompletionsNode]'s documentation for a more detailed description of how exactly the completion item's
 * text will be constructed.
 */
interface CirJsonSchemaNestedCompletionsTreeProvider {

    /**
     * @return null if you do not want to alter the cirJson schema-based completion for this file
     */
    fun getNestedCompletionsRoot(editedFile: PsiFile): NestedCompletionsNode?

    companion object {

        val EXTENSION_POINT_NAME = ExtensionPointName.create<CirJsonSchemaNestedCompletionsTreeProvider>(
                "org.cirjson.plugin.idea.cirJsonSchemaNestedCompletionsTreeProvider")

        fun getNestedCompletionsData(editedFile: PsiFile): NestedCompletionsNode? {
            return EXTENSION_POINT_NAME.extensionsIfPointIsRegistered.asSequence()
                    .mapNotNull { extension -> extension.getNestedCompletionsRoot(editedFile) }
                    .reduceOrNull { acc, next -> acc.merge(next) }
        }

    }

}