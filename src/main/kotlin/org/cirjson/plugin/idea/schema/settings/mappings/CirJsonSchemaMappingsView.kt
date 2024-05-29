package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import org.cirjson.plugin.idea.schema.widget.CirJsonSchemaInfoPopupStep
import java.awt.BorderLayout
import java.util.function.BiConsumer
import javax.swing.*
import javax.swing.event.DocumentEvent

@Suppress("DialogTitleCapitalization")
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
        val addAction = MyAddActionButtonRunnable()

        myTableView = CirJsonMappingsTableView(addAction).apply {
            setShowGrid(false)
            tableHeader.isVisible = true
        }
        val decorator = ToolbarDecorator.createDecorator(myTableView).apply {
            val editAction = MyEditActionButtonRunnable()
            val removeAction = MyRemoveActionButtonRunnable()
            setRemoveAction(removeAction)
            setRemoveActionName("settings.cirjson.schema.remove.mapping")
            setAddAction(addAction)
            setAddActionName("settings.cirjson.schema.add.mapping")
            setEditAction(editAction)
            setEditActionName("settings.cirjson.schema.edit.mapping")
            disableUpDownActions()
        }

        val schemaFieldBacking = JBTextField()
        mySchemaField = TextFieldWithBrowseButton(schemaFieldBacking).apply {
            setButtonIcon(AllIcons.General.OpenDiskHover)
        }
        val urlButton = FixedSizeButton().apply {
            icon = AllIcons.General.Web
            addActionListener {
                val service = CirJsonSchemaService.get(myProject)
                val schemas = service.allUserVisibleSchemas
                val popup = object : CirJsonSchemaInfoPopupStep(schemas, myProject, null, service,
                        CirJsonBundle.message("schema.configuration.mapping.remote")) {

                    override fun setMapping(selectedValue: CirJsonSchemaInfo?, virtualFile: VirtualFile?,
                            project: Project) {
                        selectedValue ?: return
                        mySchemaField.text = selectedValue.getUrl(myProject)
                        mySchemaPathChangedCallback.accept(selectedValue.description, true)
                    }

                }
                JBPopupFactory.getInstance().createListPopup(popup).showInCenterOf(this)
            }
        }
        SwingHelper.installFileCompletionAndBrowseDialog(myProject, mySchemaField,
                CirJsonBundle.message("cirjson.schema.add.schema.chooser.title"),
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
        mySchemaField.textField.document.addDocumentListener(object : DocumentAdapter() {

            override fun textChanged(e: DocumentEvent) {
                mySchemaPathChangedCallback.accept(mySchemaField.text, false)
            }

        })
        attachNavigateToSchema()

        myError = SwingHelper.createHtmlLabel(CirJsonBundle.message("cirjson.schema.conflicting.mappings"), null) {
            setupError()
        }

        val schemaSelector = JPanel(BorderLayout()).apply {
            add(mySchemaField, BorderLayout.CENTER)
            add(urlButton, BorderLayout.EAST)
        }

        val builder = FormBuilder.createFormBuilder()
        val label = JBLabel(CirJsonBundle.message("cirjson.schema.file.selector.title"))
        builder.addLabeledComponent(label, schemaSelector)
        label.apply {
            labelFor = schemaSelector
            border = JBUI.Borders.empty(0, 10)
        }
        schemaSelector.border = JBUI.Borders.emptyRight(10)
        val versionLabel = JBLabel(CirJsonBundle.message("cirjson.schema.version.selector.title"))
        mySchemaVersionComboBox = ComboBox(DefaultComboBoxModel(CirJsonSchemaVersion.entries.toTypedArray()))
        versionLabel.apply {
            labelFor = mySchemaVersionComboBox
            border = JBUI.Borders.empty(0, 10)
        }
        builder.addLabeledComponent(versionLabel, mySchemaVersionComboBox)
        myErrorIcon = JBLabel(UIUtil.getBalloonWarningIcon())
        val wrapper = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 10)
            add(myErrorIcon, BorderLayout.WEST)
            add(myError, BorderLayout.CENTER)
        }
        builder.addComponent(wrapper)
        val panel = decorator.createPanel()
        panel.border = BorderFactory.createCompoundBorder(JBUI.Borders.empty(0, 8), panel.border)
        builder.addComponentFillVertically(panel, 5)
        val commentComponent =
                ComponentPanelBuilder.createCommentComponent(CirJsonBundle.message("cirjson.schema.path.to.file"),
                        false).apply {
                    border = JBUI.Borders.empty(0, 8, 5, 0)
                }
        builder.addComponent(commentComponent)

        component = builder.panel
    }

    private fun attachNavigateToSchema() {
        TODO()
    }

    private fun setupError() {
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(myErrorText, UIUtil.getBalloonWarningIcon(),
                MessageType.WARNING.popupBackground, null).apply {
            setDisposable(this@CirJsonSchemaMappingsView)
            setHideOnClickOutside(true)
            setCloseButtonEnabled(true)
            createBalloon().showInCenterOf(myError)
        }
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

    internal inner class MyAddActionButtonRunnable : AnActionButtonRunnable {

        override fun run(t: AnActionButton?) {
            TODO("Not yet implemented")
        }

    }

    private inner class MyEditActionButtonRunnable : AnActionButtonRunnable {

        override fun run(t: AnActionButton?) {
            TODO("Not yet implemented")
        }

    }

    private inner class MyRemoveActionButtonRunnable : AnActionButtonRunnable {

        override fun run(t: AnActionButton?) {
            TODO("Not yet implemented")
        }

    }

}