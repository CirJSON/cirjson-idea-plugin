package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import java.util.function.Function
import javax.swing.JComponent

class CirJsonSchemaConfigurable(private val myProject: Project, private val mySchemaFilePath: String,
        val schema: UserDefinedCirJsonSchemaConfiguration, private val myTreeUpdater: TreeUpdater?,
        private val myNameCreator: Function<in String, String>) :
        NamedConfigurable<UserDefinedCirJsonSchemaConfiguration>(true, Runnable { myTreeUpdater?.updateTree(true) }) {

    val uiSchema: UserDefinedCirJsonSchemaConfiguration
        get() {
            TODO()
        }

    override fun isModified(): Boolean {
        TODO("Not yet implemented")
    }

    override fun apply() {
        TODO("Not yet implemented")
    }

    override fun getDisplayName(): String {
        TODO("Not yet implemented")
    }

    override fun setDisplayName(name: String?) {
        TODO("Not yet implemented")
    }

    override fun getEditableObject(): UserDefinedCirJsonSchemaConfiguration {
        TODO("Not yet implemented")
    }

    override fun getBannerSlogan(): String {
        TODO("Not yet implemented")
    }

    override fun createOptionsPanel(): JComponent {
        TODO("Not yet implemented")
    }

    fun setError(error: String?, showWarning: Boolean) {
        TODO("Not yet implemented")
    }

}