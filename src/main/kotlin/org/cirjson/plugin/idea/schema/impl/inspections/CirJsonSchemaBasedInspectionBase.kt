package org.cirjson.plugin.idea.schema.impl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.cirjson.plugin.idea.psi.CirJsonValue
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.impl.CirJsonOriginalPsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

abstract class CirJsonSchemaBasedInspectionBase : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean,
            session: LocalInspectionToolSession): PsiElementVisitor {
        val file = holder.file
        val allRoots = CirJsonOriginalPsiWalker.INSTANCE.getRoots(file)

        val root = if (allRoots.size == 1) {
            allRoots.first() as? CirJsonValue
        } else {
            null
        } ?: return PsiElementVisitor.EMPTY_VISITOR

        val service = CirJsonSchemaService.get(file.project)
        val virtualFile = file.viewProvider.virtualFile

        if (!service.isApplicableToFile(virtualFile)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return doBuildVisitor(root, service.getSchemaObject(file), service, holder, session)
    }

    protected abstract fun doBuildVisitor(root: CirJsonValue, schema: CirJsonSchemaObject?,
            service: CirJsonSchemaService, holder: ProblemsHolder,
            session: LocalInspectionToolSession): PsiElementVisitor

}