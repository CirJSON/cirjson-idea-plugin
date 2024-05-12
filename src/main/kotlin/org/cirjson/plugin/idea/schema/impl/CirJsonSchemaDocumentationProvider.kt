package org.cirjson.plugin.idea.schema.impl

import com.intellij.ide.impl.isTrusted
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference
import com.intellij.util.ObjectUtils
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker

class CirJsonSchemaDocumentationProvider {

    companion object {

        fun findSchemaAndGenerateDoc(element: PsiElement, originalElement: PsiElement?, preferShort: Boolean,
                forcedPropName: String?): String? {
            var realElement = element

            if (realElement is FakePsiElement) {
                return null
            }

            realElement =
                    if (isWhitespaceOrComment(originalElement)) realElement else ObjectUtils.coalesce(originalElement,
                            realElement)

            if (originalElement != null && hasFileOrPointerReferences(originalElement.references)) {
                return null
            }

            val containingFile = realElement.containingFile ?: return null
            val service = CirJsonSchemaService.get(realElement.project)
            val virtualFile = containingFile.viewProvider.virtualFile

            if (!service.isApplicableToFile(virtualFile)) {
                return null
            }

            val rootSchema = service.getSchemaObject(containingFile) ?: return null

            return generateDoc(realElement, rootSchema, preferShort, forcedPropName)
        }

        private fun isWhitespaceOrComment(originalElement: PsiElement?): Boolean {
            return originalElement is PsiWhiteSpace || originalElement is PsiComment
        }

        private fun hasFileOrPointerReferences(references: Array<PsiReference>): Boolean {
            for (reference in references) {
                if (reference is PsiFileReference
                        || reference is CirJsonPointerReferenceProvider.CirJsonSchemaIdReference
                        || reference is CirJsonPointerReferenceProvider.CirJsonPointerReference) {
                    return true
                }
            }

            return false
        }

        fun generateDoc(element: PsiElement, rootSchema: CirJsonSchemaObject, preferShort: Boolean,
                forcedPropName: String?): String? {
            val walker = CirJsonLikePsiWalker.getWalker(element, rootSchema) ?: return null

            val checkable = walker.findElementToCheck(element) ?: return null
            val position = walker.findPosition(checkable, true) ?: return null

            if (forcedPropName != null) {
                if (isWhitespaceOrComment(element)) {
                    position.addFollowingStep(forcedPropName)
                } else {
                    if (position.empty) {
                        return null
                    }

                    if (position.isArray(position.size - 1)) {
                        return null
                    }

                    position.replaceStep(position.size - 1, forcedPropName)
                }
            }

            val schemas = CirJsonSchemaResolver(element.project, rootSchema, position).resolve()

            var htmlDescription: String? = null
            var deprecated = false
            val possibleTypes = arrayListOf<CirJsonSchemaType>()

            for (schema in schemas) {
                if (htmlDescription == null) {
                    htmlDescription = getBestDocumentation(preferShort, schema)
                    val message = schema.deprecationMessage

                    if (message != null) {
                        htmlDescription = if (htmlDescription == null) {
                            message
                        } else {
                            "$message<br/>$htmlDescription"
                        }
                        deprecated = true
                    }
                }

                if (schema.type != null && schema.type != CirJsonSchemaType._any) {
                    possibleTypes.add(schema.type!!)
                } else if (schema.typeVariants != null) {
                    possibleTypes.addAll(schema.typeVariants!!)
                } else {
                    val guessedType = schema.guessType()

                    if (guessedType != null) {
                        possibleTypes.add(guessedType)
                    }
                }
            }

            return appendNameTypeAndApi(position, getThirdPartyApiInfo(element, rootSchema), possibleTypes,
                    htmlDescription, deprecated, preferShort)
        }

        private fun appendNameTypeAndApi(position: CirJsonPointerPosition, apiInfo: String,
                possibleTypes: List<CirJsonSchemaType>, htmlDescription: String?, deprecated: Boolean,
                preferShort: Boolean): String? {
            var realHtmlDescription = htmlDescription

            if (position.empty) {
                return realHtmlDescription
            }

            val name = position.lastName ?: return realHtmlDescription

            var type = ""
            val schemaType = CirJsonSchemaObject.getTypesDescription(false, possibleTypes)

            if (schemaType != null) {
                type = ": $schemaType"
            }

            val deprecationComment = if (deprecated) {
                CirJsonBundle.message("schema.documentation.deprecated.postfix")
            } else {
                ""
            }

            realHtmlDescription = if (preferShort) {
                val desc = realHtmlDescription?.let { "<br/>$realHtmlDescription" } ?: ""
                "<b>$name</b>$type$apiInfo$deprecationComment$desc"
            } else {
                val desc =
                        realHtmlDescription?.let { "${DocumentationMarkup.CONTENT_START}$realHtmlDescription${DocumentationMarkup.CONTENT_END}" }
                                ?: ""
                "${DocumentationMarkup.DEFINITION_START}$name$type$apiInfo$deprecationComment${DocumentationMarkup.DEFINITION_END}$desc"
            }

            return realHtmlDescription
        }

        private fun getThirdPartyApiInfo(element: PsiElement, rootSchema: CirJsonSchemaObject): String {
            val service = CirJsonSchemaService.get(element.project)
            var apiInfo = ""
            val provider = service.getSchemaProvider(rootSchema)

            if (provider != null) {
                val information = provider.thirdPartyApiInformation

                if (information != null) {
                    apiInfo = "&nbsp;&nbsp;<i>($information)</i>"
                }
            }

            return apiInfo
        }

        fun getBestDocumentation(preferShort: Boolean, schema: CirJsonSchemaObject): String? {
            var htmlDescription = schema.htmlDescription

            if (htmlDescription != null && hasNonTrustedProjects()) {
                htmlDescription = StringUtil.escapeXmlEntities(htmlDescription)
            }

            val description = schema.description
            val title = schema.title

            return if (preferShort && !StringUtil.isEmptyOrSpaces(title)) {
                return plainTextPostProcess(title!!)
            } else if (!StringUtil.isEmptyOrSpaces(htmlDescription)) {
                var desc = htmlDescription!!

                if (!StringUtil.isEmptyOrSpaces(title)) {
                    desc = "${plainTextPostProcess(title!!)}<br/>$desc"
                }

                desc
            } else if (!StringUtil.isEmptyOrSpaces(description)) {
                var desc = plainTextPostProcess(description!!)

                if (!StringUtil.isEmptyOrSpaces(title)) {
                    desc = "${plainTextPostProcess(title!!)}<br/>$desc"
                }

                desc
            } else {
                null
            }
        }

        private fun hasNonTrustedProjects(): Boolean {
            for (project in ProjectManager.getInstance().openProjects) {
                if (!project.isTrusted()) {
                    return true
                }
            }

            return false
        }

        private fun plainTextPostProcess(text: String): String {
            return StringUtil.escapeXmlEntities(text).replace("\\n", "<br/>")
        }

    }

}