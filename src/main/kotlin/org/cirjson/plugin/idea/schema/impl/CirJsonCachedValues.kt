package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AstLoadingFilter
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameKind
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameProvider
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogEntry
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver

object CirJsonCachedValues {

    private val CIR_JSON_OBJECT_CACHE_KEY = Key.create<CachedValue<CirJsonSchemaObject?>>("CirJsonSchemaObjectCache")

    const val URL_CACHE_KEY: String = "CirJsonSchemaUrlCache"

    private val SCHEMA_URL_KEY = Key.create<CachedValue<String?>>(URL_CACHE_KEY)

    const val ID_CACHE_KEY: String = "CirJsonSchemaIdCache"

    const val OBSOLETE_ID_CACHE_KEY: String = "CirJsonSchemaObsoleteIdCache"

    private val SCHEMA_ID_CACHE_KEY = Key.create<CachedValue<String?>>(ID_CACHE_KEY)

    const val ID_PATHS_CACHE_KEY: String = "CirJsonSchemaIdToPointerCache"

    private val SCHEMA_ID_PATHS_CACHE_KEY = Key.create<CachedValue<MutableMap<String, String>>>(ID_PATHS_CACHE_KEY)

    private val SCHEMA_CATALOG_CACHE_KEY =
            Key.create<CachedValue<List<CirJsonSchemaCatalogEntry>?>>("CirJsonSchemaCatalogCache")

    private val OBJECT_FOR_FILE_KEY = Key.create<CachedValue<CirJsonSchemaObject?>>("CirJsonCachedValues.OBJ_KEY")

    fun getSchemaObject(schemaFile: VirtualFile, project: Project): CirJsonSchemaObject? {
        CirJsonFileResolver.startFetchingHttpFileIfNeeded(schemaFile, project)
        return computeForFile(schemaFile, project, {
            CirJsonSchemaCacheManager.getInstance(it.project).computeSchemaObject(schemaFile, it)
        }, CIR_JSON_OBJECT_CACHE_KEY)
    }

    fun getSchemaUrlFromSchemaProperty(file: VirtualFile, project: Project): String? {
        val value = CirJsonSchemaFileValuesIndex.getCachedValue(project, file, URL_CACHE_KEY)

        if (value != null) {
            return if (value != CirJsonSchemaFileValuesIndex.NULL) value else null
        }

        val psiFile = resolveFile(file, project) ?: return null
        return getOrCompute(psiFile, CirJsonCachedValues::fetchSchemaUrl, SCHEMA_URL_KEY)
    }

    private fun resolveFile(file: VirtualFile, project: Project): PsiFile? {
        if (project.isDisposed || !file.isValid) {
            return null
        }

        return PsiManager.getInstance(project).findFile(file)
    }

    private fun fetchSchemaUrl(psiFile: PsiFile?): String? {
        if (psiFile == null) {
            return null
        }

        if (psiFile is CirJsonFile) {
            val url = CirJsonSchemaFileValuesIndex.readTopLevelProps(psiFile.fileType, psiFile.text)[URL_CACHE_KEY]
            return if (url == null || url == CirJsonSchemaFileValuesIndex.NULL) null else url
        }

        val walker = CirJsonLikePsiWalker.getWalker(psiFile, CirJsonSchemaObject.NULL_OBJ) ?: return null

        val roots = walker.getRoots(psiFile)

        for (root in roots ?: listOf()) {
            val adapter = walker.createValueAdapter(root)
            val obj = adapter?.asObject ?: return null
            val list = obj.propertyList

            for (propertyAdapter in list) {
                if (propertyAdapter.name == CirJsonSchemaFileValuesIndex.SCHEMA_PROPERTY_NAME) {
                    val values = propertyAdapter.values

                    if (values.size == 1) {
                        val item = values.first()

                        if (item.isStringLiteral) {
                            return StringUtil.unquoteString(item.delegate.text)
                        }
                    }
                }
            }
        }

        return null
    }

    fun getSchemaId(schemaFile: VirtualFile, project: Project): String? {
        if (schemaFile is LightVirtualFile) {
            return null
        }

        val value = CirJsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, ID_CACHE_KEY)

        if (value != null && value != CirJsonSchemaFileValuesIndex.NULL) {
            return CirJsonPointerUtil.normalizeId(value)
        }

        val obsoleteValue = CirJsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, OBSOLETE_ID_CACHE_KEY)

        if (obsoleteValue != null && obsoleteValue != CirJsonSchemaFileValuesIndex.NULL) {
            return CirJsonPointerUtil.normalizeId(obsoleteValue)
        }

        if (value == CirJsonSchemaFileValuesIndex.NULL || obsoleteValue == CirJsonSchemaFileValuesIndex.NULL) {
            return null
        }

        val result = computeForFile(schemaFile, project, CirJsonCachedValues::fetchSchemaId, SCHEMA_ID_CACHE_KEY)
                ?: return null
        return CirJsonPointerUtil.normalizeId(result)
    }

    fun <T> computeForFile(schemaFile: VirtualFile, project: Project, eval: (PsiFile) -> T,
            key: Key<CachedValue<T>>): T? {
        val psiFile = resolveFile(schemaFile, project) ?: return null
        return getOrCompute(psiFile, eval, key)
    }

    fun getAllIdsInFile(file: PsiFile): Collection<String> {
        return getOrComputeIdsMap(file).keys
    }

    fun resolveId(psiFile: PsiFile, id: String): String? {
        return getOrComputeIdsMap(psiFile)[id]
    }

    fun getOrComputeIdsMap(psiFile: PsiFile): MutableMap<String, String> {
        return getOrCompute(psiFile, CirJsonCachedValues::computeIdsMap, SCHEMA_ID_PATHS_CACHE_KEY)
    }

    private fun computeIdsMap(file: PsiFile): MutableMap<String, String> {
        return SyntaxTraverser.psiTraverser(file).filter(CirJsonProperty::class.java)
                .filter { StringUtil.unquoteString(it.nameElement.text) == "\$id" }
                .filter { it.value is CirJsonStringLiteral }
                .toMap({ (it.value!! as CirJsonStringLiteral).value }, {
                    CirJsonQualifiedNameProvider.generateQualifiedName(it.parent,
                            CirJsonQualifiedNameKind.CirJsonPointer)
                })
    }

    fun fetchSchemaId(psiFile: PsiFile): String? {
        if (psiFile !is CirJsonFile) {
            return null
        }

        val props = CirJsonSchemaFileValuesIndex.readTopLevelProps(psiFile.fileType, psiFile.text)
        val id = props[ID_CACHE_KEY]

        if (id != null && id != CirJsonSchemaFileValuesIndex.NULL) {
            return id
        }

        val obsoleteId = props[OBSOLETE_ID_CACHE_KEY]

        return if (obsoleteId != CirJsonSchemaFileValuesIndex.NULL) obsoleteId else null
    }

    fun getSchemaCatalog(catalog: VirtualFile, project: Project): List<CirJsonSchemaCatalogEntry>? {
        if (!catalog.isValid) {
            return null
        }

        return computeForFile(catalog, project, CirJsonCachedValues::computeSchemaCatalog, SCHEMA_CATALOG_CACHE_KEY)
    }

    private fun computeSchemaCatalog(catalog: PsiFile): List<CirJsonSchemaCatalogEntry>? {
        if (!catalog.isValid) {
            return null
        }

        val virtualFile = catalog.virtualFile ?: return null

        if (!virtualFile.isValid) {
            return null
        }

        val value = AstLoadingFilter.forceAllowTreeLoading<CirJsonValue, RuntimeException>(catalog) {
            (catalog as? CirJsonFile)?.topLevelValue
        }

        if (value !is CirJsonObject) {
            return null
        }

        val schemas = value.findProperty("schemas") ?: return null
        val schemasValue = schemas.value

        if (schemasValue !is CirJsonArray) {
            return null
        }

        val catalogMap = arrayListOf<CirJsonSchemaCatalogEntry>()
        fillMap(schemasValue, catalogMap)
        return catalogMap
    }

    private fun fillMap(array: CirJsonArray, catalogMap: MutableList<CirJsonSchemaCatalogEntry>) {
        for (value in array.valueList) {
            val obj = value as? CirJsonObject ?: continue
            val fileMatch = obj.findProperty("fileMatch")
            val masks = fileMatch?.let { resolveMasks(it.value) } ?: emptyList()
            val urlString = readStringValue(obj.findProperty("url")) ?: continue
            catalogMap.add(CirJsonSchemaCatalogEntry(masks, urlString, readStringValue(obj.findProperty("name")),
                    readStringValue(obj.findProperty("description"))))
        }
    }

    private fun resolveMasks(value: CirJsonValue?): Collection<String> {
        if (value is CirJsonStringLiteral) {
            return ContainerUtil.createMaybeSingletonList(value.value)
        }

        if (value !is CirJsonArray) {
            return emptyList()
        }

        val strings = arrayListOf<String>()

        for (v in value.valueList) {
            if (v is CirJsonStringLiteral) {
                strings.add(v.value)
            }
        }

        return strings
    }

    private fun readStringValue(property: CirJsonProperty?): String? {
        property ?: return null
        val urlValue = property.value

        if (urlValue is CirJsonStringLiteral) {
            val urlStringValue = urlValue.value

            if (!StringUtil.isEmpty(urlStringValue)) {
                return urlStringValue
            }
        }

        return null
    }

    private fun <T> getOrCompute(psiFile: PsiFile, eval: (PsiFile) -> T, key: Key<CachedValue<T>>): T {
        return CachedValuesManager.getCachedValue(psiFile, key) {
            CachedValueProvider.Result.create(eval.invoke(psiFile), psiFile)
        }
    }

    fun computeSchemaForFile(file: PsiFile, service: CirJsonSchemaService): CirJsonSchemaObject? {
        val originalFile = CompletionUtil.getOriginalOrSelf(file)
        val value = CachedValuesManager.getCachedValue(originalFile, OBJECT_FOR_FILE_KEY) {
            val schema = getSchemaFile(originalFile, service)

            val obj = schema.second ?: CirJsonSchemaObject.NULL_OBJ
            val psiFile =
                    schema.first ?: return@getCachedValue CachedValueProvider.Result.create(obj, originalFile, service)
            CachedValueProvider.Result.create(obj, psiFile, service)
        }

        return if (value === CirJsonSchemaObject.NULL_OBJ) null else value
    }

    private fun getSchemaFile(originalFile: PsiFile,
            service: CirJsonSchemaService): Pair<PsiFile, CirJsonSchemaObject> {
        val virtualFile = originalFile.virtualFile
        val schemaFile = if (virtualFile == null) {
            null
        } else {
            getSchemaFile(virtualFile, service)
        }
        val schemaObject = if (virtualFile == null) {
            null
        } else {
            service.getSchemaObject(virtualFile)
        }
        val psiFile = if (schemaFile == null || !schemaFile.isValid) {
            null
        } else {
            originalFile.manager.findFile(schemaFile)
        }

        return Pair(psiFile, schemaObject)
    }

    fun getSchemaFile(sourceFile: VirtualFile, service: CirJsonSchemaService): VirtualFile? {
        val serviceImpl = service as CirJsonSchemaServiceImpl
        val schemas = serviceImpl.getSchemasForFile(sourceFile, single = true, onlyUserSchemas = false)

        if (schemas.isEmpty()) {
            return null
        }

        assert(schemas.size == 1)
        return schemas.first()
    }

    fun hasComputedSchemaObjectForFile(file: PsiFile): Boolean {
        val data = CompletionUtil.getOriginalOrSelf(file).getUserData(OBJECT_FOR_FILE_KEY) ?: return false
        val cachedValueGetter = data.upToDateOrNull ?: return false
        val upToDateCachedValueOrNull = cachedValueGetter.get()
        return upToDateCachedValueOrNull != null && upToDateCachedValueOrNull !== CirJsonSchemaObject.NULL_OBJ
    }

}