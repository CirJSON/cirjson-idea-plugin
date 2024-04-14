package org.cirjson.plugin.idea.codeinsight

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonDialectUtil
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.psi.*

/**
 * Compliance checks include:
 *
 * * Usage of line and block commentaries,
 * * Usage of single quoted strings,
 * * Usage of identifiers (unquoted words),
 * * Not double-quoted string literal is used as property key,
 * * Multiple top-level values;
 */
open class CirJsonStandardComplianceInspection : LocalInspectionTool() {

    var myWarnAboutComments: Boolean = true

    var myWarnAboutNanInfinity: Boolean = true

    var myWarnAboutTrailingCommas: Boolean = true

    var myWarnAboutMultipleTopLevelValues: Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return HighlightDisplayLevel.ERROR
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CirJsonDialectUtil.isStandardCirJson(holder.file)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return StandardCirJsonValidatingElementVisitor(holder)
    }

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(
                OptPane.checkbox("myWarnAboutComments", CirJsonBundle.message("inspection.compliance.option.comments")),
                OptPane.checkbox("myWarnAboutMultipleTopLevelValues",
                        CirJsonBundle.message("inspection.compliance.option.multiple.top.level.values")),
                OptPane.checkbox("myWarnAboutTrailingCommas",
                        CirJsonBundle.message("inspection.compliance.option.trailing.comma")),
                OptPane.checkbox("myWarnAboutNanInfinity",
                        CirJsonBundle.message("inspection.compliance.option.nan.infinity"))
        )
    }

    private class AddDoubleQuotesFix : LocalQuickFix {

        override fun getFamilyName(): String {
            return CirJsonBundle.message("quickfix.add.double.quotes.desc")
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val rawText = element.text

            if (element !is CirJsonLiteral && element !is CirJsonReferenceExpression) {
                LOG.error("Quick fix was applied to unexpected element", rawText, element.parent.text)
                return
            }

            var content = CirJsonPsiUtil.stripQuotes(rawText)

            if (element is CirJsonStringLiteral && rawText.startsWith("'")) {
                content = escapeSingleQuotedStringContent(content)
            }

            val replacement = CirJsonElementGenerator(project).createValue<CirJsonValue>("\"$content\"")
            CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { element.replace(replacement) }
        }

    }

    protected open inner class StandardCirJsonValidatingElementVisitor(private val myHolder: ProblemsHolder) :
            CirJsonElementVisitor() {

        protected open fun allowComments(): Boolean {
            return false
        }

        protected open fun allowSingleQuotes(): Boolean {
            return false
        }

        protected open fun allowIdentifierPropertyNames(): Boolean {
            return false
        }

        protected open fun allowTrailingCommas(): Boolean {
            return false
        }

        protected open fun allowNanInfinity(): Boolean {
            return false
        }

        protected open fun isValidPropertyName(literal: PsiElement): Boolean {
            return literal is CirJsonLiteral
                    && CirJsonPsiUtil.getElementTextWithoutHostEscaping(literal).startsWith("\"")
        }

        override fun visitComment(comment: PsiComment) {
            if (!allowComments() && myWarnAboutComments) {
                if (CirJsonStandardComplianceProvider.shouldWarnAboutComment(comment)
                        && comment.containingFile.language is CirJsonLanguage) {
                    myHolder.registerProblem(comment, CirJsonBundle.message("inspection.compliance.msg.comments"))
                }
            }
        }

        override fun visitStringLiteral(stringLiteral: CirJsonStringLiteral) {
            if (!allowSingleQuotes() && CirJsonPsiUtil.getElementTextWithoutHostEscaping(stringLiteral)
                            .startsWith("'")) {
                myHolder.registerProblem(stringLiteral,
                        CirJsonBundle.message("inspection.compliance.msg.single.quoted.strings"), AddDoubleQuotesFix())
            }

            super.visitStringLiteral(stringLiteral)
        }

        override fun visitLiteral(literal: CirJsonLiteral) {
            if (CirJsonPsiUtil.isPropertyKey(literal) && !isValidPropertyName(literal)) {
                myHolder.registerProblem(literal,
                        CirJsonBundle.message("inspection.compliance.msg.illegal.property.key"), AddDoubleQuotesFix())
            }

            if (!allowNanInfinity() && literal is CirJsonNumberLiteral && myWarnAboutNanInfinity) {
                val text = CirJsonPsiUtil.getElementTextWithoutHostEscaping(literal)

                if (text == StandardCirJsonLiteralChecker.INF || text == StandardCirJsonLiteralChecker.MINUS_INF
                        || text == StandardCirJsonLiteralChecker.NAN) {
                    myHolder.registerProblem(literal,
                            CirJsonBundle.message("syntax.error.illegal.floating.point.literal"))
                }
            }

            super.visitLiteral(literal)
        }

        override fun visitReferenceExpression(reference: CirJsonReferenceExpression) {
            if (!allowIdentifierPropertyNames() || !CirJsonPsiUtil.isPropertyKey(reference)
                    || !isValidPropertyName(reference)) {
                if (reference.text != MISSING_VALUE || !InjectedLanguageManager.getInstance(myHolder.project)
                                .isInjectedFragment(myHolder.file)) {
                    myHolder.registerProblem(reference, CirJsonBundle.message("inspection.compliance.msg.bad.token"),
                            AddDoubleQuotesFix())
                }
            }

            super.visitReferenceExpression(reference)
        }

        override fun visitArray(array: CirJsonArray) {
            if (myWarnAboutTrailingCommas && !allowTrailingCommas()
                    && CirJsonStandardComplianceProvider.shouldWarnAboutTrailingComma(array)) {
                val trailingComma = findTrailingComma(array.lastChild, CirJsonElementTypes.R_BRACKET)

                if (trailingComma != null) {
                    myHolder.registerProblem(trailingComma,
                            CirJsonBundle.message("inspection.compliance.msg.trailing.comma"))
                }
            }

            val id = array.id

            if (id == null) {
                val arrayIdLiteral = array.idLiteral

                if (arrayIdLiteral == null) {
                    myHolder.registerProblem(array, CirJsonBundle.message("inspection.compliance.msg.array.without.id"))
                } else {
                    myHolder.registerProblem(arrayIdLiteral,
                            CirJsonBundle.message("inspection.compliance.msg.empty.id"))
                }

            }

            super.visitArray(array)
        }

        override fun visitObject(obj: CirJsonObject) {
            if (myWarnAboutTrailingCommas && !allowTrailingCommas()
                    && CirJsonStandardComplianceProvider.shouldWarnAboutTrailingComma(obj)) {
                val trailingComma = findTrailingComma(obj.lastChild, CirJsonElementTypes.R_CURLY)

                if (trailingComma != null) {
                    myHolder.registerProblem(trailingComma,
                            CirJsonBundle.message("inspection.compliance.msg.trailing.comma"))
                }
            }

            val properties = obj.propertyList

            for (property in properties) {
                val name = property.nameElement

                if (CirJsonPsiUtil.stripQuotes(name.text) == ID_KEY) {
                    myHolder.registerProblem(name,
                            CirJsonBundle.message("inspection.compliance.msg.id.key.as.property.key"))
                }
            }

            super.visitObject(obj)
        }

        override fun visitValue(value: CirJsonValue) {
            val cirJsonFile = value.containingFile

            if (cirJsonFile !is CirJsonFile) {
                return
            }

            if (myWarnAboutMultipleTopLevelValues && value.parent === cirJsonFile
                    && value !== cirJsonFile.topLevelValue) {
                myHolder.registerProblem(value,
                        CirJsonBundle.message("inspection.compliance.msg.multiple.top.level.values"))
            }
        }

        override fun visitObjectIdElement(element: CirJsonObjectIdElement) {
            if (element.id.isEmpty()) {
                myHolder.registerProblem(element.stringLiteral,
                        CirJsonBundle.message("inspection.compliance.msg.empty.id"))
            }

            super.visitObjectIdElement(element)
        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val LOG = Logger.getInstance(CirJsonStandardComplianceInspection::class.java)

        private const val MISSING_VALUE = "missingValue"

        const val ID_KEY = "__cirJsonId__"

        protected fun findTrailingComma(lastChild: PsiElement, ending: IElementType): PsiElement? {
            if (lastChild.node.elementType != ending) {
                return null
            }

            val beforeEnding = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastChild)

            if (beforeEnding != null && beforeEnding.node.elementType == CirJsonElementTypes.COMMA) {
                return beforeEnding
            }

            return null
        }

        protected fun escapeSingleQuotedStringContent(content: String): String {
            val result = StringBuilder()
            var nextCharEscaped = false

            for (c in content) {
                if (nextCharEscaped && c != '\'' || !nextCharEscaped && c == '"') {
                    result.append('\\')
                }

                if (c != '\\' || nextCharEscaped) {
                    result.append(c)
                    nextCharEscaped = false
                } else {
                    nextCharEscaped = true
                }
            }

            if (nextCharEscaped) {
                result.append('\\')
            }

            return result.toString()
        }

    }

}