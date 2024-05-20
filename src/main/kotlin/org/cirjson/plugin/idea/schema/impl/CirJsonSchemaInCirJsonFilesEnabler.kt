package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.CirJsonUtil
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaEnabler

class CirJsonSchemaInCirJsonFilesEnabler : CirJsonSchemaEnabler {

    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        return CirJsonUtil.isCirJsonFile(file, project)
    }

    override fun canBeSchemaFile(file: VirtualFile): Boolean {
        return isEnabledForFile(file, null)
    }

}