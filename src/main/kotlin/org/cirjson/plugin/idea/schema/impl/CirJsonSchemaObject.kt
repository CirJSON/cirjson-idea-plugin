package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.schema.CirJsonDependencyModificationTracker
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class CirJsonSchemaObject private constructor(val rawFile: VirtualFile?, val fileUrl: String?,
        val pointer: String) {

    private var myBackRef: CirJsonSchemaObject? = null

    private var myDefinitionsMap: MutableMap<String, CirJsonSchemaObject>? = null

    private var myProperties: MutableMap<String, CirJsonSchemaObject> = HashMap()

    private var myPatternProperties: PatternProperties? = null

    private var myId: String? = null

    private var myType: CirJsonSchemaType? = null

    private var myRef: String? = null

    private var myRefIsRecursive = false

    private var myIsRecursiveAnchor = false

    private var myTypeVariants: MutableSet<CirJsonSchemaType>? = null

    private var myAdditionalPropertiesAllowed: Boolean? = null

    private var myAdditionalPropertiesNotAllowedFor: MutableSet<String>? = null

    private var myAdditionalPropertiesSchema: CirJsonSchemaObject? = null

    private var myAdditionalItemsAllowed: Boolean? = null

    private var myAdditionalItemsSchema: CirJsonSchemaObject? = null

    private var myItemsSchema: CirJsonSchemaObject? = null

    private var myItemsSchemaList: MutableList<CirJsonSchemaObject>? = null

    private var myRequired: MutableSet<String>? = null

    private var myEnum: MutableList<Any>? = null

    private var myAllOf: MutableList<CirJsonSchemaObject>? = null

    private var myAnyOf: MutableList<CirJsonSchemaObject>? = null

    private var myOneOf: MutableList<CirJsonSchemaObject>? = null

    private var myNot: CirJsonSchemaObject? = null

    private var myIfThenElse: MutableList<IfThenElse>? = null

    private var myIf: CirJsonSchemaObject? = null

    private var myThen: CirJsonSchemaObject? = null

    private var myElse: CirJsonSchemaObject? = null

    private var myIsValidByExclusion = true

    private var myIdsMap: MutableMap<String, String>? = null

    private val myUserDataHolder = UserDataHolderBase()

    constructor(file: VirtualFile?, pointer: String) : this(
            if (file?.url != null && CirJsonFileResolver.isTempOrMockUrl(file.url)) file else null, file?.url, pointer)

    constructor(pointer: String) : this(null, pointer)

    fun completeInitialization(cirJsonObject: CirJsonValueAdapter) {
        if (myIf != null) {
            myIfThenElse = ArrayList<IfThenElse>().apply {
                add(IfThenElse(myIf!!, myThen, myElse))
            }
        }

        myIdsMap = CirJsonCachedValues.getOrComputeIdsMap(cirJsonObject.delegate.containingFile)
    }

    fun resolveId(id: String): String? {
        return myIdsMap?.get(id)
    }

    private fun mergeTypes(selfType: CirJsonSchemaType?, otherType: CirJsonSchemaType?,
            otherTypeVariants: MutableSet<CirJsonSchemaType>?): CirJsonSchemaType? {
        if (selfType == null) {
            return otherType
        }

        if (otherType == null) {
            if (!otherTypeVariants.isNullOrEmpty()) {
                val filteredVariants = EnumSet.noneOf(CirJsonSchemaType::class.java)

                for (variant in otherTypeVariants) {
                    val subtype = getSubtypeOfBoth(selfType, variant)

                    if (subtype != null) {
                        filteredVariants.add(subtype)
                    }
                }

                if (filteredVariants.isEmpty()) {
                    myIsValidByExclusion = false
                    return selfType
                }

                if (filteredVariants.size == 1) {
                    return filteredVariants.first()
                }

                return null // will be handled by variants
            }

            return selfType
        }

        val subtypeOfBoth = getSubtypeOfBoth(selfType, otherType)

        if (subtypeOfBoth == null) {
            myIsValidByExclusion = false
            return otherType
        }

        return subtypeOfBoth
    }

    private fun mergeTypeVariantSets(self: MutableSet<CirJsonSchemaType>?,
            other: MutableSet<CirJsonSchemaType>?): MutableSet<CirJsonSchemaType>? {
        if (self == null) {
            return other
        }

        if (other == null) {
            return self
        }

        val resultSet = EnumSet.noneOf(CirJsonSchemaType::class.java)

        for (type in self) {
            val merged = mergeTypes(type, null, other)

            if (merged != null) {
                resultSet.add(merged)
            }
        }

        if (resultSet.isEmpty()) {
            myIsValidByExclusion = false
            return other
        }

        return resultSet
    }

    // peer pointer is not merged!
    fun mergeValues(other: CirJsonSchemaObject) {
        // we do not copy id, schema
        mergeProperties(this, other)
        myDefinitionsMap = copyMap(myDefinitionsMap, other.myDefinitionsMap)
        val map = copyMap(myPatternProperties?.mySchemasMap, other.myPatternProperties?.mySchemasMap)
        myPatternProperties = if (map != null) {
            PatternProperties(map)
        } else {
            null
        }

        // TODO: merge myTitle when added
        // TODO: merge myDescription when added
        // TODO: merge myHtmlDescription when added
        // TODO: merge myDeprecationMessage when added

        myType = mergeTypes(myType, other.myType, other.myTypeVariants)

        // TODO: merge myDefault when added
        // TODO: merge myExample when added

        if (other.myRef != null) {
            myRef = other.myRef
        }

        // TODO: merge myFormat when added

        myTypeVariants = mergeTypeVariantSets(myTypeVariants, other.myTypeVariants)

        // TODO: merge myMultipleOf when added
        // TODO: merge myMaximum when added
        // TODO: merge myExclusiveMaximumNumber when added
        // TODO: merge myExclusiveMaximum when added
        // TODO: merge myMinimum when added
        // TODO: merge myExclusiveMinimumNumber when added
        // TODO: merge myExclusiveMinimum when added
        // TODO: merge myMaxLength when added
        // TODO: merge myMinLength when added
        // TODO: merge myPattern when added

        if (other.myAdditionalPropertiesAllowed != null) {
            myAdditionalPropertiesAllowed = other.myAdditionalPropertiesAllowed

            if (other.myAdditionalPropertiesAllowed == false) {
                addAdditionalPropsNotAllowedFor(other.fileUrl!!, other.pointer)
            }
        }

        if (other.myAdditionalPropertiesSchema != null) {
            myAdditionalPropertiesSchema = other.myAdditionalPropertiesSchema
        }

        if (other.myAdditionalItemsAllowed != null) {
            myAdditionalItemsAllowed = other.myAdditionalItemsAllowed
        }

        if (other.myAdditionalItemsSchema != null) {
            myAdditionalItemsSchema = other.myAdditionalItemsSchema
        }

        if (other.myItemsSchema != null) {
            myItemsSchema = other.myItemsSchema
        }

        // TODO: merge myContainsSchema when added

        myItemsSchemaList = copyList(myItemsSchemaList, other.myItemsSchemaList)

        // TODO: merge myMaxItems when added
        // TODO: merge myMinItems when added
        // TODO: merge myUniqueItems when added
        // TODO: merge myMaxProperties when added
        // TODO: merge myMinProperties when added

        if (myRequired != null && other.myRequired != null) {
            val set = HashSet<String>(myRequired!!.size + other.myRequired!!.size)
            set.addAll(myRequired!!)
            set.addAll(other.myRequired!!)
            myRequired = set
        }

        // TODO: merge myPropertyDependencies when added
        // TODO: merge mySchemaDependencies when added
        // TODO: merge myEnumMetadata when added

        if (other.myEnum != null) {
            myEnum = other.myEnum
        }

        myAllOf = copyList(myAllOf, other.myAllOf)
        myAnyOf = copyList(myAnyOf, other.myAnyOf)
        myOneOf = copyList(myOneOf, other.myOneOf)

        if (other.myNot != null) {
            myNot = other.myNot
        }

        if (other.myIfThenElse != null) {
            if (myIfThenElse == null) {
                myIfThenElse = other.myIfThenElse
            } else {
                myIfThenElse = ContainerUtil.concat(myIfThenElse!!, other.myIfThenElse!!)
            }
        }

        // TODO: merge myShouldValidateAgainstJSType when added
        // TODO: merge myLanguageInjection when added
        // TODO: merge myForceCaseInsensitive when added
    }

    var definitionsMap: Map<String, CirJsonSchemaObject>?
        get() {
            return myDefinitionsMap
        }
        set(value) {
            myDefinitionsMap = value?.toMutableMap()
        }

    val isValidByExclusion: Boolean
        get() {
            return myIsValidByExclusion
        }

    var properties: Map<String, CirJsonSchemaObject>
        get() {
            return myProperties
        }
        set(value) {
            myProperties = value.toMutableMap()
        }

    var additionalPropertiesAllowed: Boolean?
        get() {
            return myAdditionalPropertiesAllowed ?: true
        }
        set(value) {
            myAdditionalPropertiesAllowed = value

            if (myAdditionalPropertiesAllowed == false) {
                addAdditionalPropsNotAllowedFor(fileUrl!!, pointer)
            }
        }

    private fun addAdditionalPropsNotAllowedFor(url: String, pointer: String) {
        val newSet: MutableSet<String> = if (myAdditionalPropertiesNotAllowedFor != null) {
            HashSet(myAdditionalPropertiesNotAllowedFor!!)
        } else {
            HashSet()
        }

        newSet.add(url + pointer)

        myAdditionalPropertiesNotAllowedFor = newSet
    }

    var additionalPropertiesSchema: CirJsonSchemaObject?
        get() {
            return myAdditionalPropertiesSchema
        }
        set(value) {
            myAdditionalPropertiesSchema = value
        }

    var additionalItemsAllowed: Boolean?
        get() {
            return myAdditionalItemsAllowed ?: true
        }
        set(value) {
            myAdditionalItemsAllowed = value
        }

    var additionalItemsSchema: CirJsonSchemaObject?
        get() {
            return myAdditionalItemsSchema
        }
        set(value) {
            myAdditionalItemsSchema = value
        }

    var itemsSchema: CirJsonSchemaObject?
        get() {
            return myItemsSchema
        }
        set(value) {
            myItemsSchema = value
        }

    var itemsSchemaList: List<CirJsonSchemaObject>?
        get() {
            return myItemsSchemaList
        }
        set(value) {
            myItemsSchemaList = value?.toMutableList()
        }

    var type: CirJsonSchemaType?
        get() {
            return myType
        }
        set(value) {
            myType = value
        }

    var typeVariants: Set<CirJsonSchemaType>?
        get() {
            return myTypeVariants
        }
        set(value) {
            myTypeVariants = value?.toMutableSet()
        }

    var ref: String?
        get() {
            return myRef
        }
        set(value) {
            myRef = value
        }

    var isRefRecursive: Boolean
        get() {
            return myRefIsRecursive
        }
        set(value) {
            myRefIsRecursive = value
        }

    var isRecursiveAnchor: Boolean
        get() {
            return myIsRecursiveAnchor
        }
        set(value) {
            myIsRecursiveAnchor = value
        }

    var backReference: CirJsonSchemaObject
        get() = throw IllegalAccessException()
        set(value) {
            myBackRef = value
        }

    var id: String?
        get() {
            return myId
        }
        set(value) {
            myId = value
        }

    var required: Set<String>?
        get() {
            return myRequired
        }
        set(value) {
            myRequired = value?.toMutableSet()
        }

    var enum: List<Any>?
        get() {
            return myEnum
        }
        set(value) {
            myEnum = value?.toMutableList()
        }

    var allOf: MutableList<CirJsonSchemaObject>?
        get() {
            return myAllOf
        }
        set(value) {
            myAllOf = value
        }

    var anyOf: MutableList<CirJsonSchemaObject>?
        get() {
            return myAnyOf
        }
        set(value) {
            myAnyOf = value
        }

    var oneOf: MutableList<CirJsonSchemaObject>?
        get() {
            return myOneOf
        }
        set(value) {
            myOneOf = value
        }

    val ifThenElse: List<IfThenElse>?
        get() {
            return myIfThenElse
        }

    var not: CirJsonSchemaObject?
        get() {
            return myNot
        }
        set(value) {
            myNot = value
        }

    var `if`: CirJsonSchemaObject?
        get() = throw IllegalAccessException()
        set(value) {
            myIf = value
        }

    var then: CirJsonSchemaObject?
        get() = throw IllegalAccessException()
        set(value) {
            myThen = value
        }

    var `else`: CirJsonSchemaObject?
        get() = throw IllegalAccessException()
        set(value) {
            myElse = value
        }

    fun getMatchingPatternPropertySchema(name: String): CirJsonSchemaObject? {
        return myPatternProperties?.getPatternPropertySchema(name)
    }

    fun findRelativeDefinition(ref: String): CirJsonSchemaObject? {
        var realRef = ref
        if (CirJsonPointerUtil.isSelfReference(realRef)) {
            return this
        }

        if (!realRef.startsWith("#/")) {
            return null
        }

        realRef = realRef.substring(2)
        val parts = CirJsonPointerUtil.split(realRef)
        var current: CirJsonSchemaObject? = this
        var i = 0

        while (i < parts.size) {
            if (current == null) {
                return null
            }

            val part = parts[i]

            if (part == DEFINITIONS || part == DEFINITIONS_v9) {
                if (i == parts.size - 1) {
                    return null
                }

                val nextPart = parts[++i]
                current = current.definitionsMap?.get(CirJsonPointerUtil.unescapeCirJsonPointerPart(nextPart))
                continue
            }

            if (part == PROPERTIES) {
                if (i == parts.size - 1) {
                    return null
                }

                current = current.definitionsMap?.get(CirJsonPointerUtil.unescapeCirJsonPointerPart(parts[++i]))
                continue
            }

            if (part == ITEMS) {
                if (i == parts.size - 1) {
                    current = current.itemsSchema
                } else {
                    val next = tryParseInt(parts[++i])
                    val itemsSchemaList = current.itemsSchemaList

                    if (itemsSchemaList != null && next != null && next < itemsSchemaList.size) {
                        current = itemsSchemaList[next]
                    }
                }

                continue
            }

            if (part == ADDITIONAL_ITEMS) {
                if (i == parts.size - 1) {
                    current = current.additionalItemsSchema
                }

                continue
            }

            current = current.definitionsMap?.get(part)
            i++
        }

        return current
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null || other !is CirJsonSchemaObject) {
            return false
        }

        return fileUrl == other.fileUrl && pointer == other.pointer
    }

    override fun hashCode(): Int {
        return arrayOf(fileUrl, pointer).hashCode()
    }

    fun resolveRefSchema(service: CirJsonSchemaService): CirJsonSchemaObject? {
        val ref = this.ref
        assert(!StringUtil.isEmptyOrSpaces(ref))
        val refsStorage = getComputedRefsStorage(service.project)
        val schemaObject = refsStorage.getOrDefault(ref, NULL_OBJ)

        if (schemaObject != NULL_OBJ) {
            return schemaObject
        }

        val value = fetchSchemaFromRefDefinition(ref!!, this, service, isRefRecursive)

        if (!CirJsonFileResolver.isHttpPath(ref)) {
            service.registerReference(ref)
        } else if (value != null) {
            // our aliases - if http ref actually refers to a local file with specific ID
            val virtualFile = service.resolveSchemaFile(value)

            if (virtualFile != null && virtualFile !is HttpVirtualFile) {
                service.registerReference(virtualFile.name)
            }
        }

        if (value != null && value != NULL_OBJ && value.fileUrl != fileUrl) {
            value.backReference = this
        }

        refsStorage[ref] = value ?: NULL_OBJ
        return value
    }

    private fun getComputedRefsStorage(project: Project): ConcurrentMap<String, CirJsonSchemaObject> {
        return CachedValuesManager.getManager(project).getCachedValue(myUserDataHolder) {
            CachedValueProvider.Result.create(ConcurrentHashMap(),
                    CirJsonDependencyModificationTracker.forProject(project))
        }
    }

    private class PatternProperties(schemasMap: Map<String, CirJsonSchemaObject>) {

        val mySchemasMap: MutableMap<String, CirJsonSchemaObject> = HashMap()

        private val myCachedPatterns: MutableMap<String, Pattern> = HashMap()

        private val myCachedPatternProperties: MutableMap<String, String> =
                CollectionFactory.createConcurrentWeakKeyWeakValueMap()

        init {
            schemasMap.keys.forEach { mySchemasMap[StringUtil.unescapeBackSlashes(it)] = schemasMap[it]!! }
            mySchemasMap.keys.forEach {
                val pair = compilePattern(it)

                if (pair.second == null) {
                    assert(pair.first != null)
                    myCachedPatterns[it] = pair.first!!
                }
            }
        }

        fun getPatternPropertySchema(name: String): CirJsonSchemaObject? {
            var value = myCachedPatternProperties[name]

            if (value != null) {
                assert(mySchemasMap.containsKey(value))
                return mySchemasMap[value]
            }

            value = ContainerUtil.find(myCachedPatterns.keys) { matchPatter(myCachedPatterns[it]!!, name) }

            if (value != null) {
                myCachedPatternProperties[name] = value
                assert(mySchemasMap.containsKey(value))
                return mySchemasMap[value]
            }

            return null
        }

    }

    companion object {

        private val LOG = Logger.getInstance(CirJsonSchemaObject::class.java)

        const val MOCK_URL = "mock:///"

        const val TEMP_URL = "temp:///"

        const val DEFINITIONS = "definitions"

        const val DEFINITIONS_v9 = "\$defs"

        const val PROPERTIES = "properties"

        const val ITEMS = "items"

        const val ADDITIONAL_ITEMS = "additionalItems"

        const val X_INTELLIJ_LANGUAGE_INJECTION = "x-intellij-language-injection"

        val NULL_OBJ = CirJsonSchemaObject("\$_NULL_$")

        private fun getSubtypeOfBoth(selfType: CirJsonSchemaType, otherType: CirJsonSchemaType): CirJsonSchemaType? {
            if (otherType == CirJsonSchemaType._any) {
                return selfType
            }

            if (selfType == CirJsonSchemaType._any) {
                return otherType
            }

            return when (selfType) {
                CirJsonSchemaType._string -> {
                    if (otherType == CirJsonSchemaType._string || otherType == CirJsonSchemaType._string_number) {
                        CirJsonSchemaType._string
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._number -> {
                    if (otherType == CirJsonSchemaType._integer) {
                        CirJsonSchemaType._integer
                    } else {
                        if (otherType == CirJsonSchemaType._number || otherType == CirJsonSchemaType._string_number) {
                            CirJsonSchemaType._number
                        } else {
                            null
                        }
                    }
                }

                CirJsonSchemaType._integer -> {
                    if (otherType == CirJsonSchemaType._number || otherType == CirJsonSchemaType._string_number
                            || otherType == CirJsonSchemaType._integer) {
                        CirJsonSchemaType._integer
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._object -> {
                    if (otherType == CirJsonSchemaType._object) {
                        CirJsonSchemaType._object
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._array -> {
                    if (otherType == CirJsonSchemaType._array) {
                        CirJsonSchemaType._array
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._boolean -> {
                    if (otherType == CirJsonSchemaType._boolean) {
                        CirJsonSchemaType._boolean
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._null -> {
                    if (otherType == CirJsonSchemaType._null) {
                        CirJsonSchemaType._null
                    } else {
                        null
                    }
                }

                CirJsonSchemaType._string_number -> {
                    if (otherType == CirJsonSchemaType._integer || otherType == CirJsonSchemaType._number
                            || otherType == CirJsonSchemaType._string
                            || otherType == CirJsonSchemaType._string_number) {
                        otherType
                    } else {
                        null
                    }
                }

                else -> otherType
            }
        }

        private fun mergeProperties(thisObject: CirJsonSchemaObject, otherObject: CirJsonSchemaObject) {
            for (prop in otherObject.myProperties.entries) {
                val key = prop.key
                val otherProp = prop.value

                if (key !in thisObject.myProperties.keys) {
                    thisObject.myProperties[key] = otherObject
                } else {
                    val existingProp = thisObject.myProperties[key]!!
                    thisObject.myProperties[key] = merge(existingProp, otherProp, otherProp)
                }
            }
        }

        private fun <T> copyList(target: MutableList<T>?, source: MutableList<T>?): MutableList<T>? {
            if (source.isNullOrEmpty()) {
                return target
            }

            val result = target ?: ArrayList(source.size)
            result.addAll(source)
            return result
        }

        private fun <K, V> copyMap(target: MutableMap<K, V>?, source: MutableMap<K, V>?): MutableMap<K, V>? {
            if (source.isNullOrEmpty()) {
                return target
            }

            val result = target ?: HashMap(source.size)
            result.putAll(source)
            return result
        }

        private fun tryParseInt(s: String): Int? {
            return try {
                s.toInt()
            } catch (_: Exception) {
                null
            }
        }

        private fun adaptSchemaPattern(pattern: String): String {
            var realPattern = pattern

            realPattern = if (realPattern.startsWith("^") || realPattern.startsWith("*") ||
                    realPattern.startsWith(".")) {
                realPattern
            } else {
                ".*$realPattern"
            }
            realPattern = if (realPattern.endsWith("+") || realPattern.endsWith("*") || realPattern.endsWith("$")) {
                realPattern
            } else {
                "$pattern.*"
            }
            realPattern = realPattern.replace("\\\\", "\\")

            return realPattern
        }

        private fun compilePattern(pattern: String): Pair<Pattern?, String?> {
            return try {
                Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null)
            } catch (e: PatternSyntaxException) {
                Pair.create(null, e.message)
            }
        }

        private fun matchPatter(pattern: Pattern, s: String): Boolean {
            return try {
                pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches()
            } catch (e: ProcessCanceledException) {
                // something wrong with the pattern, infinite cycle?
                LOG.info("Pattern matching canceled")
                false
            } catch (e: Exception) {
                // catch exceptions around to prevent things like https://bugs.openjdk.org/browse/JDK-6984178
                LOG.info(e)
                false
            }
        }

        private fun fetchSchemaFromRefDefinition(ref: String, schema: CirJsonSchemaObject,
                service: CirJsonSchemaService, recursive: Boolean): CirJsonSchemaObject? {
            val schemaFile = service.resolveSchemaFile(schema) ?: return null
            val splitter = CirJsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(ref)
            val schemaId = splitter.schemaId

            if (schemaId != null) {
                val refSchema = resolveSchemaByReference(service, schemaFile, schemaId) ?: return null
                return findRelativeDefinition(refSchema, splitter, service)
            }

            var rootSchema = service.getSchemaObjectForSchemaFile(schemaFile)

            if (rootSchema == null) {
                LOG.debug("Schema object not found for ${schemaFile.path}")
                return null
            }

            if (recursive) {
                while (rootSchema!!.isRecursiveAnchor) {
                    val backRef = rootSchema.myBackRef ?: break
                    val file = ObjectUtils.coalesce(backRef.rawFile,
                            if (backRef.fileUrl == null) null else CirJsonFileResolver.urlToFile(backRef.fileUrl))
                            ?: break
                    try {
                        rootSchema = CirJsonSchemaReader.readFromFile(service.project, file)
                    } catch (e: Exception) {
                        break
                    }
                }
            }

            return findRelativeDefinition(rootSchema, splitter, service)
        }

        private fun resolveSchemaByReference(service: CirJsonSchemaService, schemaFile: VirtualFile,
                schemaId: String): CirJsonSchemaObject? {
            val refFile = service.findSchemaFileByReference(schemaId, schemaFile)

            if (refFile == null) {
                LOG.debug("Schema file not found by reference: '$schemaId' from ${schemaFile.path}")
                return null
            }

            if (refFile is HttpVirtualFile) {
                val info = refFile.fileInfo

                if (info != null) {
                    val state = info.state

                    if (state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
                        CirJsonFileResolver.startFetchingHttpFileIfNeeded(refFile, service.project)
                        return NULL_OBJ
                    } else if (state == RemoteFileState.DOWNLOADING_IN_PROGRESS) {
                        return NULL_OBJ
                    }
                }
            }

            val refSchema = service.getSchemaObjectForSchemaFile(refFile)

            if (refSchema == null) {
                LOG.debug("Schema file not found by reference: '$schemaId' from ${schemaFile.path}")
                return null
            }

            return refSchema
        }

        private fun findRelativeDefinition(schema: CirJsonSchemaObject,
                splitter: CirJsonSchemaVariantsTreeBuilder.SchemaUrlSplitter,
                service: CirJsonSchemaService): CirJsonSchemaObject? {
            val path = splitter.relativePath

            if (StringUtil.isEmptyOrSpaces(path)) {
                val id = splitter.schemaId

                if (CirJsonPointerUtil.isSelfReference(id)) {
                    return schema
                }

                if (id != null && id.startsWith("#")) {
                    val resolvedId = schema.resolveId(id)

                    if (resolvedId == null || id == "#$resolvedId") {
                        return null
                    }

                    return findRelativeDefinition(schema,
                            CirJsonSchemaVariantsTreeBuilder.SchemaUrlSplitter("#$resolvedId"), service)
                }

                return schema
            }

            val definition = schema.findRelativeDefinition(path)

            if (definition == null) {
                val schemaFile = service.resolveSchemaFile(schema)
                LOG.debug("Definition not found by reference: '$path' in file ${schemaFile?.path ?: "(no file)"}")
            }

            return definition
        }

        fun merge(base: CirJsonSchemaObject, other: CirJsonSchemaObject,
                pointTo: CirJsonSchemaObject): CirJsonSchemaObject {
            val obj = CirJsonSchemaObject(pointTo.rawFile, pointTo.fileUrl, pointTo.pointer)
            obj.mergeValues(other)
            obj.mergeValues(base)
            obj.ref = other.ref
            return obj
        }

    }

}