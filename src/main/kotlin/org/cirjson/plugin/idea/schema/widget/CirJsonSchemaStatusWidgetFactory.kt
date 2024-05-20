package org.cirjson.plugin.idea.schema.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import kotlinx.coroutines.CoroutineScope
import org.cirjson.plugin.idea.CirJsonBundle

class CirJsonSchemaStatusWidgetFactory : StatusBarEditorBasedWidgetFactory() {

    override fun getId(): String = CirJsonSchemaStatusWidget.ID

    override fun getDisplayName(): String {
        return CirJsonBundle.message("schema.widget.display.name")
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        val project = statusBar.project ?: return false

        val editor = getFileEditor(statusBar)
        return CirJsonSchemaStatusWidget.isAvailableOnFile(project, editor?.file)
    }

    override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget {
        return CirJsonSchemaStatusWidget(project, scope)
    }

}