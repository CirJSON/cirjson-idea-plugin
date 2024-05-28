package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.ui.EditorNotifications
import com.intellij.util.IconUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.MultiMap
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.io.File
import java.util.function.Function
import javax.swing.tree.DefaultTreeModel
import kotlin.math.max

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
        var max = -1
        val children = myRoot.children()

        while (children.hasMoreElements()) {
            val element = children.nextElement()

            if (element !is MyNode) {
                continue
            }

            val displayName = element.displayName

            if (displayName.startsWith(s)) {
                val lastPart = displayName.substring(s.length).trim()

                if (lastPart.isEmpty() && max == -1) {
                    max = 1
                    continue
                }

                val i = lastPart.toIntOrNull() ?: -1

                max = max(i, max)
            }
        }

        return if (max == -1) s else "$s ${max + 1}"
    }

    override fun getDisplayName(): String {
        return CirJsonBundle.message("configurable.CirJsonSchemaMappingsConfigurable.display.name")
    }

    override fun getEmptySelectionString(): String {
        return if (myRoot.children().hasMoreElements()) {
            CirJsonBundle.message("schema.configuration.mapping.empty.area.string")
        } else {
            CirJsonBundle.message("schema.configuration.mapping.empty.area.alt.string")
        }
    }

    override fun createActions(fromPopup: Boolean): List<AnAction> {
        val result = arrayListOf<AnAction>()
        result.add(object :
                DumbAwareAction(CirJsonBundle.message("action.DumbAware.CirJsonSchemaMappingsConfigurable.text.add"),
                        CirJsonBundle.message("action.DumbAware.CirJsonSchemaMappingsConfigurable.description.add"),
                        IconUtil.addIcon) {

            init {
                registerCustomShortcutSet(CommonShortcuts.INSERT, myTree)
            }

            override fun actionPerformed(e: AnActionEvent) {
                addProjectSchema()
            }

        })
        result.add(MyDeleteAction())
        return result
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val uiList = getUiList(true)
        validate(uiList)
        val projectMap = hashMapOf<String, UserDefinedCirJsonSchemaConfiguration>()

        for (info in uiList) {
            projectMap[info.name!!] = info
        }

        CirJsonSchemaMappingsProjectConfiguration.getInstance(myProject).stateMap = projectMap
        val projects = ProjectManager.getInstance().openProjects

        for (project in projects) {
            val service = CirJsonSchemaService.get(project) as CirJsonSchemaService?
            service?.reset()
        }

        DaemonCodeAnalyzer.getInstance(myProject).restart()
        EditorNotifications.getInstance(myProject).updateAllNotifications()
    }

    override fun isModified(): Boolean {
        val storedList = storedList
        val uiList = try {
            getUiList(false)
        } catch (e: ConfigurationException) {
            return false
        }

        return storedList == uiList
    }

    private val storedList: List<UserDefinedCirJsonSchemaConfiguration>
        get() {
            val list = ArrayList<UserDefinedCirJsonSchemaConfiguration>()
            val projectState = CirJsonSchemaMappingsProjectConfiguration.getInstance(myProject).stateMap
            list.addAll(projectState.values)
            list.sortWith(COMPARATOR)
            return list
        }

    override fun reset() {
        fillTree()
        updateWarningText(true)

        if (myInitializer != null) {
            myInitializer!!.run()
            myInitializer = null
        }
    }

    private fun fillTree() {
        myRoot.removeAllChildren()

        if (myProject.isDefault) {
            return
        }

        val list = storedList

        for (info in list) {
            val pathToSchema = info.relativePathToSchema
            val schemaFilePath = if (CirJsonFileResolver.isAbsoluteUrl(pathToSchema) || File(pathToSchema).isAbsolute) {
                pathToSchema
            } else {
                File(myProject.basePath, pathToSchema).path
            }
            val configurable = CirJsonSchemaConfigurable(myProject, schemaFilePath, info, myTreeUpdater, myNameCreator)
            configurable.setError(myError, true)
            myRoot.add(MyNode(configurable))
        }

        (myTree.model as DefaultTreeModel).reload(myRoot)

        if (myRoot.children().hasMoreElements()) {
            myTree.addSelectionRow(0)
        }
    }

    override fun getNodeComparator(): Comparator<MyNode> {
        return Comparator { o1, o2 ->
            if (o1.configurable is CirJsonSchemaConfigurable && o2.configurable is CirJsonSchemaConfigurable) {
                COMPARATOR.compare(getSchemaInfo(o1), getSchemaInfo(o2))
            } else {
                o1.displayName.compareTo(o2.displayName, true)
            }
        }
    }

    override fun getHelpTopic(): String? {
        return SETTINGS_CIRJSON_SCHEMA
    }

    override fun getId(): String {
        return SETTINGS_CIRJSON_SCHEMA
    }

    override fun dispose() {
        val children = myRoot.children()

        while (children.hasMoreElements()) {
            val child = children.nextElement()
            (child as? MyNode)?.configurable?.disposeUIResources()
        }
    }

    fun addProjectSchema(): UserDefinedCirJsonSchemaConfiguration {
        val configuration =
                UserDefinedCirJsonSchemaConfiguration(createUniqueName(STUB_SCHEMA_NAME), CirJsonSchemaVersion.SCHEMA_1,
                        "", false, null)
        configuration.generatedName = configuration.name
        addCreatedMappings(configuration)
        return configuration
    }

    private fun addCreatedMappings(info: UserDefinedCirJsonSchemaConfiguration) {
        val configurable = CirJsonSchemaConfigurable(myProject, "", info, myTreeUpdater, myNameCreator)
        configurable.setError(myError, true)
        val node = MyNode(configurable)
        addNode(node, myRoot)
        selectNodeInTree(node, true)
    }

    fun selectInTree(configuration: UserDefinedCirJsonSchemaConfiguration) {
        val children = myRoot.children()

        while (children.hasMoreElements()) {
            val node = children.nextElement() as MyNode
            val configurable = node.configurable as CirJsonSchemaConfigurable

            if (configurable.uiSchema == configuration) {
                selectNodeInTree(node)
            }
        }
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

        @Throws(ConfigurationException::class)
        private fun validate(list: List<UserDefinedCirJsonSchemaConfiguration>) {
            val set = hashSetOf<String>()

            for (info in list) {
                if (info.name!! in set) {
                    throw ConfigurationException(
                            CirJsonBundle.message("schema.configuration.error.duplicate.name", info.name!!))
                }

                set.add(info.name!!)
            }
        }

    }

}