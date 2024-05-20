package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion

class CirJsonSchemaProjectSelfProviderFactory : CirJsonSchemaProviderFactory, DumbAware {

    override fun getProviders(project: Project): List<CirJsonSchemaFileProvider> {
        return listOf(MyCirJsonSchemaFileProvider(project, SCHEMA_CIRJSON_FILE_NAME))
    }

    class MyCirJsonSchemaFileProvider(private val myProject: Project, override val name: String) :
            CirJsonSchemaFileProvider {

        val isSchemaV1 = name == SCHEMA_CIRJSON_FILE_NAME

        override fun isAvailable(file: VirtualFile): Boolean {
            if (myProject.isDisposed) {
                return false
            }

            val service = CirJsonSchemaService.get(myProject)

            if (!service.isApplicableToFile(file)) {
                return false
            }

            val schemaVersion = service.getSchemaVersion(file) ?: return false
            return when (schemaVersion) {
                CirJsonSchemaVersion.SCHEMA_1 -> isSchemaV1
            }
        }

        override val schemaVersion: CirJsonSchemaVersion = CirJsonSchemaVersion.SCHEMA_1

        override val schemaFile: VirtualFile?
            get() = CirJsonSchemaProviderFactory.getResourceFile(CirJsonSchemaProjectSelfProviderFactory::class.java,
                    "/cirJsonSchema/$name")

        override val schemaType: SchemaType = SchemaType.SCHEMA

        override val remoteSource: String?
            get() = when (name) {
                SCHEMA_CIRJSON_FILE_NAME -> "http://cirjson.org/draft-01/schema"
                else -> null
            }

        override val presentableName: String
            get() = when (name) {
                SCHEMA_CIRJSON_FILE_NAME -> CirJsonBundle.message("schema.of.version", 1)
                else -> name
            }

    }

    companion object {

        const val TOTAL_PROVIDERS = 3

        const val SCHEMA_CIRJSON_FILE_NAME = "schema.cirjson"

    }

}