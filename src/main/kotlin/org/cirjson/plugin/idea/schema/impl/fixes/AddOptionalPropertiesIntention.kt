package org.cirjson.plugin.idea.schema.impl.fixes

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonLikeSyntaxAdapter
import org.cirjson.plugin.idea.schema.impl.*

open class AddOptionalPropertiesIntention : IntentionAction {

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun getFamilyName(): String {
        return CirJsonBundle.message("intention.add.not.required.properties.family.name")
    }

    override fun getText(): String {
        return CirJsonBundle.message("intention.add.not.required.properties.text")
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val containingObject = findContainingObjectNode(editor, file) ?: return false
        return CirJsonCachedValues.hasComputedSchemaObjectForFile(containingObject.containingFile)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        runWithModalProgressBlocking(project, CirJsonBundle.message("intention.add.not.required.properties.text")) {
            val (objectPointer, missingProperties) = readAction {
                val containingObject =
                        findContainingObjectNode(editor, file)?.createSmartPointer() ?: return@readAction null
                val missingProperties = collectMissingPropertiesFromSchema(containingObject,
                        containingObject.project)?.missingKnownProperties ?: return@readAction null
                containingObject to missingProperties
            } ?: return@runWithModalProgressBlocking

            writeAction {
                executeCommand {
                    AddMissingPropertyFix(missingProperties, getSyntaxAdapter(project)).performFix(
                            objectPointer.dereference(), Ref.create())
                }
            }

            withContext(Dispatchers.EDT) {
                objectPointer.containingFile?.let { ReformatCodeProcessor(it, false).run() }
            }
        }
    }

    protected open fun findContainingObjectNode(editor: Editor, file: PsiFile): PsiElement? {
        val offset = editor.caretModel.offset
        return file.findElementAt(offset)?.parentOfType<CirJsonObject>(false)
    }

    protected open fun getSyntaxAdapter(project: Project): CirJsonLikeSyntaxAdapter {
        return CirJsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val containingObject = findContainingObjectNode(editor, file) ?: return IntentionPreviewInfo.EMPTY
        val missingProperties = collectMissingPropertiesFromSchema(containingObject.createSmartPointer(),
                containingObject.project)?.missingKnownProperties ?: return IntentionPreviewInfo.EMPTY
        AddMissingPropertyFix(missingProperties, getSyntaxAdapter(project)).performFixInner(Ref.create(),
                containingObject, Ref.create())
        ReformatCodeProcessor(containingObject.containingFile, false).run()
        return IntentionPreviewInfo.DIFF
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        @RequiresReadLock
        fun collectMissingPropertiesFromSchema(objectNodePointer: SmartPsiElementPointer<out PsiElement>,
                project: Project): CirJsonSchemaPropertiesInfo? {
            val objectNode = objectNodePointer.dereference() ?: return null
            val schemaObjectFile =
                    CirJsonSchemaService.get(project).getSchemaObject(objectNode.containingFile) ?: return null
            val psiWalker = CirJsonLikePsiWalker.getWalker(objectNode, schemaObjectFile) ?: return null
            val position = psiWalker.findPosition(objectNode, true) ?: return null
            val valueAdapter = psiWalker.createValueAdapter(objectNode) ?: return null
            val checker = CirJsonSchemaAnnotatorChecker(project,
                    CirJsonComplianceCheckerOptions(isCaseInsensitiveEnumCheck = false, isForceStrict = false,
                            isReportMissingOptionalProperties = true))
            checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position)
            val errorForNode = checker.errors[objectNode] ?: return null

            val missingRequiredProperties =
                    extractPropertiesOfKind(errorForNode, CirJsonValidationError.FixableIssueKind.MissingProperty)
            val missingKnownProperties = extractPropertiesOfKind(errorForNode,
                    CirJsonValidationError.FixableIssueKind.MissingOptionalProperty)
            return CirJsonSchemaPropertiesInfo(missingRequiredProperties, missingKnownProperties)
        }

        private fun extractPropertiesOfKind(foundError: CirJsonValidationError,
                kind: CirJsonValidationError.FixableIssueKind): CirJsonValidationError.MissingMultiplePropsIssueData {
            val issueData = foundError.takeIf { it.fixableIssueKind == kind }?.issueData

            val filteredProperties = when (issueData) {
                is CirJsonValidationError.MissingMultiplePropsIssueData -> filterOutUnwantedProperties(
                        issueData.myMissingPropertyIssues)

                is CirJsonValidationError.MissingPropertyIssueData -> filterOutUnwantedProperties(listOf(issueData))
                else -> emptyList()
            }

            return CirJsonValidationError.MissingMultiplePropsIssueData(filteredProperties)
        }

        private fun filterOutUnwantedProperties(
                missingProperties: Collection<CirJsonValidationError.MissingPropertyIssueData>): Collection<CirJsonValidationError.MissingPropertyIssueData> {
            return missingProperties.filter { !it.propertyName.startsWith("$") }
        }

    }

}