package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaFileProvider
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaNestedCompletionsTreeProvider
import org.cirjson.plugin.idea.schema.extension.SchemaType
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.SchemaPath
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.collectNestedCompletions
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.navigate
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer

class CirJsonSchemaCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = PsiUtilCore.getVirtualFile(position) ?: return

        val service = CirJsonSchemaService.get(position.project)

        if (!service.isApplicableToFile(file)) {
            return
        }

        val rootSchema = service.getSchemaObject(position.containingFile) ?: return
        val positionParent = position.parent ?: return
        val parent = positionParent.parent

        if (parent != null) {
            if (parent is CirJsonProperty) {
                val propName = parent.name

                if (propName == "\$schema" && parent.parent is CirJsonObject && parent.parent.parent is CirJsonFile
                        || propName == "\$ref" && service.isSchemaFile(file)) {
                    return
                }
            }
        }

        updateStat(service.getSchemaProvider(rootSchema), service.resolveSchemaFile(rootSchema))
        doCompletion(parameters, result, rootSchema, true)
    }

    private class Worker(private val myRootSchema: CirJsonSchemaObject, private val myPosition: PsiElement,
            private val myOriginalPosition: PsiElement, private val myResultConsumer: Consumer<LookupElement>) {

        private val myWrapInQuotes: Boolean

        private val myInsideStringLiteral: Boolean

        private val myWalker = CirJsonLikePsiWalker.getWalker(myPosition, myRootSchema)

        private val myProject = myOriginalPosition.project

        val myVariants = HashSet<LookupElement>()

        init {
            val positionParent = myPosition.parent
            myInsideStringLiteral = positionParent != null && myWalker != null
                    && myWalker.isQuotedString(positionParent)
            myWrapInQuotes = !myInsideStringLiteral
        }

        fun work() {
            if (myWalker == null) {
                return
            }

            val checkable = myWalker.findElementToCheck(myPosition) ?: return
            val isName = myWalker.isName(checkable)
            val position = myWalker.findPosition(checkable, isName == ThreeState.NO) ?: return

            if (position.empty && isName == ThreeState.NO) {
                return
            }

            val knownNames = HashSet<String>()

            val nestedCompletionsNode = CirJsonSchemaNestedCompletionsTreeProvider.getNestedCompletionsData(
                    myOriginalPosition.containingFile).navigate(position)

            CirJsonSchemaResolver(myProject, myRootSchema, position).resolve().forEach {
                it.collectNestedCompletions(myProject, nestedCompletionsNode,
                        null) { path: SchemaPath?, subSchema: CirJsonSchemaObject ->
                    processSchema(subSchema, isName, checkable, knownNames, path)
                }
            }

            for (variant in myVariants) {
                myResultConsumer.accept(variant)
            }
        }

        private fun processSchema(schema: CirJsonSchemaObject, isName: ThreeState, checkable: PsiElement,
                knownNames: Set<String>, path: SchemaPath?) {
            TODO()
        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private const val BUILTIN_USAGE_KEY: String = "builtin"

        private const val SCHEMA_USAGE_KEY: String = "schema"

        private const val USER_USAGE_KEY: String = "user"

        private const val REMOTE_USAGE_KEY: String = "remote"

        fun doCompletion(parameters: CompletionParameters, result: CompletionResultSet, rootSchema: CirJsonSchemaObject,
                stop: Boolean) {
            val completionPosition = parameters.originalPosition ?: parameters.position
            val worker = Worker(rootSchema, parameters.position, completionPosition, result)
            worker.work()

            if (stop && worker.myVariants.isNotEmpty()) {
                result.stopHere()
            }
        }

        @TestOnly
        fun getCompletionVariants(schema: CirJsonSchemaObject, position: PsiElement,
                originalPosition: PsiElement): List<LookupElement> {
            val result = ArrayList<LookupElement>()
            Worker(schema, position, originalPosition) { result.add(it) }.work()
            return result
        }

        private fun updateStat(provider: CirJsonSchemaFileProvider?, schemaFile: VirtualFile?) {
            if (provider == null) {
                if (schemaFile is HttpVirtualFile) {
                    CirJsonSchemaUsageTriggerCollector.trigger(REMOTE_USAGE_KEY)
                }

                return
            }

            val schemaType = provider.schemaType
            CirJsonSchemaUsageTriggerCollector.trigger(when (schemaType) {
                SchemaType.SCHEMA -> SCHEMA_USAGE_KEY
                SchemaType.USER_SCHEMA -> USER_USAGE_KEY
                SchemaType.EMBEDDED_SCHEMA -> BUILTIN_USAGE_KEY
                SchemaType.REMOTE_SCHEMA -> REMOTE_USAGE_KEY
            })
        }

        private fun insertPropertyWithEnum(context: InsertionContext, editor: Editor, defaultValue: String?,
                values: List<Any>?, type: CirJsonSchemaType?, comma: String, walker: CirJsonLikePsiWalker,
                insertColon: Boolean) {
            var realDefaultValue = defaultValue
            val propertyValueSeparator = walker.getPropertyValueSeparator(type)

            if (!walker.isRequiringValueQuote && realDefaultValue != null) {
                realDefaultValue = StringUtil.unquoteString(realDefaultValue)
            }

            val isNumber = type != null && (CirJsonSchemaType._integer == type || CirJsonSchemaType._number == type)
                    || type == null && (realDefaultValue != null && !StringUtil.isQuotedString(
                    realDefaultValue) || values != null && values.all { it !is String })
            val hasValues = !values.isNullOrEmpty()
            val hasDefaultValue = !realDefaultValue.isNullOrEmpty()
            val hasQuotes = isNumber || walker.isRequiringValueQuote
            val offset = editor.caretModel.offset
            val charSequence = editor.document.charsSequence
            val ws = if (charSequence.length > offset && charSequence[offset] == ' ') "" else " "
            val colonWs = if (insertColon) propertyValueSeparator + ws else ws
            val stringToInsert =
                    colonWs + (if (hasDefaultValue) realDefaultValue else if (hasQuotes) "" else "\"\"") + comma
            val caretShift = if (insertColon) propertyValueSeparator.length + 1 else 1
            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, caretShift)

            if (!hasQuotes || hasDefaultValue) {
                val model = editor.selectionModel
                val caretStart = model.selectionStart
                var newOffset = caretStart + if (hasDefaultValue) realDefaultValue!!.length else 1

                if (hasDefaultValue && !hasQuotes) {
                    newOffset--
                }

                model.setSelection(if (hasQuotes) caretStart else caretStart + 1, newOffset)
                editor.caretModel.moveToOffset(newOffset)
            }

            if (!walker.hasWhitespaceDelimitedCodeBlocks) {
                formatInsertedString(context, stringToInsert.length)
            }

            if (hasValues) {
                AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
            }
        }

        fun formatInsertedString(context: InsertionContext, offset: Int) {
            val project = context.project
            PsiDocumentManager.getInstance(project).commitDocument(context.document)
            val codeStyleManager = CodeStyleManager.getInstance(project)
            codeStyleManager.reformatText(context.file, context.startOffset, context.tailOffset + offset)
        }

    }

}