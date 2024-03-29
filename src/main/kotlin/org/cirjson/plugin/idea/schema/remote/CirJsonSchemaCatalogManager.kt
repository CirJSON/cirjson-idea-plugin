package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration

class CirJsonSchemaCatalogManager(private val myProject: Project) {

    fun getSchemaFileForFile(file: VirtualFile): VirtualFile? {
        if (!CirJsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled) {
            return null
        }

        if (CirJsonSchemaCatalogExclusion.EP_NAME.findFirstSafe { it.isExcluded(file) } != null) {
            return null
        }

        return null
    }

}