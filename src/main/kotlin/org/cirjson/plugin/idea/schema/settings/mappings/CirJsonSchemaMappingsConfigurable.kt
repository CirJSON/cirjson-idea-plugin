package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.MasterDetailsComponent
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration

open class CirJsonSchemaMappingsConfigurable : MasterDetailsComponent(), SearchableConfigurable, Disposable {

    private var myInitializer: Runnable? = null

    var initializer: Runnable?
        get() = throw IllegalAccessException("Can't get this value")
        set(value) {
            myInitializer = value
        }

    override fun getDisplayName(): String {
        TODO("Not yet implemented")
    }

    override fun getEmptySelectionString(): String? {
        TODO("Not yet implemented")
    }

    override fun createActions(fromPopup: Boolean): MutableList<AnAction>? {
        TODO("Not yet implemented")
    }

    override fun apply() {
        TODO("Not yet implemented")
    }

    override fun isModified(): Boolean {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun getNodeComparator(): Comparator<MyNode> {
        TODO("Not yet implemented")
    }

    override fun getHelpTopic(): String? {
        TODO("Not yet implemented")
    }

    override fun getId(): String {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    fun addProjectSchema(): UserDefinedCirJsonSchemaConfiguration {
        TODO()
    }

    fun selectInTree(configuration: UserDefinedCirJsonSchemaConfiguration) {
        TODO()
    }

}