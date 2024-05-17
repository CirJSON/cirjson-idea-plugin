package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.FilterPattern
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.CirJsonSchemaService

class CirJsonSchemaReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(REF_PATTERN, CirJsonPointerReferenceProvider(false))
        registrar.registerReferenceProvider(REC_REF_PATTERN, CirJsonPointerReferenceProvider(false))
        registrar.registerReferenceProvider(SCHEMA_PATTERN, CirJsonPointerReferenceProvider(true))
        registrar.registerReferenceProvider(REQUIRED_PROP_PATTERN, CirJsonRequiredPropsReferenceProvider())
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val REF_PATTERN = createPropertyValuePattern("\$ref", schemaOnly = true, rootOnly = false)

        private val REC_REF_PATTERN = createPropertyValuePattern("\$recursiveRef", schemaOnly = true, rootOnly = false)

        private val SCHEMA_PATTERN = createPropertyValuePattern("\$schema", schemaOnly = false, rootOnly = true)

        private val REQUIRED_PROP_PATTERN = createRequiredPropPattern()

        private fun createPropertyValuePattern(propertyName: String, schemaOnly: Boolean,
                rootOnly: Boolean): PsiElementPattern.Capture<CirJsonValue> {
            return PlatformPatterns.psiElement(CirJsonValue::class.java).and(FilterPattern(object : ElementFilter {

                override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
                    if (element !is CirJsonValue) {
                        return false
                    }

                    val property = element.parent as? CirJsonProperty ?: return false

                    if (property.value !== element || propertyName != property.name) {
                        return false
                    }

                    val file = property.containingFile
                    return if (rootOnly && (file !is CirJsonFile || file.topLevelValue !== property.parent)) {
                        false
                    } else if (schemaOnly && !CirJsonSchemaService.isSchemaFile(
                                    CompletionUtil.getOriginalOrSelf(file))) {
                        false
                    } else {
                        true
                    }
                }

                override fun isClassAcceptable(hintClass: Class<*>?): Boolean {
                    return true
                }

            }))
        }

        private fun createRequiredPropPattern(): PsiElementPattern.Capture<CirJsonStringLiteral> {
            return PlatformPatterns.psiElement(CirJsonStringLiteral::class.java)
                    .and(FilterPattern(object : ElementFilter {

                        override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
                            if (element !is CirJsonStringLiteral) {
                                return false
                            }

                            val parent = element.parent as? CirJsonArray ?: return false
                            val property = parent.parent as? CirJsonProperty ?: return false
                            return "required" == property.name && CirJsonSchemaService.isSchemaFile(
                                    element.containingFile)
                        }

                        override fun isClassAcceptable(hintClass: Class<*>?): Boolean {
                            return true
                        }

                    }))
        }

    }

}