package org.cirjson.plugin.idea.codeinsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

abstract class CirJsonStandardComplianceProvider {

    open fun isCommentAllowed(comment: PsiComment): Boolean {
        return false
    }

    open fun isTrailingCommaAllowed(element: PsiElement): Boolean {
        return false
    }

    companion object {

        val EP_NAME = ExtensionPointName.create<CirJsonStandardComplianceProvider>(
                "org.cirjson.plugin.idea.cirJsonStandardComplianceProvider")

        fun shouldWarnAboutComment(comment: PsiComment): Boolean {
            return EP_NAME.findFirstSafe { it.isCommentAllowed(comment) } == null
        }

        fun shouldWarnAboutTrailingComma(comment: PsiElement): Boolean {
            return EP_NAME.findFirstSafe { it.isTrailingCommaAllowed(comment) } == null
        }

    }

}