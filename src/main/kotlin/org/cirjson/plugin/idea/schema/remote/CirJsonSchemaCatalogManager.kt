package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogEntry
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonCachedValues

class CirJsonSchemaCatalogManager(private val myProject: Project) {

    private var myCatalog: VirtualFile? = null

    private var myResolvedMappings = ContainerUtil.createConcurrentSoftMap<VirtualFile, String>()

    fun getSchemaFileForFile(file: VirtualFile): VirtualFile? {
        if (!CirJsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled) {
            return null
        }

        if (CirJsonSchemaCatalogExclusion.EP_NAME.findFirstSafe { it.isExcluded(file) } != null) {
            return null
        }

        var schemaUrl = myResolvedMappings[file]

        if (schemaUrl == EMPTY) {
            return null
        }

        if (schemaUrl == null && myCatalog != null) {
            schemaUrl = resolveSchemaFile(file, myCatalog!!, myProject)

            if (NO_CACHE == schemaUrl) {
                return null
            }

            myResolvedMappings[file] = schemaUrl ?: EMPTY
        }

        TODO()
    }

    private class FileMatcher(private val myEntry: CirJsonSchemaCatalogEntry)

    companion object {

        private const val NO_CACHE = "\$_\$_WS_NO_CACHE_\$_\$"

        private const val EMPTY: String = "\$_\$_WS_EMPTY_\$_\$"

        private fun resolveSchemaFile(file: VirtualFile, catalogFile: VirtualFile, project: Project): String? {
            CirJsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile, project)

            val schemaCatalog = CirJsonCachedValues.getSchemaCatalog(catalogFile, project)
                    ?: return if (catalogFile is HttpVirtualFile) NO_CACHE else null

            val fileMatchers = ContainerUtil.map(schemaCatalog) { FileMatcher(it) }

            TODO()
        }

    }

}