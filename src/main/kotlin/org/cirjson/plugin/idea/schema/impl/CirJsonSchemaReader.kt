package org.cirjson.plugin.idea.schema.impl

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.AstLoadingFilter
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaReader.MyReader
import java.util.*
import java.util.function.BiConsumer
import java.util.stream.Collectors
import kotlin.collections.ArrayDeque
import kotlin.collections.set

class CirJsonSchemaReader(private val myFile: VirtualFile) {

    private val myIds = HashMap<String, CirJsonSchemaObject>()

    private val myQueue = ArrayDeque<Pair<CirJsonSchemaObject, CirJsonValueAdapter>>()

    fun read(file: PsiFile): CirJsonSchemaObject? {
        val walker = CirJsonLikePsiWalker.getWalker(file, CirJsonSchemaObject.NULL_OBJ) ?: return null
        val root = AstLoadingFilter.forceAllowTreeLoading<PsiElement?, Exception>(file) {
            ContainerUtil.getFirstItem(walker.getRoots(file))
        } ?: return null
        val rootAdapter = walker.createValueAdapter(root) ?: return null
        return read(rootAdapter)
    }

    private fun read(rootAdapter: CirJsonValueAdapter): CirJsonSchemaObject {
        val root = CirJsonSchemaObject(myFile, "/")
        enqueue(myQueue, root, rootAdapter)

        while (myQueue.isNotEmpty()) {
            val currentItem = myQueue.removeFirst()

            val currentSchema = currentItem.first
            val pointer = currentSchema.pointer
            val adapter = currentItem.second

            if (adapter is CirJsonObjectValueAdapter) {
                val list = adapter.propertyList
                for (property in list) {
                    val values = property.values

                    if (values.size != 1) {
                        continue
                    }

                    val name = property.name ?: continue
                    val reader = READERS_MAP[name]
                    val value = values.first()

                    if (reader != null) {
                        reader.read(value, currentSchema, myQueue, myFile)
                    } else {
                        readSingleDefinition(name, value, currentSchema, pointer)
                    }
                }
            } else if (adapter is CirJsonArrayValueAdapter) {
                val values = adapter.elements

                for (indexedValue in values.withIndex()) {
                    readSingleDefinition(indexedValue.index.toString(), indexedValue.value, currentSchema, pointer)
                }
            }

            if (currentSchema.id != null) {
                myIds[currentSchema.id!!] = currentSchema
            }

            currentSchema.completeInitialization(adapter)
        }

        return root
    }

    private fun readSingleDefinition(name: String, value: CirJsonValueAdapter, currentSchema: CirJsonSchemaObject,
            pointer: String) {
        val nextPointer = getNewPointer(name, pointer)
        val defined = enqueue(myQueue, CirJsonSchemaObject(myFile, nextPointer), value)
        val definitions = currentSchema.definitionsMap?.toMutableMap()
                ?: HashMap<String, CirJsonSchemaObject>().also { currentSchema.definitionsMap = it }
        definitions[name] = defined
    }

    fun interface MyReader {

        fun read(element: CirJsonValueAdapter, obj: CirJsonSchemaObject,
                queue: MutableCollection<Pair<CirJsonSchemaObject, CirJsonValueAdapter>>, virtualFile: VirtualFile)

    }

    companion object {

        private val MAX_SCHEMA_LENGTH = FileUtilRt.LARGE_FOR_CONTENT_LOADING

        private val cirJsonObjectMapper = ObjectMapper(JsonFactory())

        val LOG = Logger.getInstance(CirJsonSchemaReader::class.java)

        val ERRORS_NOTIFICATION = NotificationGroupManager.getInstance().getNotificationGroup("CirJSON Schema")

        private val READERS_MAP = HashMap<String, MyReader>().apply {
            this["\$anchor"] = createFromStringValue { obj, s -> obj.id = s }
            this["\$id"] = createFromStringValue { obj, s -> obj.id = s }
            this["id"] = createFromStringValue { obj, s -> obj.id = s }
            // TODO schema when added
            this["description"] = createFromStringValue { obj, s -> obj.description = s }
            this["deprecationMessage"] = createFromStringValue { obj, s -> obj.deprecationMessage = s }
            this[CirJsonSchemaObject.X_INTELLIJ_HTML_DESCRIPTION] =
                    createFromStringValue { obj, s -> obj.htmlDescription = s }
            this[CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION] = MyReader { element, obj, _, _ ->
                readInjectionMetadata(element, obj)
            }
            this[CirJsonSchemaObject.X_INTELLIJ_ENUM_METADATA] =
                    MyReader { element, obj, _, _ -> readEnumMetadata(element, obj) }
            this[CirJsonSchemaObject.X_INTELLIJ_CASE_INSENSITIVE] = MyReader { element, obj, _, _ ->
                if (element.isBooleanLiteral) {
                    obj.isForceCaseInsensitive = getBoolean(element)
                }
            }
            this["title"] = createFromStringValue { obj, s -> obj.title = s }
            this["\$ref"] = createFromStringValue { obj, s -> obj.ref = s }
            this["\$recursiveRef"] = createFromStringValue { obj, s ->
                obj.ref = s
                obj.isRefRecursive = true
            }
            this["\$recursiveRef"] = MyReader { element, obj, _, _ ->
                if (element.isBooleanLiteral) {
                    obj.isRecursiveAnchor = true
                }
            }
            this["default"] = createDefault()
            this["example"] = createExampleConsumer()
            this["format"] = createFromStringValue { obj, s -> obj.format = s }
            this[CirJsonSchemaObject.DEFINITIONS] = createDefinitionsConsumer()
            this[CirJsonSchemaObject.DEFINITIONS_v9] = createDefinitionsConsumer()
            this[CirJsonSchemaObject.PROPERTIES] = createPropertiesConsumer()
            this["multipleOf"] = createFromNumber { obj, i -> obj.multipleOf = i }
            this["maximum"] = createFromNumber { obj, i -> obj.multipleOf = i }
            this["minimum"] = createFromNumber { obj, i -> obj.multipleOf = i }
            this["exclusiveMaximum"] = MyReader { element, obj, _, _ ->
                if (element.isBooleanLiteral) {
                    obj.isExclusiveMaximum = getBoolean(element)
                } else if (element.isNumberLiteral) {
                    obj.exclusiveMaximumNumber = getNumber(element)
                }
            }
            this["exclusiveMinimum"] = MyReader { element, obj, _, _ ->
                if (element.isBooleanLiteral) {
                    obj.isExclusiveMinimum = getBoolean(element)
                } else if (element.isNumberLiteral) {
                    obj.exclusiveMinimumNumber = getNumber(element)
                }
            }
            this["maxLength"] = createFromInteger { obj, i -> obj.maxLength = i }
            this["minLength"] = createFromInteger { obj, i -> obj.minLength = i }
            this["pattern"] = createFromStringValue { obj, s -> obj.pattern = s }
            this[CirJsonSchemaObject.ADDITIONAL_ITEMS] = createAdditionalItems()
            this[CirJsonSchemaObject.ITEMS] = createItems()
            this["contains"] = createContains()
            this["maxItems"] = createFromInteger { obj, i -> obj.maxItems = i }
            this["minItems"] = createFromInteger { obj, i -> obj.minItems = i }
            this["uniqueItems"] = MyReader { element, obj, _, _ ->
                if (element.isBooleanLiteral) {
                    obj.uniqueItems = getBoolean(element)
                }
            }
            this["maxProperties"] = createFromInteger { obj, i -> obj.maxProperties = i }
            this["minProperties"] = createFromInteger { obj, i -> obj.minProperties = i }
            this["required"] = createRequired()
            this["additionalProperties"] = createAdditionalProperties()
            this["propertyNames"] =
                    createFromObject("propertyNames") { obj, schema -> obj.propertyNamesSchema = schema }
            this["patternProperties"] = createPatternProperties()
            this["dependencies"] = createDependencies()
            this["enum"] = createEnum()
            this["const"] = MyReader { element, obj, _, _ ->
                obj.enum = ContainerUtil.createMaybeSingletonList(readEnumValue(element))
            }
            this["type"] = createType()
            this["allOf"] = createContainer { obj, members -> obj.allOf = members }
            this["anyOf"] = createContainer { obj, members -> obj.anyOf = members }
            this["oneOf"] = createContainer { obj, members -> obj.oneOf = members }
            this["not"] = createFromObject("not") { obj, members -> obj.not = members }
            this["if"] = createFromObject("if") { obj, members -> obj.`if` = members }
            this["then"] = createFromObject("then") { obj, members -> obj.then = members }
            this["else"] = createFromObject("else") { obj, members -> obj.`else` = members }
            this["instanceof"] = MyReader { _, obj, _, _ -> obj.shouldValidateAgainstJSType = true }
            this["typeof"] = MyReader { _, obj, _, _ -> obj.shouldValidateAgainstJSType = true }
        }

        fun checkIfValidJsonSchema(project: Project, file: VirtualFile): String? {
            val length = file.length
            val fileName = file.name

            if (length > MAX_SCHEMA_LENGTH) {
                return CirJsonBundle.message("schema.reader.file.too.large", fileName, length)
            } else if (length == 0L) {
                return CirJsonBundle.message("schema.reader.file.empty", fileName)
            }

            try {
                readFromFile(project, file)
            } catch (e: Exception) {
                val message =
                        CirJsonBundle.message("schema.reader.file.not.found.or.error", fileName, e.message.toString())
                LOG.info(message)
                return message
            }

            return null
        }

        @Throws(Exception::class)
        fun readFromFile(project: Project, file: VirtualFile): CirJsonSchemaObject {
            if (!file.isValid) {
                throw Exception(CirJsonBundle.message("schema.reader.cant.load.file", file.name))
            }

            val psiFile = PsiManager.getInstance(project).findFile(file)
            val obj = if (psiFile != null) CirJsonSchemaReader(file).read(psiFile) else null

            if (obj == null) {
                throw Exception(CirJsonBundle.message("schema.reader.cant.load.model", file.name))
            }

            return obj
        }

        private fun enqueue(queue: MutableCollection<Pair<CirJsonSchemaObject, CirJsonValueAdapter>>,
                schemaObject: CirJsonSchemaObject, container: CirJsonValueAdapter): CirJsonSchemaObject {
            queue.add(Pair.create(schemaObject, container))
            return schemaObject
        }

        private fun getNewPointer(name: String, oldPointer: String): String {
            return if (oldPointer == "/") {
                "$oldPointer$name"
            } else {
                "$oldPointer/$name"
            }
        }

        private fun readInjectionMetadata(element: CirJsonValueAdapter, obj: CirJsonSchemaObject) {
            if (element.isStringLiteral) {
                obj.languageInjection = getString(element)
            } else if (element is CirJsonObjectValueAdapter) {
                for (adapter in element.propertyList) {
                    val lang = readSingleProp(adapter, "language", CirJsonSchemaReader::getString)
                    lang?.let { obj.languageInjection = it }
                    val prefix = readSingleProp(adapter, "prefix", CirJsonSchemaReader::getString)
                    prefix?.let { obj.languageInjectionPrefix = it }
                    val suffix = readSingleProp(adapter, "suffix", CirJsonSchemaReader::getString)
                    suffix?.let { obj.languageInjectionSuffix = it }
                }
            }
        }

        private fun <T> readSingleProp(adapter: CirJsonPropertyAdapter, propName: String,
                getterFunc: (CirJsonValueAdapter) -> T): T? {
            if (propName == adapter.name) {
                val values = adapter.values

                if (values.size == 1) {
                    return getterFunc.invoke(values.first())
                }
            }

            return null
        }

        private fun readEnumMetadata(element: CirJsonValueAdapter, obj: CirJsonSchemaObject) {
            if (element !is CirJsonObjectValueAdapter) {
                return
            }

            val metadataMap = HashMap<String, Map<String, String>>()

            for (adapter in element.propertyList) {
                val name = adapter.name ?: continue
                val values = adapter.values

                if (values.size != 1) {
                    continue
                }

                val valueAdapter = values.first()

                if (valueAdapter.isStringLiteral) {
                    metadataMap[name] = Collections.singletonMap("description", getString(valueAdapter))
                } else if (valueAdapter is CirJsonObjectValueAdapter) {
                    val valueMap = HashMap<String, String>()

                    for (propertyAdapter in valueAdapter.propertyList) {
                        val adapterName = propertyAdapter.name ?: continue
                        val adapterValues = propertyAdapter.values

                        if (adapterValues.size != 1) {
                            continue
                        }

                        val next = adapterValues.first()

                        if (next.isStringLiteral) {
                            valueMap[adapterName] = getString(next)
                        }
                    }

                    metadataMap[name] = valueMap
                }
            }

            obj.enumMetadata = metadataMap
        }

        private fun createFromStringValue(propertySetter: BiConsumer<CirJsonSchemaObject, String>): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element.isStringLiteral) {
                    propertySetter.accept(obj, getString(element))
                }
            }
        }

        private fun createFromInteger(propertySetter: BiConsumer<CirJsonSchemaObject, Int>): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element.isNumberLiteral) {
                    propertySetter.accept(obj, getNumber(element).toInt())
                }
            }
        }

        private fun createFromNumber(propertySetter: BiConsumer<CirJsonSchemaObject, Number>): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element.isNumberLiteral) {
                    propertySetter.accept(obj, getNumber(element).toInt())
                }
            }
        }

        private fun createFromObject(prop: String,
                propertySetter: BiConsumer<CirJsonSchemaObject, CirJsonSchemaObject>): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    propertySetter.accept(obj,
                            enqueue(queue, CirJsonSchemaObject(virtualFile, getNewPointer(prop, obj.pointer)), element))
                }
            }
        }

        private fun createContainer(
                propertySetter: BiConsumer<CirJsonSchemaObject, MutableList<CirJsonSchemaObject>>): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element is CirJsonArrayValueAdapter) {
                    val list = element.elements
                    val members = ArrayList<CirJsonSchemaObject>(list.size)

                    for (indexedValue in list.withIndex()) {
                        val value = indexedValue.value

                        if (!value.isObject) {
                            continue
                        }

                        val i = indexedValue.index
                        members.add(enqueue(queue,
                                CirJsonSchemaObject(virtualFile, getNewPointer(i.toString(), obj.pointer)), value))
                    }

                    propertySetter.accept(obj, members)
                }
            }
        }

        private fun createType(): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element.isStringLiteral) {
                    val type = parseType(StringUtil.unquoteString(element.delegate.text)) ?: return@MyReader
                    obj.type = type
                } else if (element is CirJsonArrayValueAdapter) {
                    val types = element.elements.filter(notEmptyString())
                            .mapNotNull { parseType(StringUtil.unquoteString(it.delegate.text)) }.toSet()

                    if (types.isNotEmpty()) {
                        obj.typeVariants = types
                    }
                }
            }
        }

        private fun parseType(typeString: String): CirJsonSchemaType? {
            return try {
                CirJsonSchemaType.valueOf("_$typeString")
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun readEnumValue(value: CirJsonValueAdapter): Any? {
            return when {
                value.isStringLiteral -> "\"${StringUtil.unquoteString(value.delegate.text)}\""
                value.isNumberLiteral -> getNumber(value)
                value.isBooleanLiteral -> getBoolean(value)
                value.isNull -> "null"
                value is CirJsonArrayValueAdapter -> {
                    EnumArrayValueWrapper(value.elements.mapNotNull { readEnumValue(it) }.toTypedArray())
                }

                value is CirJsonObjectValueAdapter -> {
                    EnumObjectValueWrapper(value.propertyList.filter { it.values.size == 1 }.mapNotNull {
                        val v = readEnumValue(it.values.first())

                        if (v == null) {
                            null
                        } else {
                            Pair.create<String?, Any>(it.name, v)
                        }
                    }.stream().collect(Collectors.toMap({ it.first }, { it.second })))
                }

                else -> null
            }
        }

        private fun createEnum(): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element is CirJsonArrayValueAdapter) {
                    val objects = ArrayList<Any>()
                    val list = element.elements

                    for (value in list) {
                        val enumValue = readEnumValue(value) ?: return@MyReader
                        objects.add(enumValue)
                    }

                    obj.enum = objects
                }
            }
        }

        private fun getString(value: CirJsonValueAdapter): String {
            return StringUtil.unquoteString(value.delegate.text)
        }

        private fun getBoolean(value: CirJsonValueAdapter): Boolean {
            return value.delegate.text.toBoolean()
        }

        private fun getNumber(value: CirJsonValueAdapter): Number {
            return try {
                value.delegate.text.toInt()
            } catch (e: NumberFormatException) {
                try {
                    value.delegate.text.toDouble()
                } catch (e2: NumberFormatException) {
                    -1
                }
            }
        }

        private fun createPatternProperties(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.patternProperties =
                            readInnerObject(getNewPointer("patternProperties", obj.pointer), element, queue,
                                    virtualFile)
                }
            }
        }

        private fun createDependencies(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element !is CirJsonObjectValueAdapter) {
                    return@MyReader
                }

                val propertyDependencies = hashMapOf<String, List<String>>()
                val schemaDependencies = hashMapOf<String, CirJsonSchemaObject>()

                val list = element.propertyList

                for (property in list) {
                    val escapedName = CirJsonPointerUtil.escapeForCirJsonPointer(StringUtil.notNullize(property.name))
                    val values = property.values

                    if (values.size != 1) {
                        continue
                    }

                    val value = values.firstOrNull() ?: continue

                    if (value is CirJsonArrayValueAdapter) {
                        val dependencies = value.elements.filter(notEmptyString())
                                .map { StringUtil.unquoteString(it.delegate.text) }.toList()

                        if (dependencies.isNotEmpty()) {
                            propertyDependencies[property.name!!] = dependencies
                        }
                    } else if (value.isObject) {
                        val newPointer = getNewPointer("dependencies/$escapedName", obj.pointer)
                        schemaDependencies[property.name!!] =
                                enqueue(queue, CirJsonSchemaObject(virtualFile, newPointer), value)
                    }
                }

                obj.propertyDependencies = propertyDependencies
                obj.schemaDependencies = schemaDependencies
            }
        }

        private fun notEmptyString(): (CirJsonValueAdapter) -> Boolean {
            return { it.isStringLiteral && !StringUtil.isEmptyOrSpaces(it.delegate.text) }
        }

        private fun createAdditionalProperties(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isBooleanLiteral) {
                    obj.additionalPropertiesAllowed = getBoolean(element)
                } else if (element.isObject) {
                    obj.additionalPropertiesSchema = enqueue(queue,
                            CirJsonSchemaObject(virtualFile, getNewPointer("additionalProperties", obj.pointer)),
                            element)
                }
            }
        }

        private fun createItems(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.itemsSchema =
                            enqueue(queue, CirJsonSchemaObject(virtualFile, getNewPointer("items", obj.pointer)),
                                    element)
                } else if (element is CirJsonArrayValueAdapter) {
                    val list = ArrayList<CirJsonSchemaObject>()
                    val values = element.elements

                    for (indexedValue in values.withIndex()) {
                        val i = indexedValue.index
                        val value = indexedValue.value

                        if (value.isObject) {
                            list.add(enqueue(queue,
                                    CirJsonSchemaObject(virtualFile, getNewPointer("items/$i", obj.pointer)), value))
                        }
                    }
                }
            }
        }

        private fun createRequired(): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element is CirJsonArrayValueAdapter) {
                    obj.required = LinkedHashSet(element.elements.filter(notEmptyString())
                            .map { StringUtil.unquoteString(it.delegate.text) })
                }
            }
        }

        private fun createDefinitionsConsumer(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.definitionsMap =
                            readInnerObject(getNewPointer("definitions", obj.pointer), element, queue, virtualFile)
                }
            }
        }

        private fun createContains(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.containsSchema =
                            enqueue(queue, CirJsonSchemaObject(virtualFile, getNewPointer("contains", obj.pointer)),
                                    element)
                }
            }
        }

        private fun createAdditionalItems(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isBooleanLiteral) {
                    obj.additionalItemsAllowed = getBoolean(element)
                } else if (element.isObject) {
                    obj.additionalItemsSchema = enqueue(queue,
                            CirJsonSchemaObject(virtualFile, getNewPointer("additionalItems", obj.pointer)), element)
                }
            }
        }

        private fun createPropertiesConsumer(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.properties =
                            readInnerObject(getNewPointer("properties", obj.pointer), element, queue, virtualFile)
                }
            }
        }

        private fun readInnerObject(parentPointer: String, element: CirJsonValueAdapter,
                queue: MutableCollection<Pair<CirJsonSchemaObject, CirJsonValueAdapter>>,
                virtualFile: VirtualFile): Map<String, CirJsonSchemaObject> {
            val map = HashMap<String, CirJsonSchemaObject>()

            if (element !is CirJsonObjectValueAdapter) {
                return map
            }

            val properties = element.propertyList

            for (property in properties) {
                val values = property.values

                if (values.size != 1) {
                    continue
                }

                val value = values.first()
                val propertyName = property.name ?: continue

                if (value.isBooleanLiteral) {
                    map[propertyName] = CirJsonSchemaObject(virtualFile, getNewPointer(propertyName, parentPointer))
                    continue
                }

                if (!value.isObject) {
                    continue
                }

                map[propertyName] =
                        enqueue(queue, CirJsonSchemaObject(virtualFile, getNewPointer(propertyName, parentPointer)),
                                value)
            }

            return map
        }

        private fun createDefault(): MyReader {
            return MyReader { element, obj, queue, virtualFile ->
                if (element.isObject) {
                    obj.default =
                            enqueue(queue, CirJsonSchemaObject(virtualFile, getNewPointer("default", obj.pointer)),
                                    element)
                } else if (element.isStringLiteral) {
                    obj.default = StringUtil.unquoteString(element.delegate.text)
                } else if (element.isNumberLiteral) {
                    obj.default = getNumber(element)
                } else if (element.isBooleanLiteral) {
                    obj.default = getBoolean(element)
                }
            }
        }

        private fun createExampleConsumer(): MyReader {
            return MyReader { element, obj, _, _ ->
                if (element.isObject) {
                    obj.example = readExample(element)
                }
            }
        }

        private fun readExample(element: CirJsonValueAdapter): Map<String, Any> {
            val example = HashMap<String, Any>()

            if (element !is CirJsonObjectValueAdapter) {
                return example
            }

            for (property in element.propertyList) {
                val value = property.values.first()
                val propertyName = property.name ?: continue
                example[propertyName] = value
            }

            return example
        }

    }

}