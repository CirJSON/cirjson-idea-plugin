package org.cirjson.plugin.idea.schema.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.kotlin.compareToWithIgnoreCare
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo

object CirJsonSchemaStatusPopup {

    internal val ADD_MAPPING = object : CirJsonSchemaInfo("") {

        override val description: String = CirJsonBundle.message("schema.widget.add.mapping")

    }

    internal val IGNORE_FILE = object : CirJsonSchemaInfo("") {

        override val description: String = CirJsonBundle.message("schema.widget.no.mapping")

    }

    internal val STOP_IGNORE_FILE = object : CirJsonSchemaInfo("") {

        override val description: String = CirJsonBundle.message("schema.widget.stop.ignore.file")

    }

    internal val EDIT_MAPPINGS = object : CirJsonSchemaInfo("") {

        override val description: String = CirJsonBundle.message("schema.widget.edit.mappings")

    }

    internal val LOAD_REMOTE = object : CirJsonSchemaInfo("") {

        override val description: String = CirJsonBundle.message("schema.widget.load.mappings")

    }

    internal fun createPopup(service: CirJsonSchemaService, project: Project, virtualFile: VirtualFile,
            showOnlyEdit: Boolean): ListPopup {
        val step = createPopupStep(service, project, virtualFile, showOnlyEdit)
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    internal fun createPopupStep(service: CirJsonSchemaService, project: Project, virtualFile: VirtualFile,
            showOnlyEdit: Boolean): CirJsonSchemaInfoPopupStep {
        val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)
        val mapping = configuration.findMappingForFile(virtualFile)
        val allSchemas = if (!showOnlyEdit || mapping == null) {
            val infos = service.allUserVisibleSchemas
            val comparator = Comparator.comparing(CirJsonSchemaInfo::description, String::compareToWithIgnoreCare)
            val registered = infos.filter { it.provider != null }.sortedWith(comparator)
            var otherList = listOf<CirJsonSchemaInfo>()

            if (CirJsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled) {
                otherList = infos.filter { it.provider == null }.sortedWith(comparator)

                if (otherList.isEmpty()) {
                    otherList = listOf(LOAD_REMOTE)
                }
            }

            (registered + otherList).toMutableList().apply {
                add(0, if (mapping == null) ADD_MAPPING else EDIT_MAPPINGS)
            }
        } else {
            SmartList(EDIT_MAPPINGS)
        }

        if (configuration.isIgnoredFile(virtualFile)) {
            allSchemas.add(0, STOP_IGNORE_FILE)
        } else {
            allSchemas.add(0, IGNORE_FILE)
        }

        return CirJsonSchemaInfoPopupStep(allSchemas, project, virtualFile, service, null)
    }

}