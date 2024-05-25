package org.cirjson.plugin.idea.schema.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.StatusText
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo

open class CirJsonSchemaInfoPopupStep(allSchemas: List<CirJsonSchemaInfo>, private val myProject: Project,
        private val myVirtualFile: VirtualFile, private val myService: CirJsonSchemaService, title: String?) :
        BaseListPopupStep<CirJsonSchemaInfo>(title, allSchemas), ListPopupStepEx<CirJsonSchemaInfo> {

    override fun getTooltipTextFor(value: CirJsonSchemaInfo?): String? {
        TODO("Not yet implemented")
    }

    override fun setEmptyText(emptyText: StatusText) {
        TODO("Not yet implemented")
    }

}