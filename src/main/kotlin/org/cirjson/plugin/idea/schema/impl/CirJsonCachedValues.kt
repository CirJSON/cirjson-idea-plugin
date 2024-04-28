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
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameKind
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameProvider
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
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
        TODO()
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

}