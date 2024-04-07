package org.cirjson.plugin.idea.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.impl.CirJsonRecursiveElementVisitor

class CirJsonSortPropertiesIntention : BaseElementAtCaretIntentionAction(), LowPriorityAction, LightEditCompatible,
        DumbAware {

    override fun getText(): String {
        return CirJsonBundle.message("cirjson.intention.sort.properties")
    }

    override fun getFamilyName(): String {
        return CirJsonBundle.message("cirjson.intention.sort.properties")
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return Session(editor, element).hasUnsortedObjects
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) {
            CommonRefactoringUtil.showErrorHint(project, editor,
                    CirJsonBundle.message("cirjson.intention.sort.properties.file.is.readonly"),
                    CirJsonBundle.message("cirjson.intention.sort.properties.cannot.sort.properties"), null)
            return
        }

        val session = Session(editor, element)

        if (session.rootObject != null) {
            session.sort()
            reformat(project, editor, session.rootObject)
        }
    }

    private fun reformat(project: Project, editor: Editor, obj: CirJsonObject) {
        val pointer = SmartPointerManager.createPointer(obj)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        val element = pointer.element ?: return
        val codeStyleManager = CodeStyleManager.getInstance(project)
        codeStyleManager.reformatText(element.containingFile, setOf(element.textRange))
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

    private class Session(editor: Editor, private val contextElement: PsiElement) {

        private val selectionModel = editor.selectionModel

        val rootObject: CirJsonObject? = run outerRun@{
            val initObject = PsiTreeUtil.getParentOfType(contextElement, CirJsonObject::class.java) ?: run {
                val cirJsonFile = contextElement.containingFile as? CirJsonFile ?: return@run null
                return@outerRun cirJsonFile.allTopLevelValue.filterIsInstance<CirJsonObject>().firstOrNull()
            } ?: return@outerRun null

            if (!selectionModel.hasSelection()) {
                return@outerRun initObject
            }

            var obj = initObject

            while (obj.textRange?.containsRange(selectionModel.selectionStart, selectionModel.selectionEnd) == false) {
                obj = PsiTreeUtil.getParentOfType(obj, CirJsonObject::class.java) ?: break
            }

            return@outerRun obj
        }

        private val objects = rootObject?.let {
            val result = LinkedHashSet<CirJsonObject>()

            if (selectionModel.hasSelection()) {
                object : CirJsonRecursiveElementVisitor() {

                    override fun visitObject(o: CirJsonObject) {
                        super.visitObject(o)

                        if (o.textRange?.containsRange(selectionModel.selectionStart,
                                        selectionModel.selectionEnd) == true) {
                            result.add(o)
                        }
                    }

                }.visitObject(it)
            }

            result.add(it)
            result
        } ?: emptySet()

        val hasUnsortedObjects: Boolean
            get() {
                return objects.any { !isSorted(it) }
            }

        private fun isSorted(obj: CirJsonObject): Boolean {
            return obj.propertyList.asSequence().map { it.name }.zipWithNext().all { it.first <= it.second }
        }

        fun sort() {
            objects.forEach {
                if (!isSorted(it)) {
                    cycleSortProperties(it)
                }
            }
        }

        private fun cycleSortProperties(obj: CirJsonObject) {
            val properties: MutableList<CirJsonProperty> = obj.propertyList
            val size = properties.size

            for (cycleStart in 0 until size) {
                val item = properties[cycleStart]
                var pos = advance(properties, size, cycleStart, item)

                if (pos == -1) {
                    continue
                }

                if (pos != cycleStart) {
                    exchange(properties, pos, cycleStart)
                }

                while (pos != cycleStart) {
                    pos = advance(properties, size, cycleStart, properties[cycleStart])

                    if (pos == -1) {
                        break
                    }

                    if (pos != cycleStart) {
                        exchange(properties, pos, cycleStart)
                    }
                }
            }
        }

        private fun advance(properties: List<CirJsonProperty>, size: Int, cycleStart: Int, item: CirJsonProperty): Int {
            var pos = cycleStart
            val itemName = item.name

            for (i in cycleStart + 1 until size) {
                if (properties[i].name < itemName) {
                    pos++
                }
            }

            if (pos == cycleStart) {
                return -1
            }

            while (itemName != properties[pos].name) {
                pos++
            }

            return pos
        }

        private fun exchange(properties: MutableList<CirJsonProperty>, pos: Int, item: Int) {
            val propertyAtPos = properties[pos]
            val itemProperty = properties[item]
            properties[pos] = propertyAtPos.parent.addBefore(itemProperty, propertyAtPos) as CirJsonProperty
            properties[item] = itemProperty.parent.addBefore(propertyAtPos, itemProperty) as CirJsonProperty
            propertyAtPos.delete()
            itemProperty.delete()
        }

    }

}
