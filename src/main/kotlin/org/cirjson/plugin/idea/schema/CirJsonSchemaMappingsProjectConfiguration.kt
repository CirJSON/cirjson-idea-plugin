package org.cirjson.plugin.idea.schema

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.util.*

@State(name = "CirJsonSchemaMappingsProjectConfiguration", storages = [Storage("cirJsonSchemas.xml")])
open class CirJsonSchemaMappingsProjectConfiguration(private val myProject: Project) :
        PersistentStateComponent<CirJsonSchemaMappingsProjectConfiguration.MyState> {

    @Volatile
    var myState = MyState()

    fun findMappingForFile(file: VirtualFile): UserDefinedCirJsonSchemaConfiguration? {
        val projectBaseDir = myProject.baseDir

        for (configuration in myState.myState.values) {
            for (pattern in configuration.patterns) {
                if (pattern.mappingKind != CirJsonMappingKind.FILE) {
                    continue
                }

                val relativeFile = VfsUtil.findRelativeFile(projectBaseDir, *pattern.pathParts)

                if (relativeFile == file
                        || file.url == UserDefinedCirJsonSchemaConfiguration.Item.neutralizePath(pattern.path!!)) {
                    return configuration
                }
            }
        }

        return null
    }

    override fun getState(): MyState? {
        return myState
    }

    fun schemaFileMoved(project: Project, oldRelativePath: String, newRelativePath: String) {
        val configuration =
                myState.myState.values.firstOrNull { FileUtil.pathsEqual(it.relativePathToSchema, oldRelativePath) }
                        ?: return
        configuration.relativePathToSchema = newRelativePath
        CirJsonSchemaService.get(project).reset()
    }

    override fun loadState(state: MyState) {
        myState = state
        CirJsonSchemaService.get(myProject).reset()
    }

    fun isIgnoredFile(virtualFile: VirtualFile): Boolean {
        val mappingForFile = findMappingForFile(virtualFile) ?: return false
        return mappingForFile.isIgnoredFile
    }

    class MyState(@Tag("state") @XCollection val myState: Map<String, UserDefinedCirJsonSchemaConfiguration>) {

        constructor() : this(TreeMap())

    }

    companion object {

        fun getInstance(project: Project): CirJsonSchemaMappingsProjectConfiguration {
            return project.getService(CirJsonSchemaMappingsProjectConfiguration::class.java)
        }

    }

}