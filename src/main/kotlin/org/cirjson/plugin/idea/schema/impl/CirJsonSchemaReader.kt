package org.cirjson.plugin.idea.schema.impl

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
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

        private val READERS_MAP = HashMap<String, MyReader>().apply {
            this["\$anchor"] = createFromStringValue { obj, s -> obj.id = s }
            this["\$id"] = createFromStringValue { obj, s -> obj.id = s }
            this["id"] = createFromStringValue { obj, s -> obj.id = s }
            // TODO schema when added
            // TODO description when added
            this["deprecationMessage"] = createFromStringValue { obj, s -> obj.deprecationMessage = s }
            // TODO htmlDescription when added
            // TODO injectionMetadata when added
            this[CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION] =
                    MyReader { element, obj, _, _ -> readEnumMetadata(element, obj) }
            // TODO forceCaseInsensitive when added
            // TODO title when added
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
            // TODO example when added
            // TODO format when added
            this[CirJsonSchemaObject.DEFINITIONS] = createDefinitionsConsumer()
            this[CirJsonSchemaObject.DEFINITIONS_v9] = createDefinitionsConsumer()
            this[CirJsonSchemaObject.PROPERTIES] = createPropertiesConsumer()
            // TODO multipleOf when added
            // TODO maximum when added
            // TODO minimum when added
            // TODO exclusiveMaximum when added
            // TODO exclusiveMinimum when added
            // TODO maxLength when added
            // TODO minLength when added
            // TODO pattern when added
            this[CirJsonSchemaObject.ADDITIONAL_ITEMS] = createAdditionalItems()
            this[CirJsonSchemaObject.ITEMS] = createItems()
            // TODO contains when added
            // TODO maxItems when added
            // TODO minItems when added
            // TODO uniqueItems when added
            // TODO maxProperties when added
            // TODO minProperties when added
            this["required"] = createRequired()
            this["additionalProperties"] = createAdditionalProperties()
            // TODO propertyNames when added
            // TODO patternProperties when added
            // TODO dependencies when added
            this["enum"] = createEnum()
            this["type"] = createType()
            this["allOf"] = createContainer { obj, members -> obj.allOf = members }
            this["anyOf"] = createContainer { obj, members -> obj.anyOf = members }
            this["oneOf"] = createContainer { obj, members -> obj.oneOf = members }
            this["not"] = createFromObject("not") { obj, members -> obj.not = members }
            this["if"] = createFromObject("if") { obj, members -> obj.`if` = members }
            this["then"] = createFromObject("then") { obj, members -> obj.then = members }
            this["else"] = createFromObject("else") { obj, members -> obj.`else` = members }
            // TODO instanceof when added
            // TODO typeof when added
        }

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

    }

}