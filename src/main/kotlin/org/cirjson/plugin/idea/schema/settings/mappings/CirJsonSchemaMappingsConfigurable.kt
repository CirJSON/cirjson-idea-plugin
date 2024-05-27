package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.util.ThreeState
import com.intellij.util.containers.MultiMap
import com.jetbrains.jsonSchema.settings.mappings.TreeUpdater
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
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
        val patternsMap = MultiMap<String, UserDefinedCirJsonSchemaConfiguration.Item>()
        val sb = StringBuilder()
        val list = try {
            getUiList(false)
        } catch (e: ConfigurationException) {
            return
        }

        for (info in list) {
            info.refreshPatterns()
            val comparator = CirJsonSchemaPatternComparator(myProject)
            val patterns = info.patterns!!

            for (pattern in patterns) {
                for (entry in patternsMap.entrySet()) {
                    for (item in entry.value) {
                        val similar = comparator.isSimilar(pattern, item)

                        if (similar == ThreeState.NO) {
                            continue
                        }

                        if (sb.isNotEmpty()) {
                            sb.append('\n')
                        }

                        sb.append(CirJsonBundle.message("schema.configuration.error.conflicting.mappings.desc",
                                pattern.mappingKind, pattern.presentation, info.name!!, item.presentation, entry.key))
                    }
                }
            }

            patternsMap[info.name!!] = patterns
        }

        myError = if (sb.isNotEmpty()) {
            CirJsonBundle.message("schema.configuration.error.conflicting.mappings.title", sb.toString())
        } else {
            null
        }
        val children = myRoot.children()

        while (children.hasMoreElements()) {
            val node = children.nextElement()

            if (node is MyNode && node.configurable is CirJsonSchemaConfigurable) {
                (node.configurable as CirJsonSchemaConfigurable).setError(myError, showWarning)
            }
        }
    }

    @Throws(ConfigurationException::class)
    private fun getUiList(applyChildren: Boolean): List<UserDefinedCirJsonSchemaConfiguration> {
        val uiList = mutableListOf<UserDefinedCirJsonSchemaConfiguration>()
        val children = myRoot.children()

        while (children.hasMoreElements()) {
            val node = children.nextElement() as MyNode

            if (applyChildren) {
                node.configurable.apply()
                uiList.add(getSchemaInfo(node))
            } else {
                uiList.add((node.configurable as CirJsonSchemaConfigurable).uiSchema)
            }
        }

        uiList.sortWith(COMPARATOR)
        return uiList
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

        private fun getSchemaInfo(node: MyNode): UserDefinedCirJsonSchemaConfiguration {
            return (node.configurable as CirJsonSchemaConfigurable).schema
        }

    }

}