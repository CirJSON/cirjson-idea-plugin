package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.*
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonMappingKind
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import org.cirjson.plugin.idea.schema.widget.CirJsonSchemaInfoPopupStep
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

@Suppress("DialogTitleCapitalization")
class CirJsonSchemaMappingsView(private val myProject: Project, private val myTreeUpdater: TreeUpdater?,
        private val mySchemaPathChangedCallback: BiConsumer<in String, in Boolean>) : Disposable {

    private val myTableView: TableView<UserDefinedCirJsonSchemaConfiguration.Item>

    val component: JComponent

    private val mySchemaField: TextFieldWithBrowseButton

    private val mySchemaVersionComboBox: ComboBox<CirJsonSchemaVersion>

    private val myError: JEditorPane

    private var myErrorText: String? = null

    private val myErrorIcon: JBLabel

    var isInitialized: Boolean = false
        private set

    val data: List<UserDefinedCirJsonSchemaConfiguration.Item>
        get() = myTableView.listTableModel.items.filter { it.mappingKind == CirJsonMappingKind.DIRECTORY || it.path.isNotEmpty() }

    val schemaVersion: CirJsonSchemaVersion
        get() = mySchemaVersionComboBox.selectedItem as CirJsonSchemaVersion

    val schemaSubPath: String
        get() {
            val schemaFieldText = mySchemaField.text
            return if (CirJsonFileResolver.isAbsoluteUrl(schemaFieldText)) {
                schemaFieldText
            } else {
                FileUtil.toSystemDependentName(CirJsonSchemaInfo.getRelativePath(myProject, schemaFieldText))
            }
        }

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
        DumbAwareAction.create {
            val pathToSchema = mySchemaField.text

            if (StringUtil.isEmptyOrSpaces(pathToSchema) || CirJsonFileResolver.isHttpPath(pathToSchema)) {
                return@create
            }

            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(pathToSchema))

            if (virtualFile == null) {
                val balloonBuilder = JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(CirJsonBundle.message("cirjson.schema.file.not.found"),
                                UIUtil.getBalloonErrorIcon(), MessageType.ERROR.popupBackground, null)
                val balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(3)).createBalloon()
                balloon.showInCenterOf(mySchemaField)
                return@create
            }

            PsiNavigationSupport.getInstance().createNavigatable(myProject, virtualFile, -1).navigate(true)
        }.registerCustomShortcutSet(CommonShortcuts.getEditSource(), mySchemaField)
    }

    private fun setupError() {
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(myErrorText!!, UIUtil.getBalloonWarningIcon(),
                MessageType.WARNING.popupBackground, null).apply {
            setDisposable(this@CirJsonSchemaMappingsView)
            setHideOnClickOutside(true)
            setCloseButtonEnabled(true)
            createBalloon().showInCenterOf(myError)
        }
    }

    override fun dispose() {}

    fun setError(text: String?, showWarning: Boolean) {
        myErrorText = text
        myError.isVisible = showWarning && text != null
        myErrorIcon.isVisible = showWarning && text != null
    }

    fun setItems(schemaFilePath: String, version: CirJsonSchemaVersion,
            data: List<UserDefinedCirJsonSchemaConfiguration.Item>) {
        isInitialized = true
        mySchemaField.text = schemaFilePath
        mySchemaVersionComboBox.selectedItem = version
        myTableView.setModelAndUpdateColumns(ListTableModel(createColumns(), ArrayList(data)))
    }

    private fun createColumns(): Array<ColumnInfo<UserDefinedCirJsonSchemaConfiguration.Item, String>> {
        return arrayOf(MappingItemColumnInfo())
    }

    internal inner class MyAddActionButtonRunnable : AnActionButtonRunnable {

        override fun run(button: AnActionButton) {
            val point = button.preferredPopupPoint
            val popup = object : BaseListPopupStep<CirJsonMappingKind>() {

                override fun getTextFor(value: CirJsonMappingKind): String {
                    return CirJsonBundle.message("cirjson.schema.add.mapping.kind.text",
                            StringUtil.capitalizeWords(value.description, true))
                }

                override fun getIconFor(value: CirJsonMappingKind): Icon {
                    return value.icon
                }

                override fun onChosen(selectedValue: CirJsonMappingKind, finalChoice: Boolean): PopupStep<*>? {
                    return if (finalChoice) {
                        doFinalStep { doRun(selectedValue) }
                    } else {
                        FINAL_CHOICE
                    }
                }

                fun doRun(mappingKind: CirJsonMappingKind) {
                    val currentItem = UserDefinedCirJsonSchemaConfiguration.Item("", mappingKind)
                    myTableView.apply {
                        listTableModel.addRow(currentItem)
                        editCellAt(listTableModel.rowCount - 1, 0)
                    }

                    myTreeUpdater!!.updateTree(false)
                }

            }

            JBPopupFactory.getInstance().createListPopup(popup).show(point)
        }

    }

    private inner class MyEditActionButtonRunnable : AnActionButtonRunnable {

        override fun run(t: AnActionButton?) {
            execute()
        }

        fun execute() {
            val selectedRow = myTableView.selectedRow

            if (selectedRow == -1) {
                return
            }

            myTableView.editCellAt(selectedRow, 0)
        }

    }

    private inner class MyRemoveActionButtonRunnable : AnActionButtonRunnable {

        override fun run(t: AnActionButton?) {
            val rows = myTableView.selectedRows

            if (rows.isNotEmpty()) {

                for ((cnt, row) in rows.withIndex()) {
                    myTableView.listTableModel.removeRow(row - cnt)
                }

                myTableView.listTableModel.fireTableDataChanged()
                myTreeUpdater!!.updateTree(true)
            }
        }

    }

    private inner class MappingItemColumnInfo : ColumnInfo<UserDefinedCirJsonSchemaConfiguration.Item, String>("") {

        override fun valueOf(item: UserDefinedCirJsonSchemaConfiguration.Item): String {
            return item.presentation
        }

        override fun getRenderer(item: UserDefinedCirJsonSchemaConfiguration.Item): TableCellRenderer {
            return object : DefaultTableCellRenderer() {

                override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean,
                        hasFocus: Boolean, row: Int, column: Int): Component {
                    val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                            column) as JLabel
                    label.icon = item.mappingKind.icon

                    val error = item.error ?: return label

                    return JPanel().apply {
                        layout = BorderLayout()
                        add(label, BorderLayout.CENTER)
                        background = label.background
                        toolTipText = error
                        val warning = JLabel(AllIcons.General.Warning)
                        add(warning, BorderLayout.LINE_END)
                    }
                }

            }
        }

        override fun getEditor(item: UserDefinedCirJsonSchemaConfiguration.Item): TableCellEditor {
            return CirJsonMappingsTableCellEditor(item, myProject, myTreeUpdater)
        }

        override fun isCellEditable(item: UserDefinedCirJsonSchemaConfiguration.Item): Boolean {
            return true
        }

    }

}