package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface CirJsonSchemaProviderFactory {

    /**
     * Called in smart mode by default. Implement [com.intellij.openapi.project.DumbAware] to be called in dumb mode.
     *
     * @param project the project
     */
    fun getProviders(project: Project): List<CirJsonSchemaFileProvider>

    companion object {

        val EP_NAME =
                ExtensionPointName.create<CirJsonSchemaProviderFactory>("javaScript.cirJsonSchema.providerFactory")

    }

}