package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.jetbrains.jsonSchema.settings.mappings.TreeUpdater
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import java.util.function.Function

open class CirJsonSchemaMappingsConfigurable(private val myProject: Project) : MasterDetailsComponent(),
        SearchableConfigurable, Disposable {

    private var myInitializer: Runnable? = null

    var initializer: Runnable?
        get() = throw IllegalAccessException("Can't get this value")
        set(value) {
            myInitializer = value
        }

    private var myError: String? = null

    private val myTreeUpdater = TreeUpdater {
        TREE_UPDATER.run()
        updateWarningText(it)
    }

    private val myNameCreator = Function<String, String> {
        createUniqueName(it)
    }

    init {
        @Suppress("LeakingThis")
        initTree()
    }

    private fun updateWarningText(showWarning: Boolean) {
        TODO()
    }

    private fun createUniqueName(s: String): String {
        TODO()
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

    @Suppress("CompanionObjectInExtension")
    companion object {

        const val SETTINGS_CIRJSON_SCHEMA = "settings.cirjson.schema"

        internal val STUB_SCHEMA_NAME = CirJsonBundle.message("new.schema")

        private val COMPARATOR = Comparator<UserDefinedCirJsonSchemaConfiguration> { o1, o2 ->
            if (o1.isApplicationDefined != o2.isApplicationDefined) {
                if (o1.isApplicationDefined) 1 else -1
            } else {
                o1.name!!.compareTo(o2.name!!, true)
            }
        }

    }

}