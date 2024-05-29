package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import java.util.function.BiConsumer
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JEditorPane

class CirJsonSchemaMappingsView(private val myProject: Project, private val myTreeUpdater: TreeUpdater?,
        private val mySchemaPathChangedCallback: BiConsumer<in String, in Boolean>) : Disposable {

    private val myTableView: TableView<UserDefinedCirJsonSchemaConfiguration.Item>

    val component: JComponent

    private val mySchemaField: TextFieldWithBrowseButton

    private val mySchemaVersionComboBox: ComboBox<CirJsonSchemaVersion>

    private val myError: JEditorPane

    private var myErrorText: String = ""

    private val myErrorIcon: JBLabel

    var isInitialized: Boolean = false
        private set

    val data: List<UserDefinedCirJsonSchemaConfiguration.Item>
        get() = TODO()

    val schemaVersion: CirJsonSchemaVersion
        get() = TODO()

    val schemaSubPath: String
        get() = TODO()

    init {
        val addActionButtonRunnable = MyAddActionButtonRunnable()

        myTableView = CirJsonMappingsTableView(addActionButtonRunnable)
        // TODO: setup myTableView

        val schemaFieldBacking = JBTextField()
        mySchemaField = TextFieldWithBrowseButton(schemaFieldBacking)
        // TODO: setup mySchemaField

        myError = SwingHelper.createHtmlLabel(CirJsonBundle.message("cirjson.schema.conflicting.mappings"), null) {
        }
        // TODO: setup myError

        val builder = FormBuilder.createFormBuilder()
        mySchemaVersionComboBox = ComboBox(DefaultComboBoxModel(CirJsonSchemaVersion.entries.toTypedArray()))
        myErrorIcon = JBLabel(UIUtil.getBalloonWarningIcon())

        component = builder.panel
    }

    override fun dispose() {
        TODO("not implemented")
    }

    fun setError(text: String?, showWarning: Boolean) {
        TODO()
    }

    fun setItems(schemaFilePath: String, version: CirJsonSchemaVersion,
            data: List<UserDefinedCirJsonSchemaConfiguration.Item>?) {
        TODO()
    }

    internal class MyAddActionButtonRunnable

}