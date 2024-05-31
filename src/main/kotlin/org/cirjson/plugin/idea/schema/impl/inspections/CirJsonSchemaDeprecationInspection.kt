package org.cirjson.plugin.idea.schema.impl.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonElementVisitor
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonValue
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaResolver

class CirJsonSchemaDeprecationInspection : CirJsonSchemaBasedInspectionBase() {

    override fun doBuildVisitor(root: CirJsonValue, schema: CirJsonSchemaObject?, service: CirJsonSchemaService,
            holder: ProblemsHolder, session: LocalInspectionToolSession): PsiElementVisitor {
        schema ?: return PsiElementVisitor.EMPTY_VISITOR
        val walker = CirJsonLikePsiWalker.getWalker(root, schema) ?: return PsiElementVisitor.EMPTY_VISITOR
        val project = root.project

        return object : CirJsonElementVisitor() {

            override fun visitProperty(o: CirJsonProperty) {
                annotate(o)
                super.visitProperty(o)
            }

            private fun annotate(o: CirJsonProperty) {
                val position = walker.findPosition(o, true) ?: return

                val result = CirJsonSchemaResolver(project, schema, position).detailedResolve()
                val iterable = if (result.myExcludingSchemas.size == 1) {
                    ContainerUtil.concat(result.mySchemas, result.myExcludingSchemas[0])
                } else {
                    result.mySchemas
                }

                for (obj in iterable) {
                    val message = obj.deprecationMessage ?: continue
                    holder.registerProblem(o.nameElement,
                            CirJsonBundle.message("cirjson.intention.property.0.is.deprecated.1", o.name, message))
                }
            }

        }
    }

}