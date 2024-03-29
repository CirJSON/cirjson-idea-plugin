package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile

interface CirJsonSchemaCatalogExclusion {

    fun isExcluded(file: VirtualFile): Boolean

    companion object {

        val EP_NAME =
                ExtensionPointName.create<CirJsonSchemaCatalogExclusion>("org.cirjson.plugin.idea.catalog.exclusion")

    }

}