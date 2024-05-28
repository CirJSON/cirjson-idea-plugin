package org.cirjson.plugin.idea.schema

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import java.io.File
import java.util.*

@State(name = "CirJsonSchemaMappingsProjectConfiguration", storages = [Storage("cirJsonSchemas.xml")])
open class CirJsonSchemaMappingsProjectConfiguration(private val myProject: Project) :
        PersistentStateComponent<CirJsonSchemaMappingsProjectConfiguration.MyState> {

    @Volatile
    var myState = MyState()

    fun findMappingForFile(file: VirtualFile?): UserDefinedCirJsonSchemaConfiguration? {
        val projectBaseDir = myProject.baseDir

        for (configuration in myState.myState.values) {
            for (pattern in configuration.patterns!!) {
                if (pattern.mappingKind != CirJsonMappingKind.FILE) {
                    continue
                }

                val relativeFile = VfsUtil.findRelativeFile(projectBaseDir, *pattern.pathParts)

                if (relativeFile == file
                        || file!!.url == UserDefinedCirJsonSchemaConfiguration.Item.neutralizePath(pattern.path)) {
                    return configuration
                }
            }
        }

        return null
    }

    override fun getState(): MyState? {
        return myState
    }

    var stateMap: Map<String, UserDefinedCirJsonSchemaConfiguration>
        get() = Collections.unmodifiableMap(myState.myState)
        set(value) {
            myState = MyState(value)
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

    fun addConfiguration(configuration: UserDefinedCirJsonSchemaConfiguration) {
        var name = configuration.name!!

        while (name in myState.myState) {
            name += "1"
        }

        (myState.myState as MutableMap)[name] = configuration
    }

    fun removeConfiguration(configuration: UserDefinedCirJsonSchemaConfiguration) {
        for (entry in myState.myState) {
            if (entry.value == configuration) {
                (myState.myState as MutableMap).remove(entry.key)
                return
            }
        }
    }

    fun findMappingBySchemaInfo(value: CirJsonSchemaInfo): UserDefinedCirJsonSchemaConfiguration? {
        for (configuration in myState.myState.values) {
            if (areSimilar(value, configuration)) {
                return configuration
            }
        }

        return null
    }

    fun areSimilar(value: CirJsonSchemaInfo, configuration: UserDefinedCirJsonSchemaConfiguration): Boolean {
        return normalizePath(value.getUrl(myProject)) == normalizePath(configuration.relativePathToSchema)
    }

    fun normalizePath(valueUrl: String): String {
        var realValueUrl = valueUrl

        if (StringUtil.contains(realValueUrl, "..")) {
            realValueUrl = File(realValueUrl).absolutePath
        }

        return realValueUrl.replace('\\', '/')
    }

    fun markAsIgnored(virtualFile: VirtualFile) {
        val existingMapping = findMappingForFile(virtualFile)

        if (existingMapping != null) {
            removeConfiguration(existingMapping)
        }

        addConfiguration(createIgnoreSchema(virtualFile.url))
    }

    fun unmarkAsIgnored(virtualFile: VirtualFile) {
        if (isIgnoredFile(virtualFile)) {
            val existingMapping = findMappingForFile(virtualFile)
            existingMapping?.let { removeConfiguration(it) }
        }
    }

    class MyState(@Tag("state") @XCollection val myState: Map<String, UserDefinedCirJsonSchemaConfiguration>) {

        constructor() : this(TreeMap())

    }

    companion object {

        fun getInstance(project: Project): CirJsonSchemaMappingsProjectConfiguration {
            return project.getService(CirJsonSchemaMappingsProjectConfiguration::class.java)
        }

        private fun createIgnoreSchema(ignoredFileUrl: String): UserDefinedCirJsonSchemaConfiguration {
            val schemaConfiguration =
                    UserDefinedCirJsonSchemaConfiguration(CirJsonBundle.message("schema.widget.no.schema.label"),
                            CirJsonSchemaVersion.SCHEMA_1, "", true, Collections.singletonList(
                            UserDefinedCirJsonSchemaConfiguration.Item(ignoredFileUrl, isPattern = false,
                                    isDirectory = false)))
            schemaConfiguration.isIgnoredFile = true
            return schemaConfiguration
        }

    }

}