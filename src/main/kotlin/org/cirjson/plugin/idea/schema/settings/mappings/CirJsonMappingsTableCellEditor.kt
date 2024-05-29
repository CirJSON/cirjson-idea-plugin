package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileLookup
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl
import com.intellij.openapi.fileChooser.ex.LocalFsFinder
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.JBUI
import org.cirjson.plugin.idea.schema.CirJsonMappingKind
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*

@Suppress("removal")
class CirJsonMappingsTableCellEditor(private val myItem: UserDefinedCirJsonSchemaConfiguration.Item,
        private val myProject: Project, private val myTreeUpdater: TreeUpdater?) : AbstractTableCellEditor() {

    val myComponent = object : TextFieldWithBrowseButton() {

        override fun installPathCompletion(fileChooserDescriptor: FileChooserDescriptor?, parent: Disposable?) {}

    }

    private val myWrapper = JPanel()

    init {
        myWrapper.apply {
            border = JBUI.Borders.empty(-3, 0)
            layout = BorderLayout()
            val label = JLabel(myItem.mappingKind.prefix.trim(), myItem.mappingKind.icon, SwingConstants.LEFT).apply {
                border = JBUI.Borders.emptyLeft(1)
            }
            add(label, BorderLayout.LINE_START)
            add(myComponent, BorderLayout.CENTER)
        }

        val descriptor = createDescriptor(myItem)

        myComponent.apply {
            if (myItem.isPattern) {
                button.isVisible = false
            } else {
                addBrowseFolderListener(object : TextBrowseFolderListener(descriptor, myProject) {

                    override fun chosenFileToResultingText(chosenFile: VirtualFile): String {
                        val relativePath = VfsUtil.getRelativePath(chosenFile, myProject.baseDir)
                        return relativePath ?: chosenFile.path
                    }

                })
            }

            var field: FileTextFieldImpl? = null
            if (!myItem.isPattern && !ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
                val finder = LocalFsFinder().apply { setBaseDir(File(myProject.baseDir.path)) }
                field = MyFileTextField(finder, descriptor, textField, myProject, this)
            }

            textField.addKeyListener(object : KeyAdapter() {

                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && (field == null || field.isPopupDisplayed)) {
                        stopCellEditing()
                    }
                }

            })
        }
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int,
            column: Int): Component {
        myComponent.childComponent.text = myItem.path
        return myWrapper
    }

    override fun getCellEditorValue(): Any {
        return myComponent.childComponent.text
    }

    override fun stopCellEditing(): Boolean {
        myItem.path = myComponent.childComponent.text
        myTreeUpdater?.updateTree(true)
        return super.stopCellEditing()
    }

    private class MyFileTextField(finder: LocalFsFinder, descriptor: FileChooserDescriptor,
            private val myTextField: JTextField, private val myProject: Project, parent: Disposable) :
            FileTextFieldImpl(myTextField, finder, LocalFsFinder.FileChooserFilter(descriptor, true),
                    FileChooserFactoryImpl.getMacroMap(), parent) {

        init {
            myAutopopup = true
        }

        override fun setTextToFile(file: FileLookup.LookupFile) {
            val path = file.absolutePath
            val ioFile = VfsUtil.findFileByIoFile(File(path), false)

            if (ioFile == null) {
                myTextField.text = path
                return
            }

            val relativePath = VfsUtil.getRelativePath(ioFile, myProject.baseDir)
            myTextField.text = relativePath ?: path
        }

    }

    companion object {

        private fun createDescriptor(item: UserDefinedCirJsonSchemaConfiguration.Item): FileChooserDescriptor {
            return if (item.mappingKind == CirJsonMappingKind.FILE) {
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            } else {
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            }
        }

    }

}