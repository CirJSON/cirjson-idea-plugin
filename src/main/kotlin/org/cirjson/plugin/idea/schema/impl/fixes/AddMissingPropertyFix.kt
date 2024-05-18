package org.cirjson.plugin.idea.schema.impl.fixes

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.EmptyNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInspection.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonLikeSyntaxAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonValidationError

class AddMissingPropertyFix(private val myData: CirJsonValidationError.MissingMultiplePropsIssueData,
        private val myQuickFixAdapter: CirJsonLikeSyntaxAdapter) : LocalQuickFix, BatchQuickFix {

    override fun getFamilyName(): String {
        return CirJsonBundle.message("intention.add.not.required.properties.fix.add.missing.properties")
    }

    override fun getName(): String {
        return CirJsonBundle.message("intention.add.not.required.properties.fix.add.missing.0", myData.getMessage(true))
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val hadComma = Ref.create(false)
        val file = element.containingFile.virtualFile
        val newElement = performFix(element, hadComma) ?: return

        val value = myQuickFixAdapter.getPropertyValue(newElement)
        val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file)
        val editor = (fileEditor as TextEditor).editor

        if (value == null) {
            WriteAction.run<RuntimeException> { editor.caretModel.moveToOffset(newElement.textRange.endOffset) }
            return
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        val templateManager = TemplateManager.getInstance(project)
        val builder = TemplateBuilderImpl(newElement)
        val text = value.text
        val isEmptyArray = StringUtil.equalsIgnoreWhitespaces(text, "[]")
        val isEmptyObject = StringUtil.equalsIgnoreWhitespaces(text, "{}")
        val goInside = isEmptyArray || isEmptyObject || StringUtil.isQuotedString(text)
        val range = if (goInside) TextRange.create(1, text.length - 1) else TextRange.create(0, text.length)
        val expression = if (myData.myMissingPropertyIssues.first().enumItemsCount > 1 || isEmptyObject) {
            MacroCallNode(CompleteMacro())
        } else if (isEmptyArray) {
            EmptyNode()
        } else {
            val resultValue = if (goInside) StringUtil.unquoteString(text) else text
            ConstantNode(resultValue)
        }
        builder.replaceElement(value, range, expression)
        editor.caretModel.moveToOffset(newElement.textRange.startOffset)

        if (PsiTreeUtil.nextLeaf(newElement) != null) {
            builder.setEndVariableAfter(newElement)
        } else {
            builder.setEndVariableBefore(newElement.lastChild)
        }

        WriteAction.run<RuntimeException> {
            val template = builder.buildInlineTemplate()
            template.isToReformat = true
            templateManager.startTemplate(editor, template)
        }
    }

    override fun applyFix(project: Project, descriptors: Array<out CommonProblemDescriptor>,
            psiElementsToIgnore: MutableList<PsiElement>, refreshViews: Runnable?) {
        val propFixes = arrayListOf<Pair<AddMissingPropertyFix, PsiElement>>()

        for (descriptor in descriptors) {
            if (descriptor !is ProblemDescriptor) {
                continue
            }

            val fixes = descriptor.fixes ?: continue
            val fix = getWorkingQuickFix(fixes) ?: continue
            propFixes.add(fix to descriptor.psiElement)
        }

        DocumentUtil.writeInRunUndoTransparentAction {
            propFixes.forEach {
                it.first.performFix(it.second, Ref.create(false))
            }
        }
    }

    fun performFix(node: PsiElement?, hadComma: Ref<Boolean>): PsiElement? {
        node ?: return null
        val element = (node as? PsiFile)?.firstChild ?: node
        val newElementRef = Ref.create<PsiElement>(null)
        WriteAction.run<RuntimeException> { performFixInner(hadComma, element, newElementRef) }
        return newElementRef.get()
    }

    fun performFixInner(hadComma: Ref<Boolean>, element: PsiElement, newElementRef: Ref<PsiElement>) {
        val isSingle = myData.myMissingPropertyIssues.size == 1
        var processedElement = element
        val reverseOrder = ArrayList(myData.myMissingPropertyIssues).reversed()

        for (issue in reverseOrder) {
            val defaultValueObject = issue.defaultValue
            val defaultValue = formatDefaultValue(defaultValueObject)
            val propertyElement = defaultValue ?: myQuickFixAdapter.getDefaultValueFromType(issue.propertyType)
            val property = myQuickFixAdapter.createProperty(issue.propertyName, propertyElement, element)
            val newElement = if (processedElement is LeafPsiElement) {
                myQuickFixAdapter.adjustPropertyAnchor(processedElement).addBefore(property, null)
            } else if (processedElement === element) {
                processedElement.addBefore(property, processedElement.lastChild)
            } else {
                processedElement.parent.addBefore(property, processedElement)
            }
            val adjusted = myQuickFixAdapter.adjustNewProperty(newElement)
            hadComma.set(myQuickFixAdapter.ensureComma(adjusted,
                    PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement)!!))

            if (!hadComma.get()) {
                hadComma.set(processedElement === element && myQuickFixAdapter.ensureComma(
                        PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement)!!, adjusted))
            }

            processedElement = adjusted

            if (isSingle) {
                newElementRef.set(adjusted)
            }
        }
    }

    private fun formatDefaultValue(defaultValueObject: Any?): String? {
        return when (defaultValueObject) {
            is String -> StringUtil.wrapWithDoubleQuote(defaultValueObject.toString())
            is Boolean, is Number -> defaultValueObject.toString()
            is PsiElement -> defaultValueObject.text
            else -> null
        }
    }

    companion object {

        private fun getWorkingQuickFix(fixes: Array<QuickFix<*>>): AddMissingPropertyFix? {
            for (fix in fixes) {
                if (fix is AddMissingPropertyFix) {
                    return fix
                }
            }

            return null
        }

    }

}