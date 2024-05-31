package org.cirjson.plugin.idea.schema.impl.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonElementVisitor
import org.cirjson.plugin.idea.psi.CirJsonValue
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaComplianceChecker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

class CirJsonSchemaComplianceInspection : CirJsonSchemaBasedInspectionBase() {

    var myCaseInsensitiveEnum = false

    override fun doBuildVisitor(root: CirJsonValue, schema: CirJsonSchemaObject?, service: CirJsonSchemaService,
            holder: ProblemsHolder, session: LocalInspectionToolSession): PsiElementVisitor {
        schema ?: return PsiElementVisitor.EMPTY_VISITOR
        val options = CirJsonComplianceCheckerOptions(myCaseInsensitiveEnum)

        return object : CirJsonElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element === root) {
                    annotate(element, schema, holder, session, options)
                }

                super.visitElement(element)
            }

        }
    }

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(OptPane.checkbox("myCaseInsensitiveEnum",
                CirJsonBundle.message("cirjson.schema.inspection.case.insensitive.enum")))
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun annotate(element: PsiElement, rootSchema: CirJsonSchemaObject, holder: ProblemsHolder,
                session: LocalInspectionToolSession, options: CirJsonComplianceCheckerOptions) {
            val walker = CirJsonLikePsiWalker.getWalker(element, rootSchema) ?: return
            CirJsonSchemaComplianceChecker(rootSchema, holder, walker, session, options).annotate(element)
        }

    }

}