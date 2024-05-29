package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.TableView
import com.intellij.util.ui.StatusText
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonMappingKind
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import javax.swing.table.TableCellEditor

internal class CirJsonMappingsTableView(runnable: CirJsonSchemaMappingsView.MyAddActionButtonRunnable) :
        TableView<UserDefinedCirJsonSchemaConfiguration.Item>() {

    private val myEmptyText = object : StatusText() {

        override fun isStatusVisible(): Boolean {
            return isEmpty
        }

    }.apply {
        text = CirJsonBundle.message("cirjson.schema.no.schema.mappings.defined")
        appendSecondaryText("${CirJsonBundle.message("cirjson.schema.add.mapping.for.a")} ",
                SimpleTextAttributes.REGULAR_ATTRIBUTES, null)
        val values = CirJsonMappingKind.entries

        for (i in values.indices) {
            val kind = values[i]
            appendSecondaryText(kind.description, SimpleTextAttributes.LINK_ATTRIBUTES) { runnable.doRun(kind) }

            if (i < values.lastIndex) {
                appendSecondaryText(", ", SimpleTextAttributes.REGULAR_ATTRIBUTES, null)
            }
        }
    }

    init {
        focusTraversalKeysEnabled = false
    }

    override fun setCellEditor(anEditor: TableCellEditor?) {
        super.setCellEditor(anEditor)

        (anEditor as? CirJsonMappingsTableCellEditor)?.myComponent?.textField?.requestFocus()
    }

    override fun getEmptyText() = myEmptyText

}