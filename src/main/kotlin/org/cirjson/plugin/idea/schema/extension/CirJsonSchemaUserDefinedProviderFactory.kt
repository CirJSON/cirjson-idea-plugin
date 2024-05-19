package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.io.File
import java.util.function.BiPredicate

class CirJsonSchemaUserDefinedProviderFactory : CirJsonSchemaProviderFactory, DumbAware {

    override fun getProviders(project: Project): List<CirJsonSchemaFileProvider> {
        val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)

        val map = configuration.stateMap
        val providers = map.values.map { createProvider(project, it) }
        return providers
    }

    internal fun createProvider(project: Project, schema: UserDefinedCirJsonSchemaConfiguration): MyProvider {
        val relPath = schema.relativePathToSchema
        val schemaVersion = schema.schemaVersion
        val name = schema.name!!
        val myFile = if (CirJsonFileResolver.isAbsoluteUrl(relPath) || FileUtil.isAbsolute(relPath)) {
            relPath
        } else {
            File(project.basePath, relPath).absolutePath
        }
        val myPatterns = schema.calculatedPatterns
        return MyProvider(project, schemaVersion, name, myFile, myPatterns)
    }

    internal class MyProvider(private val myProject: Project, override val schemaVersion: CirJsonSchemaVersion,
            override val name: String, private val myFile: String,
            private val myPatterns: List<BiPredicate<Project, VirtualFile>>) : CirJsonSchemaFileProvider,
            CirJsonSchemaImportedProviderMarker {

        private var myVirtualFile: VirtualFile? = null

        override val schemaFile: VirtualFile?
            get() {
                if (myVirtualFile != null && myVirtualFile!!.isValid) {
                    return myVirtualFile
                }

                val path = myFile
                myVirtualFile = if (CirJsonFileResolver.isAbsoluteUrl(path)) {
                    CirJsonFileResolver.urlToFile(path)
                } else {
                    val lfs = LocalFileSystem.getInstance()
                    lfs.findFileByPath(path) ?: lfs.refreshAndFindFileByPath(path)
                }

                return myVirtualFile
            }

        override val schemaType: SchemaType = SchemaType.USER_SCHEMA

        override fun isAvailable(file: VirtualFile): Boolean {
            if (myPatterns.isEmpty() || file.isDirectory || !file.isValid) {
                return false
            }

            return myPatterns.any { it.test(myProject, file) }
        }

        override val remoteSource: String?
            get() = if (CirJsonFileResolver.isHttpPath(myFile)) myFile else null

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != other?.javaClass) {
                return false
            }

            other as MyProvider

            if (name != other.name) {
                return false
            }

            return FileUtil.pathsEqual(myFile, other.myFile)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + FileUtil.pathHashCode(myFile)
            return result
        }

    }

}