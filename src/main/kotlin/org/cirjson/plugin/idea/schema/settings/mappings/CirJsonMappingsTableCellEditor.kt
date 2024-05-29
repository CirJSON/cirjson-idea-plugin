package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.project.Project
import com.intellij.util.ui.AbstractTableCellEditor
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import java.awt.Component
import javax.swing.JTable

class CirJsonMappingsTableCellEditor(private val myItem: UserDefinedCirJsonSchemaConfiguration.Item,
        private val myProject: Project, private val myTreeUpdater: TreeUpdater?) : AbstractTableCellEditor() {

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int,
            column: Int): Component {
        TODO("Not yet implemented")
    }

    override fun getCellEditorValue(): Any {
        TODO("Not yet implemented")
    }

}