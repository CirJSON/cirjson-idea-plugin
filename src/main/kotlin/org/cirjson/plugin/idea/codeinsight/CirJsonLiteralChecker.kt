package org.cirjson.plugin.idea.codeinsight

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

interface CirJsonLiteralChecker {

    fun getErrorForNumericLiteral(literalText: String): @InspectionMessage String?

    fun getErrorForStringFragment(fragmentText: Pair<TextRange, String>,
            stringLiteral: CirJsonStringLiteral): Pair<TextRange, @InspectionMessage String>?

    fun isApplicable(element: PsiElement): Boolean

    companion object {

        val EP_NAME = ExtensionPointName.create<CirJsonLiteralChecker>("org.cirjson.plugin.idea.cirJsonLiteralChecker")

    }

}