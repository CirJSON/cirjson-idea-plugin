package org.cirjson.plugin.idea.codeinsight

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.containers.MultiMap
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
import org.cirjson.plugin.idea.psi.*
import javax.swing.Icon

@Suppress("UnstableApiUsage", "DuplicatedCode")
class CirJsonDifferentDataTypesShareIDInspection : LocalInspectionTool() {

    private val myIds = MultiMap<String, Pair<PsiElement, DataType>>()

    override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
        myIds.clear()
        super.inspectionStarted(session, isOnTheFly)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return DifferentDataTypesShareIDValidatingElementVisitor()
    }

    override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
        for (entry in myIds.entrySet()) {
            val entryKey = entry.key
            val values = entry.value

            if (values.size < 2) {
                continue
            }

            if (!hasDifferentDataTypesForId(values)) {
                continue
            }

            val sharedIds = values.map { it.first }
            for (value in values) {
                val element = value.first
                holder.registerProblem(element, CirJsonBundle.message("inspection.id.different.type.msg", entryKey),
                        DifferentDataTypesShareIDFix(sharedIds, element, entryKey))
            }
        }

        super.inspectionFinished(session, holder)
    }

    private enum class DataType {

        OBJECT,

        ARRAY

    }

    private inner class DifferentDataTypesShareIDValidatingElementVisitor : CirJsonElementVisitor() {

        override fun visitObject(obj: CirJsonObject) {
            val idElement = obj.objectIdElement

            if (idElement != null) {
                myIds[idElement.id] = Pair.create(idElement.stringLiteral, DataType.OBJECT)
            }

            super.visitObject(obj)
        }

        override fun visitArray(array: CirJsonArray) {
            val children = array.children
            val idElement = children.firstOrNull()

            if (idElement != null && idElement is CirJsonStringLiteral && !array.id.isNullOrEmpty()) {
                myIds[array.id!!] = Pair.create(idElement, DataType.ARRAY)
            }

            super.visitArray(array)
        }

    }

    private class DifferentDataTypesShareIDFix(sharedIds: Collection<PsiElement>, element: PsiElement,
            private val myEntryKey: String) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

        private val mySharedIds = sharedIds.map { SmartPointerManager.createPointer(it) }

        override fun getText(): String {
            return CirJsonBundle.message("action.navigate.to.other.id.usages")
        }

        override fun getFamilyName(): String {
            return text
        }

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            return IntentionPreviewInfo.EMPTY
        }

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            return IntentionPreviewInfo.EMPTY
        }

        override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement,
                endElement: PsiElement) {
            if (editor == null) {
                return
            }

            if (mySharedIds.size == 2) {
                val iterator = mySharedIds.iterator()
                val next = iterator.next().element
                val toNavigate = if (next !== startElement) next else iterator.next().element

                if (toNavigate == null) {
                    return
                }

                CirJsonPsiUtil.navigateTo(editor, toNavigate)
            } else {
                val allElements = mySharedIds.map { it.element }.filter { it !== startElement }
                JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<PsiElement>(
                        CirJsonBundle.message("action.navigate.to.other.id.usage.header", myEntryKey), allElements) {

                    override fun getIconFor(value: PsiElement): Icon {
                        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
                    }

                    override fun getTextFor(value: PsiElement): String {
                        return CirJsonBundle.message("action.navigate.to.other.id.usage.desc", myEntryKey,
                                editor.document.getLineNumber(value.textOffset))
                    }

                    override fun getDefaultOptionIndex(): Int {
                        return 0
                    }

                    override fun onChosen(selectedValue: PsiElement, finalChoice: Boolean): PopupStep<*>? {
                        CirJsonPsiUtil.navigateTo(editor, selectedValue)
                        return PopupStep.FINAL_CHOICE
                    }

                    override fun isSpeedSearchEnabled(): Boolean {
                        return true
                    }

                }).showInBestPositionFor(editor)
            }
        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun hasDifferentDataTypesForId(values: Collection<Pair<PsiElement, DataType>>): Boolean {
            if (values.size <= 1) {
                return false
            }

            var firstType: DataType? = null

            for (value in values) {
                val type = value.second

                if (firstType == null) {
                    firstType = type
                    continue
                }

                if (firstType != type) {
                    return true
                }
            }

            return false
        }

    }

}