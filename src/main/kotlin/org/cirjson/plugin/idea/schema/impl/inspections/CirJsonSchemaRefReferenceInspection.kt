package org.cirjson.plugin.idea.schema.impl.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.impl.CirJsonPointerReferenceProvider
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

class CirJsonSchemaRefReferenceInspection : CirJsonSchemaBasedInspectionBase() {

    override fun doBuildVisitor(root: CirJsonValue, schema: CirJsonSchemaObject?, service: CirJsonSchemaService,
            holder: ProblemsHolder, session: LocalInspectionToolSession): PsiElementVisitor {
        val checkRefs = schema != null && service.isSchemaFile(schema)

        return object : CirJsonElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element === root) {
                    if (element is CirJsonObject) {
                        val schemaProp = element.findProperty("\$schema")

                        if (schemaProp != null) {
                            doCheck(schemaProp.value)
                        }
                    }
                }

                super.visitElement(element)
            }

            override fun visitProperty(o: CirJsonProperty) {
                if (!checkRefs) {
                    return
                }

                if ("\$ref" == o.name) {
                    doCheck(o.value)
                }

                super.visitProperty(o)
            }

            private fun doCheck(value: CirJsonValue?) {
                if (value !is CirJsonStringLiteral) {
                    return
                }

                for (reference in value.references) {
                    if (reference is WebReference) {
                        continue
                    }

                    val resolved = reference.resolve()

                    if (resolved == null) {
                        holder.registerProblem(reference, getReferenceErrorDesc(reference),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    }
                }
            }

        }
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun getReferenceErrorDesc(reference: PsiReference): String {
            val text = reference.canonicalText

            return if (reference is FileReference) {
                val hash = text.indexOf('#')
                val fileText = if (hash == -1) text else text.substring(0, hash)
                CirJsonBundle.message("cirjson.schema.ref.file.not.found", fileText)
            } else if (reference is CirJsonPointerReferenceProvider.CirJsonSchemaIdReference) {
                CirJsonBundle.message("cirjson.schema.ref.cannot.resolve.id", text)
            } else {
                val lastSlash = text.lastIndexOf('/')

                if (lastSlash == -1) {
                    CirJsonBundle.message("cirjson.schema.ref.cannot.resolve.path", text)
                } else {
                    val substring = text.substring(lastSlash + 1)

                    try {
                        substring.toInt()
                        CirJsonBundle.message("cirjson.schema.ref.no.array.element", substring)
                    } catch (e: NumberFormatException) {
                        CirJsonBundle.message("cirjson.schema.ref.no.property", substring)
                    }
                }
            }
        }

    }

}