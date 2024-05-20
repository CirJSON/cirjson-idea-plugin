package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.cirjson.plugin.idea.schema.CirJsonSchemaService

interface CirJsonSchemaProviderFactory {

    /**
     * Called in smart mode by default. Implement [com.intellij.openapi.project.DumbAware] to be called in dumb mode.
     *
     * @param project the project
     */
    fun getProviders(project: Project): List<CirJsonSchemaFileProvider>

    companion object {

        private val LOG = Logger.getInstance(CirJsonSchemaProviderFactory::class.java)

        val EP_NAME = ExtensionPointName.create<CirJsonSchemaProviderFactory>(
                "org.cirjson.plugin.idea.javaScript.cirJsonSchema.providerFactory")

        /**
         * Finds a [VirtualFile] instance corresponding to a specified resource path (relative or absolute).
         *
         * @param baseClass The Class used to get the resource
         * @param resourcePath String identifying a resource (relative or absolute). See [Class.getResource] for more
         * details
         *
         * @return [VirtualFile] instance, or `null` if not found
         */
        fun getResourceFile(baseClass: Class<*>, resourcePath: String): VirtualFile? {
            val url = baseClass.getResource(resourcePath)

            if (url == null) {
                LOG.error("Cannot find resource $resourcePath")
                return null
            }

            val file = VfsUtil.findFileByURL(url)

            if (file != null) {
                return file
            }

            LOG.info("File not found by $url, performing refresh...")
            ApplicationManager.getApplication().invokeLaterOnWriteThread {
                val refreshed = WriteAction.compute<VirtualFile, RuntimeException> {
                    VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.convertFromUrl(url))
                }

                if (refreshed != null) {
                    LOG.info("Refreshed $url successfully")

                    for (project in ProjectManager.getInstance().openProjects) {
                        val service = project.getService(CirJsonSchemaService::class.java)
                        service.reset()
                    }
                } else {
                    LOG.error("Cannot refresh and find file by $resourcePath")
                }
            }

            return null
        }

    }

}