package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.ui.table.TableView
import com.intellij.util.ui.StatusText
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration

internal class CirJsonMappingsTableView(runnable: CirJsonSchemaMappingsView.MyAddActionButtonRunnable) :
        TableView<UserDefinedCirJsonSchemaConfiguration.Item>() {

    private val myEmptyText = object : StatusText() {

        override fun isStatusVisible(): Boolean {
            return isEmpty
        }

    }

}