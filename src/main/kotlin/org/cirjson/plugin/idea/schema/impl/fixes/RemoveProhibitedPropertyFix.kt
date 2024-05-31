package org.cirjson.plugin.idea.schema.impl.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonLikeSyntaxAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonValidationError

class RemoveProhibitedPropertyFix(
        @SafeFieldForPreview private val myData: CirJsonValidationError.ProhibitedPropertyIssueData,
        @SafeFieldForPreview private val myQuickFixAdapter: CirJsonLikeSyntaxAdapter) : LocalQuickFix {

    override fun getFamilyName(): String {
        return CirJsonBundle.message("intention.remove.prohibited.property")
    }

    override fun getName(): String {
        return "$familyName '${myData.propertyName}'"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        assert(myData.propertyName == myQuickFixAdapter.getPropertyName(element))
        val forward = PsiTreeUtil.skipWhitespacesForward(element)
        element.delete()
        myQuickFixAdapter.removeIfComma(forward)
    }

}