package org.cirjson.plugin.idea.schema.impl.fixes

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.BatchQuickFix
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiUtilCore
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonElementGenerator
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.extension.CirJsonLikeSyntaxAdapter

class SuggestEnumValuesFix(@SafeFieldForPreview private val myQuickFixAdapter: CirJsonLikeSyntaxAdapter) :
        LocalQuickFix, BatchQuickFix {

    override fun getFamilyName(): String {
        return CirJsonBundle.message("intention.replace.with.allowed.value")
    }

    override fun getName(): String {
        return familyName
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val initialElement = descriptor.psiElement
        val element = myQuickFixAdapter.adjustValue(initialElement)
        val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(element.containingFile.virtualFile)
        var whitespaceBefore = false
        var prevPrev: PsiElement? = null
        val prev = element.prevSibling

        if (prev is PsiWhiteSpace) {
            whitespaceBefore = true
            prevPrev = prev.prevSibling
        }

        var shouldAddWhitespace = myQuickFixAdapter.fixWhitespaceBefore(initialElement, element)
        val parent = element.parent
        val isCirJsonPropName = parent is CirJsonProperty && parent.nameElement === element

        if (isCirJsonPropName) {
            WriteAction.run<RuntimeException> {
                element.replace(CirJsonElementGenerator(project).createStringLiteral(""))
            }
        } else {
            WriteAction.run<RuntimeException> { element.delete() }
        }

        val editor = EditorUtil.getEditorEx(fileEditor)!!

        shouldAddWhitespace = shouldAddWhitespace || prevPrev != null && PsiUtilCore.getElementType(
                prevPrev.nextSibling) != TokenType.WHITE_SPACE

        if (shouldAddWhitespace && whitespaceBefore) {
            WriteAction.run<RuntimeException> {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, " ")
                editor.caretModel.moveToOffset(offset + 1)
            }
        }

        if (isCirJsonPropName) {
            editor.caretModel.moveToOffset((parent as CirJsonProperty).nameElement.textOffset + 1)
        }

        CodeCompletionHandlerBase.createHandler(CompletionType.BASIC).invokeCompletion(project, editor)
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun applyFix(project: Project, descriptors: Array<out CommonProblemDescriptor>,
            psiElementsToIgnore: List<PsiElement>, refreshViews: Runnable?) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor

        if (editor != null) {
            HintManager.getInstance().showErrorHint(editor,
                    CirJsonBundle.message("intention.sorry.this.fix.is.not.available.in.batch.mode"))
        } else {
            Messages.showErrorDialog(project,
                    CirJsonBundle.message("intention.sorry.this.fix.is.not.available.in.batch.mode"),
                    CirJsonBundle.message("intention.not.applicable.in.batch.mode"))
        }
    }

}