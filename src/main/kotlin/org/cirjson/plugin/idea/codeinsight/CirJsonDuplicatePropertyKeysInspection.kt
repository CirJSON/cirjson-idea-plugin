package org.cirjson.plugin.idea.codeinsight

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.containers.MultiMap
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
import org.cirjson.plugin.idea.psi.CirJsonElementVisitor
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import javax.swing.Icon

@Suppress("UnstableApiUsage", "DuplicatedCode")
open class CirJsonDuplicatePropertyKeysInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val isSchemaFile = CirJsonSchemaService.isSchemaFile(holder.file)
        return object : CirJsonElementVisitor() {

            override fun visitObject(o: CirJsonObject) {
                val keys = MultiMap<String, PsiElement>()

                for (property in o.propertyList) {
                    keys[property.name] = property.nameElement
                }

                visitKeys(keys, isSchemaFile, holder)
            }

        }
    }

    private class NavigateToDuplicatesFix(sameNamedKeys: Collection<PsiElement>, element: PsiElement,
            private val myEntryKey: String) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

        private val mySameNamedKeys = sameNamedKeys.map { SmartPointerManager.createPointer(it) }

        override fun getText(): String {
            return CirJsonBundle.message("action.navigate.to.duplicates")
        }

        override fun getFamilyName(): String {
            return text
        }

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            return IntentionPreviewInfo.EMPTY
        }

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            return IntentionPreviewInfo.EMPTY
        }

        override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement,
                endElement: PsiElement) {
            if (editor == null) {
                return
            }

            if (mySameNamedKeys.size == 2) {
                val iterator = mySameNamedKeys.iterator()
                val next = iterator.next().element
                val toNavigate = if (next !== startElement) next else iterator.next().element

                if (toNavigate == null) {
                    return
                }

                CirJsonPsiUtil.navigateTo(editor, toNavigate)
            } else {
                val allElements = mySameNamedKeys.map { it.element }.filter { it !== startElement }
                JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<PsiElement>(
                        CirJsonBundle.message("action.navigate.to.duplicates.header", myEntryKey), allElements) {

                    override fun getIconFor(value: PsiElement): Icon {
                        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
                    }

                    override fun getTextFor(value: PsiElement): String {
                        return CirJsonBundle.message("action.navigate.to.duplicates.desc", myEntryKey,
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

        private const val COMMENT = "\$comment"

        protected fun visitKeys(keys: MultiMap<String, PsiElement>, isSchemaFile: Boolean, holder: ProblemsHolder) {
            for (entry in keys.entrySet()) {
                val sameNamedKeys = entry.value
                val entryKey = entry.key

                if (sameNamedKeys.size > 1 && (!isSchemaFile || !StringUtil.equalsIgnoreCase(COMMENT, entryKey))) {
                    for (element in sameNamedKeys) {
                        holder.registerProblem(element,
                                CirJsonBundle.message("inspection.duplicate.keys.msg.duplicate.keys", entryKey),
                                getNavigateToDuplicatesFix(sameNamedKeys, element, entryKey))
                    }
                }
            }
        }

        private fun getNavigateToDuplicatesFix(sameNamedKeys: Collection<PsiElement>, element: PsiElement,
                entryKey: String): NavigateToDuplicatesFix {
            return NavigateToDuplicatesFix(sameNamedKeys, element, entryKey)
        }

    }

}