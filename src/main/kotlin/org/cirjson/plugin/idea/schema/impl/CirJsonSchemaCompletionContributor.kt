package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.CaretSpecificDataContext
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaFileProvider
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaNestedCompletionsTreeProvider
import org.cirjson.plugin.idea.schema.extension.SchemaType
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.impl.light.CirJsonSchemaObjectReadingUtils
import org.cirjson.plugin.idea.schema.impl.nestedCompletions.*
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import javax.swing.Icon

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

            val type = cirJsonSchemaObject.guessType()
            builder = builder.withIcon(getIcon(type))

            if (hasSameType(variants)) {
                val values = cirJsonSchemaObject.enum
                val defaultValue = cirJsonSchemaObject.default

                val hasValues = !ContainerUtil.isEmpty(values)

                if (type != null || hasValues || defaultValue != null) {
                    val handler = if (!hasValues || values!!.stream().map { it::class.java }.distinct().count() == 1L) {
                        createPropertyInsertHandler(cirJsonSchemaObject, hasValue, insertComma)
                    } else {
                        createDefaultPropertyInsertHandler(true, insertComma)
                    }
                    builder = builder.withInsertHandler(handler)
                } else {
                    builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(true, insertComma))
                }
            } else {
                builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(true, insertComma))
            }

            val deprecationMessage = cirJsonSchemaObject.deprecationMessage

            if (deprecationMessage != null) {
                builder = builder.withTailText(CirJsonBundle.message("schema.documentation.deprecated.postfix"), true)
                        .withStrikeoutness(true)
            }

            myVariants.add(builder.prefixedBy(completionPath, myWalker!!))
        }

        private fun shouldWrapInQuotes(key: String, isValue: Boolean): Boolean {
            return myWrapInQuotes && myWalker != null && (isValue && myWalker.isRequiringValueQuote
                    || !isValue && myWalker.isRequiringNameQuote || !myWalker.isValidIdentifier(key, myProject))
        }

        private fun createPropertyInsertHandler(cirJsonSchemaObject: CirJsonSchemaObject, hasValue: Boolean,
                insertComma: Boolean): InsertHandler<LookupElement> {
            var type = cirJsonSchemaObject.guessType()
            val values = cirJsonSchemaObject.enum

            if (type == null && !values.isNullOrEmpty()) {
                type = detectType(values)
            }

            val defaultValue = cirJsonSchemaObject.default
            val defaultValueAsString = if (defaultValue == null || defaultValue is CirJsonSchemaObject) {
                null
            } else if (defaultValue is String) {
                "\"$defaultValue\""
            } else {
                defaultValue.toString()
            }

            val finalType = type
            return createPropertyInsertHandler(hasValue, insertComma, finalType, defaultValueAsString, values, myWalker,
                    myInsideStringLiteral)
        }

        private fun createDefaultPropertyInsertHandler(hasValue: Boolean,
                insertComma: Boolean): InsertHandler<LookupElement> {
            return object : InsertHandler<LookupElement> {

                override fun handleInsert(context: InsertionContext, item: LookupElement) {
                    ThreadingAssertions.assertWriteAccess()
                    val editor = context.editor
                    val project = context.project

                    if (handleInsideQuotesInsertion(context, editor, hasValue, myInsideStringLiteral)) {
                        return
                    }

                    var offset = editor.caretModel.offset
                    val initialOffset = offset
                    val docChars = context.document.charsSequence

                    while (offset < docChars.length && docChars[offset].isWhitespace()) {
                        offset++
                    }

                    val propertyValueSeparator = myWalker!!.getPropertyValueSeparator(null)

                    if (hasValue) {
                        if (offset < docChars.length && !isSeparatorAtOffset(docChars, offset,
                                        propertyValueSeparator)) {
                            editor.document.insertString(initialOffset, propertyValueSeparator)
                            handleWhitespaceAfterColon(editor, docChars, offset + propertyValueSeparator.length)
                        }

                        return
                    }

                    if (offset < docChars.length && isSeparatorAtOffset(docChars, offset, propertyValueSeparator)) {
                        handleWhitespaceAfterColon(editor, docChars, offset + propertyValueSeparator.length)
                    } else {
                        val stringToInsert = "$propertyValueSeparator 1${if (insertComma) "," else ""}"
                        EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                propertyValueSeparator.length + 1)
                        formatInsertedString(context, stringToInsert.length)
                        offset = editor.caretModel.offset
                        context.document.deleteString(offset, offset + 1)
                    }

                    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
                }

                private fun handleWhitespaceAfterColon(editor: Editor, docChars: CharSequence, nextOffset: Int) {
                    if (nextOffset < docChars.length && docChars[nextOffset] == ' ') {
                        editor.caretModel.moveToOffset(nextOffset + 1)
                    } else {
                        editor.caretModel.moveToOffset(nextOffset)
                        EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1)
                    }
                }

            }
        }

        private fun isSeparatorAtOffset(docChars: CharSequence, offset: Int, propertyValueSeparator: String): Boolean {
            return docChars.subSequence(offset, docChars.length).startsWith(propertyValueSeparator)
        }

        private fun addIfThenElsePropertyNameVariants(schema: CirJsonSchemaObject, insertComma: Boolean,
                hasValue: Boolean, forbiddenNames: Set<String>, adapter: CirJsonPropertyAdapter?,
                knownNames: MutableSet<String>, completionPath: SchemaPath?) {
            val ifThenElseList = schema.ifThenElse ?: return

            val walker = CirJsonLikePsiWalker.getWalker(myPosition, schema)
            val propertyAdapter = walker?.getParentPropertyAdapter(myPosition) ?: return

            val obj = propertyAdapter.parentObject ?: return

            for (ifThenElse in ifThenElseList) {
                val effectiveBranch = ifThenElse.effectiveBranchOrNull(myProject, obj) ?: continue

                addAllPropertyVariants(effectiveBranch, insertComma, hasValue, forbiddenNames, adapter, knownNames,
                        completionPath)
            }
        }

        private fun addPropertyNameSchemaVariants(schema: CirJsonSchemaObject) {
            val propertyNamesSchema = schema.propertyNamesSchema ?: return
            val anEnum = propertyNamesSchema.enum ?: return

            for (o in anEnum) {
                if (o !is String) {
                    continue
                }

                var key = o
                key = if (!shouldWrapInQuotes(key, false)) key else StringUtil.wrapWithDoubleQuote(key)
                myVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(key)))
            }
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
            val unquoted = StringUtil.unquoteString(key)
            var builder = LookupElementBuilder.create(if (!shouldWrapInQuotes(unquoted, true)) unquoted else key)
            altText?.let { builder = builder.withPresentableText(it) }
            description?.let { builder = builder.withTypeText(it) }
            handler?.let { builder = builder.withInsertHandler(it) }

            if (order != null) {
                myVariants.add(PrioritizedLookupElement.withPriority(builder, -order.toDouble()))
            } else {
                myVariants.add(builder)
            }
        }

        private fun suggestSpecialValues(type: CirJsonSchemaType?) {
            if (!CirJsonSchemaVersion.isSchemaSchemaId(myRootSchema.id) || type != CirJsonSchemaType._string) {
                return
            }

            val propertyAdapter = myWalker!!.getParentPropertyAdapter(myOriginalPosition) ?: return
            val name = propertyAdapter.name ?: return

            when (name) {
                "required" -> addRequiredPropVariants()
                CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION -> addInjectedLanguageVariants()
                "language" -> {
                    val parent = propertyAdapter.parentObject ?: return
                    val adapter = myWalker.getParentPropertyAdapter(parent.delegate) ?: return
                    if (CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION == adapter.name) {
                        addInjectedLanguageVariants()
                    }
                }
            }
        }

        private fun addRequiredPropVariants() {
            val checkable = myWalker!!.findElementToCheck(myPosition)

            if (checkable !is CirJsonStringLiteral && checkable !is CirJsonReferenceExpression) {
                return
            }

            val propertiesObject = CirJsonRequiredPropsReferenceProvider.findPropertiesObject(checkable) ?: return
            val parent = checkable.parent
            val items = if (parent is CirJsonArray) {
                parent.valueList.mapNotNull { (it as? CirJsonStringLiteral)?.value }.toSet()
            } else {
                HashSet()
            }
            propertiesObject.propertyList.map { it.name }.filter { !items.contains(it) }
                    .forEach { addStringVariant(it) }
        }

        private fun addStringVariant(defaultValueString: String?) {
            defaultValueString ?: return
            var normalizedValue = defaultValueString
            val shouldQuote = myWalker!!.isRequiringValueQuote
            val isQuoted = StringUtil.isQuotedString(normalizedValue)

            if (shouldQuote && !isQuoted) {
                normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue)
            } else if (!shouldQuote && isQuoted) {
                normalizedValue = StringUtil.unquoteString(normalizedValue)
            }

            addValueVariant(normalizedValue, null)
        }

        private fun addInjectedLanguageVariants() {
            val checkable = myWalker!!.findElementToCheck(myPosition)

            if (checkable !is CirJsonStringLiteral && checkable !is CirJsonReferenceExpression) {
                return
            }

            JBIterable.from(Language.getRegisteredLanguages()).filter(LanguageUtil::isInjectableLanguage)
                    .map(Injectable::fromLanguage).forEach {
                        myVariants.add(
                                LookupElementBuilder.create(it.id).withIcon(it.icon)
                                        .withTailText("(${it.displayName})", true))
                    }
        }

        private fun suggestByType(schema: CirJsonSchemaObject, type: CirJsonSchemaType) {
            if (CirJsonSchemaType._string == type) {
                addPossibleStringValue(schema)
            }

            if (myInsideStringLiteral) {
                return
            }

            when (type) {
                CirJsonSchemaType._boolean -> addPossibleBooleanValue(type)

                CirJsonSchemaType._null -> addValueVariant("null", null)

                CirJsonSchemaType._array -> {
                    val value = myWalker!!.defaultArrayValue
                    addValueVariant(value, null, "[...]",
                            createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks,
                                    value.length))
                }

                CirJsonSchemaType._object -> {
                    val value = myWalker!!.defaultObjectValue
                    addValueVariant(value, null, "{...}",
                            createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks,
                                    value.length))
                }

                else -> {}
            }
        }

        private fun addPossibleStringValue(schema: CirJsonSchemaObject) {
            val defaultValue = schema.default
            val defaultValueString = defaultValue?.toString()
            addStringVariant(defaultValueString)
        }

        private fun addPossibleBooleanValue(type: CirJsonSchemaType) {
            if (type != CirJsonSchemaType._boolean) {
                return
            }

            addValueVariant("true", null)
            addValueVariant("false", null)
        }

        companion object {

            private val FILTERED = setOf("[]", "{}", "[ ]", "{ }")

            private fun findFirstSentence(sentence: String): String {
                var i = sentence.indexOf(". ")

                while (i >= 0) {
                    val egText = ", e.g."

                    if (!sentence.regionMatches(i - egText.length + 1, egText, 0, egText.length)) {
                        return sentence.substring(0, i + 1)
                    }

                    i = sentence.indexOf(". ", i + 1)
                }

                return sentence
            }

            private fun getIcon(type: CirJsonSchemaType?): Icon {
                return when (type) {
                    CirJsonSchemaType._object -> AllIcons.Json.Object
                    CirJsonSchemaType._array -> AllIcons.Json.Array
                    else -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
                }
            }

            private fun hasSameType(variants: Collection<CirJsonSchemaObject>): Boolean {
                return variants.map { Pair(it.guessType(), isUntypedEnum(it)) }.distinct().count() <= 1
            }

            private fun isUntypedEnum(it: CirJsonSchemaObject): Boolean {
                if (it.guessType() != null) {
                    return false
                }

                return !it.enum.isNullOrEmpty()
            }

            private fun detectType(values: List<Any>): CirJsonSchemaType? {
                var type: CirJsonSchemaType? = null
                for (value in values) {
                    var newType: CirJsonSchemaType? = null

                    if (value is Int) {
                        newType = CirJsonSchemaType._integer
                    }

                    if (type != null && type != newType) {
                        return type
                    }

                    type = newType
                }

                return type
            }

            private fun createArrayOrObjectLiteralInsertHandler(newLine: Boolean,
                    insertedTextSize: Int): InsertHandler<LookupElement> {
                return InsertHandler<LookupElement> { context, item ->
                    val editor = context.editor

                    if (!newLine) {
                        EditorModificationUtil.moveCaretRelatively(editor, -1)
                    } else {
                        EditorModificationUtil.moveCaretRelatively(editor, -insertedTextSize)
                        PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
                        invokeEnterHandler(editor)
                        EditorActionUtil.moveCaretToLineEnd(editor, false, false)
                    }

                    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(editor, null)
                }
            }

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

        private fun handleInsideQuotesInsertion(context: InsertionContext, editor: Editor, hasValue: Boolean,
                insideStringLiteral: Boolean): Boolean {
            if (insideStringLiteral) {
                val offset = editor.caretModel.offset
                val element = context.file.findElementAt(offset)
                val tailOffset = context.tailOffset
                val guessEndOffset = tailOffset + 1

                if (element is LeafPsiElement) {
                    if (handleIncompleteString(editor, element)) {
                        return false
                    }

                    val endOffset = element.textRange.endOffset

                    if (endOffset > tailOffset) {
                        context.document.deleteString(tailOffset, endOffset - 1)
                    }
                }

                if (hasValue) {
                    return true
                }

                editor.caretModel.moveToOffset(guessEndOffset)
            } else {
                editor.caretModel.moveToOffset(context.tailOffset)
            }

            return false
        }

        private fun handleIncompleteString(editor: Editor, element: LeafPsiElement): Boolean {
            if (element.elementType == TokenType.WHITE_SPACE) {
                val prevSibling = element.prevSibling

                if (prevSibling is CirJsonProperty) {
                    val nameElement = prevSibling.nameElement

                    if (!nameElement.text.endsWith("\"")) {
                        editor.caretModel.moveToOffset(nameElement.textRange.endOffset)
                        EditorModificationUtil.insertStringAtCaret(editor, "\"", false, true, 1)
                        return true
                    }
                }
            }

            return false
        }

        fun createPropertyInsertHandler(hasValue: Boolean, insertComma: Boolean, finalType: CirJsonSchemaType?,
                defaultValueAsString: String?, values: List<Any>?, walker: CirJsonLikePsiWalker?,
                insideStringLiteral: Boolean): InsertHandler<LookupElement> {
            return InsertHandler { context, _ ->
                ThreadingAssertions.assertWriteAccess()
                val editor = context.editor
                val project = context.project
                var stringToInsert: String? = null
                val comma = if (insertComma) "," else ""

                if (handleInsideQuotesInsertion(context, editor, hasValue, insideStringLiteral)) {
                    return@InsertHandler
                }

                val propertyValueSeparator = walker!!.getPropertyValueSeparator(finalType)

                val element = context.file.findElementAt(editor.caretModel.offset)
                val insertColon = propertyValueSeparator != element?.text

                if (!insertColon) {
                    editor.caretModel.moveToOffset(editor.caretModel.offset + propertyValueSeparator.length)
                }

                if (finalType == null) {
                    insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, walker,
                            insertColon)
                    return@InsertHandler
                }

                var hadEnter: Boolean

                when (finalType) {
                    CirJsonSchemaType._object -> {
                        EditorModificationUtil.insertStringAtCaret(editor,
                                if (insertColon) "$propertyValueSeparator " else " ", false, true,
                                if (insertColon) propertyValueSeparator.length + 1 else 1)
                        hadEnter = false
                        val invokeEnter = walker.hasWhitespaceDelimitedCodeBlocks

                        if (insertColon && invokeEnter) {
                            invokeEnterHandler(editor)
                            hadEnter = true
                        }

                        if (insertColon) {
                            stringToInsert = walker.defaultObjectValue + comma
                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                    if (hadEnter) 0 else 1)
                        }

                        if (hadEnter || !insertColon) {
                            EditorActionUtil.moveCaretToLineEnd(editor, false, false)
                        }

                        PsiDocumentManager.getInstance(project).commitDocument(editor.document)

                        if (!hadEnter && stringToInsert != null) {
                            formatInsertedString(context, stringToInsert.length)
                        }

                        if (stringToInsert != null && !invokeEnter) {
                            invokeEnterHandler(editor)
                        }
                    }

                    CirJsonSchemaType._array -> {
                        EditorModificationUtil.insertStringAtCaret(editor,
                                if (insertColon) "$propertyValueSeparator " else " ", false, true,
                                if (insertColon) propertyValueSeparator.length + 1 else 1)
                        hadEnter = false

                        if (insertColon && walker.hasWhitespaceDelimitedCodeBlocks) {
                            invokeEnterHandler(editor)
                            hadEnter = true
                        } else {
                            EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1)
                        }

                        if (insertColon) {
                            stringToInsert = walker.defaultArrayValue + comma
                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                    if (hadEnter) 0 else 1)
                        }

                        if (hadEnter) {
                            EditorActionUtil.moveCaretToLineEnd(editor, false, false)
                        }

                        PsiDocumentManager.getInstance(project).commitDocument(editor.document)

                        if (stringToInsert != null && walker.isRequiringReformatAfterArrayInsertion) {
                            formatInsertedString(context, stringToInsert.length)
                        }
                    }

                    CirJsonSchemaType._boolean -> {
                        val value = (true.toString() == defaultValueAsString).toString()
                        stringToInsert = "${if (insertColon) "$propertyValueSeparator " else " "}$value$comma"
                        val model = editor.selectionModel

                        EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                stringToInsert.length - comma.length)
                        formatInsertedString(context, stringToInsert.length)
                        val start = editor.selectionModel.selectionStart
                        model.setSelection(start - value.length, start)
                        AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
                    }

                    CirJsonSchemaType._string, CirJsonSchemaType._integer, CirJsonSchemaType._number -> insertPropertyWithEnum(
                            context, editor, defaultValueAsString, values, finalType, comma, walker, insertColon)

                    else -> {}
                }
            }
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

        private fun invokeEnterHandler(editor: Editor) {
            val handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
            val caret = editor.caretModel.currentCaret
            handler.execute(editor, caret,
                    CaretSpecificDataContext.create(DataManager.getInstance().getDataContext(editor.contentComponent),
                            caret))
        }

    }

}