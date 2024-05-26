package org.cirjson.plugin.idea.schema.widget

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.StatusText
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.settings.mappings.CirJsonSchemaMappingsConfigurable
import java.util.*
import javax.swing.Icon

open class CirJsonSchemaInfoPopupStep(allSchemas: List<CirJsonSchemaInfo>, private val myProject: Project,
        private val myVirtualFile: VirtualFile?, private val myService: CirJsonSchemaService, title: String?) :
        BaseListPopupStep<CirJsonSchemaInfo>(title, allSchemas), ListPopupStepEx<CirJsonSchemaInfo> {

    override fun onChosen(selectedValue: CirJsonSchemaInfo?, finalChoice: Boolean): PopupStep<*>? {
        assert(myVirtualFile != null) { "override this method to do without a virtual file!" }
        myVirtualFile!!

        if (!finalChoice) {
            return FINAL_CHOICE
        }

        return when (selectedValue) {
            CirJsonSchemaStatusPopup.EDIT_MAPPINGS, CirJsonSchemaStatusPopup.ADD_MAPPING -> {
                doFinalStep { runSchemaEditorForCurrentFile() }
            }

            CirJsonSchemaStatusPopup.LOAD_REMOTE -> {
                doFinalStep { myService.triggerUpdateRemote() }
            }

            CirJsonSchemaStatusPopup.IGNORE_FILE -> {
                markIgnored(myVirtualFile, myProject)
                doFinalStep { myService.reset() }
            }

            CirJsonSchemaStatusPopup.STOP_IGNORE_FILE -> {
                unmarkIgnored(myVirtualFile, myProject)
                doFinalStep { myService.reset() }
            }

            else -> {
                setMapping(selectedValue, myVirtualFile, myProject)
                doFinalStep { myService.reset() }
            }
        }
    }

    protected open fun runSchemaEditorForCurrentFile() {
        assert(myVirtualFile != null) { "override this method to do without a virtual file!" }
        myVirtualFile!!

        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, CirJsonSchemaMappingsConfigurable::class.java) {
            // For some reason, CirJsonSchemaMappingsConfigurable.reset is called right after this callback, leading to
            // resetting the customization.
            // Workaround: move this logic inside CirJsonSchemaMappingsConfigurable.reset.

            it.initializer = Runnable {
                val mappings = CirJsonSchemaMappingsProjectConfiguration.getInstance(myProject)
                var configuration = mappings.findMappingForFile(myVirtualFile)

                if (configuration == null) {
                    configuration = it.addProjectSchema()
                    val relativePath = VfsUtil.getRelativePath(myVirtualFile, myProject.baseDir)
                    (configuration.patterns as MutableList).add(
                            UserDefinedCirJsonSchemaConfiguration.Item(relativePath ?: myVirtualFile.url,
                                    isPattern = false, isDirectory = false))
                }

                it.selectInTree(configuration)
            }
        }
    }

    protected open fun setMapping(selectedValue: CirJsonSchemaInfo?, virtualFile: VirtualFile?, project: Project) {
        assert(virtualFile != null) { "override this method to do without a virtual file!" }
        virtualFile!!

        val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)

        val projectBaseDir = project.baseDir

        val mappingForFile = configuration.findMappingForFile(virtualFile)

        if (mappingForFile != null) {
            for (pattern in mappingForFile.patterns!!) {
                if (VfsUtil.findRelativeFile(projectBaseDir,
                                *pattern.pathParts) == virtualFile || virtualFile.url == UserDefinedCirJsonSchemaConfiguration.Item.neutralizePath(
                                pattern.path)) {
                    (mappingForFile.patterns as MutableList).remove(pattern)

                    if (mappingForFile.patterns!!.isEmpty() && mappingForFile.isApplicationDefined) {
                        configuration.removeConfiguration(mappingForFile)
                    } else {
                        mappingForFile.refreshPatterns()
                    }

                    break
                }
            }
        }

        selectedValue ?: return

        val path = projectBaseDir?.let { VfsUtil.getRelativePath(virtualFile, it) } ?: virtualFile.url

        val existing = configuration.findMappingBySchemaInfo(selectedValue)
        val item = UserDefinedCirJsonSchemaConfiguration.Item(path, isPattern = false, isDirectory = false)

        if (existing != null) {
            if (item !in existing.patterns!!) {
                (existing.patterns as MutableList).add(item)
                existing.refreshPatterns()
            }
        } else {
            configuration.addConfiguration(
                    UserDefinedCirJsonSchemaConfiguration(selectedValue.description, selectedValue.schemaVersion,
                            selectedValue.getUrl(project), true, Collections.singletonList(item)))
        }
    }

    override fun isSpeedSearchEnabled(): Boolean = true

    override fun getTextFor(value: CirJsonSchemaInfo?): String {
        return value?.description ?: ""
    }

    override fun getIconFor(value: CirJsonSchemaInfo?): Icon {
        return when (value) {
            CirJsonSchemaStatusPopup.ADD_MAPPING -> AllIcons.General.Add
            CirJsonSchemaStatusPopup.EDIT_MAPPINGS -> AllIcons.Actions.Edit
            CirJsonSchemaStatusPopup.LOAD_REMOTE -> AllIcons.Actions.Refresh
            CirJsonSchemaStatusPopup.IGNORE_FILE -> AllIcons.Vcs.Ignore_file
            CirJsonSchemaStatusPopup.STOP_IGNORE_FILE -> AllIcons.Actions.AddFile
            else -> AllIcons.FileTypes.JsonSchema
        }
    }

    @Suppress("DialogTitleCapitalization")
    override fun getSeparatorAbove(value: CirJsonSchemaInfo): ListSeparator? {
        val values = values
        val index = values.indexOf(value)

        if (index - 1 >= 0) {
            val info = values[index - 1]

            if (info == CirJsonSchemaStatusPopup.EDIT_MAPPINGS || info == CirJsonSchemaStatusPopup.ADD_MAPPING) {
                return ListSeparator(CirJsonBundle.message("schema.widget.registered.schemas"))
            }

            if (value.provider == null && info.provider != null) {
                return ListSeparator(CirJsonBundle.message("schema.widget.store.schemas"))
            }
        }

        return null
    }

    override fun getTooltipTextFor(value: CirJsonSchemaInfo?): String? {
        return getDoc(value)
    }

    override fun setEmptyText(emptyText: StatusText) {
    }

    companion object {

        private fun markIgnored(virtualFile: VirtualFile, project: Project) {
            val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)
            configuration.markAsIgnored(virtualFile)
        }

        private fun unmarkIgnored(virtualFile: VirtualFile, project: Project) {
            val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)

            if (!configuration.isIgnoredFile(virtualFile)) {
                return
            }

            configuration.unmarkAsIgnored(virtualFile)
        }

        private fun getDoc(schema: CirJsonSchemaInfo?): String? {
            schema ?: return null
            schema.name ?: return schema.documentation
            schema.documentation ?: return schema.name

            return HtmlBuilder().apply {
                append(HtmlChunk.tag("b").addText(schema.name!!))
                append(HtmlChunk.br())
                appendRaw(schema.documentation!!)
            }.toString()
        }

    }

}