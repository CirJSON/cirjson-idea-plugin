package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import java.util.function.BiConsumer
import javax.swing.JComponent

class CirJsonSchemaMappingsView(project: Project, private val myTreeUpdater: TreeUpdater?,
        private val mySchemaPathChangedCallback: BiConsumer<in String, in Boolean>) : Disposable {

    val component: JComponent
        get() = TODO()

    var isInitialized: Boolean = false
        private set

    val data: List<UserDefinedCirJsonSchemaConfiguration.Item>
        get() = TODO()

    val schemaVersion: CirJsonSchemaVersion
        get() = TODO()

    val schemaSubPath: String
        get() = TODO()

    override fun dispose() {
        TODO("not implemented")
    }

    fun setError(text: String?, showWarning: Boolean) {
        TODO()
    }

    fun setItems(schemaFilePath: String, version: CirJsonSchemaVersion,
            data: List<UserDefinedCirJsonSchemaConfiguration.Item>?) {
        TODO()
    }

}