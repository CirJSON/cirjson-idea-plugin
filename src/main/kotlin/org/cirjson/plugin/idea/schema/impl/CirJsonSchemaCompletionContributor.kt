package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaFileProvider
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaNestedCompletionsTreeProvider
import org.cirjson.plugin.idea.schema.extension.SchemaType
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.impl.light.CirJsonSchemaObjectReadingUtils
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.SchemaPath
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.collectNestedCompletions
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.findChildBy
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
                knownNames: MutableSet<String>, completionPath: SchemaPath?) {
            if (isName != ThreeState.NO) {
                val completionOriginalPosition = myWalker!!.findChildBy(completionPath, myOriginalPosition)
                val completionPosition = myWalker.findChildBy(completionPath, myPosition)
                val insertComma = myWalker.hasMissingCommaAfter(myPosition)
                val hasValue = myWalker.isPropertyWithValue(checkable)

                val properties = myWalker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition)
                val adapter = myWalker.getParentPropertyAdapter(completionOriginalPosition)

                val forbiddenNames =
                        findPropertiesThatMustNotBePresent(schema, myPosition, myProject, properties) + properties
                addAllPropertyVariants(schema, insertComma, hasValue, forbiddenNames, adapter, knownNames,
                        completionPath)
                addIfThenElsePropertyNameVariants(schema, insertComma, hasValue, forbiddenNames, adapter, knownNames,
                        completionPath)
                addPropertyNameSchemaVariants(schema)
            }

            if (isName != ThreeState.YES) {
                suggestValues(schema, isName == ThreeState.NO, completionPath)
            }
        }

        private fun addAllPropertyVariants(schema: CirJsonSchemaObject, insertComma: Boolean, hasValue: Boolean,
                forbiddenNames: Set<String>, adapter: CirJsonPropertyAdapter?, knownNames: MutableSet<String>,
                completionPath: SchemaPath?) {
            schema.propertyNames.filter { it !in forbiddenNames && it !in knownNames || adapter?.name == it }.forEach {
                knownNames.add(it)
                val propertySchema = schema.getPropertyByName(it)!!
                addPropertyVariant(it, propertySchema, hasValue, insertComma, completionPath)
            }
        }

        private fun addPropertyVariant(key: String, cirJsonSchemaObject: CirJsonSchemaObject, hasValue: Boolean,
                insertComma: Boolean, completionPath: SchemaPath?) {
            var realCirJsonSchemaObject = cirJsonSchemaObject
            var realKey = key

            val variants = CirJsonSchemaResolver(myProject, realCirJsonSchemaObject).resolve()
            realCirJsonSchemaObject =
                    ObjectUtils.chooseNotNull(ContainerUtil.getFirstItem(variants), realCirJsonSchemaObject)
            realKey = if (!shouldWrapInQuotes(realKey, false)) realKey else StringUtil.wrapWithDoubleQuote(realKey)
            var builder = LookupElementBuilder.create(realKey)

            val typeText = CirJsonSchemaDocumentationProvider.getBestDocumentation(true, realCirJsonSchemaObject)

            if (!StringUtil.isEmptyOrSpaces(typeText)) {
                val text = StringUtil.removeHtmlTags(typeText!!)
                builder = builder.withTypeText(findFirstSentence(text), true)
            } else {
                val type = realCirJsonSchemaObject.getTypeDescription(true)

                if (type != null) {
                    builder = builder.withTypeText(type)
                }
            }

            TODO()
        }

        private fun shouldWrapInQuotes(key: String, isValue: Boolean): Boolean {
            TODO()
        }

        private fun findFirstSentence(sentence: String): String {
            TODO()
        }

        private fun addIfThenElsePropertyNameVariants(schema: CirJsonSchemaObject, insertComma: Boolean,
                hasValue: Boolean, forbiddenNames: Set<String>, adapter: CirJsonPropertyAdapter?,
                knownNames: Set<String>?, completionPath: SchemaPath?) {
            TODO()
        }

        private fun addPropertyNameSchemaVariants(schema: CirJsonSchemaObject) {
            TODO()
        }

        private fun suggestValues(schema: CirJsonSchemaObject, isSurelyValue: Boolean, completionPath: SchemaPath?) {
            suggestValuesForSchemaVariants(schema.anyOf, isSurelyValue, completionPath)
            suggestValuesForSchemaVariants(schema.oneOf, isSurelyValue, completionPath)
            suggestValuesForSchemaVariants(schema.allOf, isSurelyValue, completionPath)

            if (schema.enum != null && completionPath != null) {
                val metadata = schema.enumMetadata
                for (o in schema.enum!!) {
                    if (myInsideStringLiteral && o !is String) {
                        continue
                    }

                    val variant = o.toString()

                    if (variant !in FILTERED) {
                        val valueMetadata = metadata?.get(StringUtil.unquoteString(variant))
                        val description = valueMetadata?.get("description")
                        val deprecated = valueMetadata?.get("deprecationMessage")
                        val order: Int? = null
                        addValueVariant(variant, description, deprecated?.let { "$variant ($it)" }, null, order)
                    }
                }
            } else if (isSurelyValue) {
                val type = CirJsonSchemaObjectReadingUtils.guessType(schema)
                suggestSpecialValues(type)

                if (type != null) {
                    suggestByType(schema, type)
                } else if (schema.typeVariants != null) {
                    for (schemaType in schema.typeVariants!!) {
                        suggestByType(schema, schemaType)
                    }
                }
            }
        }

        private fun suggestValuesForSchemaVariants(list: List<CirJsonSchemaObject>?, isSurelyValue: Boolean,
                completionPath: SchemaPath?) {
            if (!list.isNullOrEmpty()) {
                for (schemaObject in list) {
                    suggestValues(schemaObject, isSurelyValue, completionPath)
                }
            }
        }

        private fun addValueVariant(key: String, description: String?) {
            addValueVariant(key, description, null, null)
        }

        private fun addValueVariant(key: String, description: String?, altText: String?,
                handler: InsertHandler<LookupElement>?) {
            addValueVariant(key, description, altText, handler, null)
        }

        private fun addValueVariant(key: String, description: String?, altText: String?,
                handler: InsertHandler<LookupElement>?, order: Int?) {
            TODO()
        }

        private fun suggestSpecialValues(type: CirJsonSchemaType?) {
            TODO()
        }

        private fun suggestByType(schema: CirJsonSchemaObject, type: CirJsonSchemaType) {
            TODO()
        }

        companion object {

            private val FILTERED = setOf("[]", "{}", "[ ]", "{ }")

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