package org.cirjson.plugin.idea.schema

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaEnabler
import javax.swing.Icon

class CirJsonSchemaIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        project ?: return null

        if (CirJsonSchemaEnabler.EXTENSION_POINT_NAME.extensionList.any { it.canBeSchemaFile(file) }) {
            val service = CirJsonSchemaService.get(project)

            if (service.isApplicableToFile(file) && service.isSchemaFile(file)) {
                return AllIcons.FileTypes.JsonSchema
            }
        }

        return null
    }

}