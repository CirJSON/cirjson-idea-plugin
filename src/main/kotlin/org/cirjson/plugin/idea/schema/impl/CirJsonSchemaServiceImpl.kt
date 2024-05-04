package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diagnostic.PluginException
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.SmartList
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.*
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import org.cirjson.plugin.idea.schema.remote.CirJsonSchemaCatalogManager
import java.util.*
import java.util.concurrent.atomic.AtomicLong

open class CirJsonSchemaServiceImpl(final override val project: Project) : CirJsonSchemaService, ModificationTracker,
        Disposable {

    private val myCatalogManager = CirJsonSchemaCatalogManager(project)

    private val myFactories = CirJsonSchemaProviderFactories()

    private val myState = MyState(project) { myFactories.providers }

    private val myBuiltInSchemaIds = object : ClearableLazyValue<Set<String>>() {

        override fun compute(): Set<String> {
            return ContainerUtil.map2SetNotNull(myState.files) { CirJsonCachedValues.getSchemaId(it, project) }
        }

    }

    private val myRefs = ConcurrentCollectionFactory.createConcurrentSet<String>()

    private val myAnyChangeCount = AtomicLong(0)

    private val myResetActions = ContainerUtil.createConcurrentList<Runnable>()

    init {
        CirJsonSchemaProviderFactory.EP_NAME.addChangeListener(this::reset, this)
        CirJsonSchemaEnabler.EXTENSION_POINT_NAME.addChangeListener(this::reset, this)
        // TODO continue when needed
    }

    override fun getModificationCount(): Long {
        return myAnyChangeCount.get()
    }

    override fun dispose() {}

    protected open val providerFactories: List<CirJsonSchemaProviderFactory>
        get() {
            return CirJsonSchemaProviderFactory.EP_NAME.extensionList
        }

    private fun resetWithCurrentFactories() {
        myState.reset()
        myBuiltInSchemaIds.drop()

        for (action in myResetActions) {
            action.run()
        }

        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    fun getSchemasForFile(file: VirtualFile, single: Boolean, onlyUserSchemas: Boolean): Collection<VirtualFile> {
        if (shouldIgnoreFile(file, project)) {
            return emptyList()
        }

        var schemaUrl: String? = null

        if (!onlyUserSchemas) {
            schemaUrl = CirJsonCachedValues.getSchemaUrlFromSchemaProperty(file, project)

            if (CirJsonFileResolver.isSchemaUrl(schemaUrl)) {
                val virtualFile = resolveFromSchemaProperty(schemaUrl, file)

                if (virtualFile != null) {
                    return Collections.singletonList(virtualFile)
                }
            }
        }

        val providers = getProvidersForFile(file)

        var checkSchemaProperty = true

        if (!onlyUserSchemas && providers.none { it.schemaType == SchemaType.USER_SCHEMA }) {
            schemaUrl = schemaUrl ?: CirJsonCachedValues.getSchemaUrlFromSchemaProperty(file, project)
            schemaUrl = schemaUrl ?: CirJsonSchemaByCommentProvider.getCommentSchema(file, project)
            val virtualFile = resolveFromSchemaProperty(schemaUrl, file)

            if (virtualFile != null) {
                return Collections.singletonList(virtualFile)
            }

            checkSchemaProperty = false
        }

        if (!single) {
            val files = arrayListOf<VirtualFile>()

            for (provider in providers) {
                val schemaFile = getSchemaForProvider(project, provider)

                if (schemaFile != null) {
                    files.add(schemaFile)
                }
            }

            if (files.isNotEmpty()) {
                return files
            }
        } else if (providers.isNotEmpty()) {
            if (providers.size > 2) {
                return emptyList()
            }

            val selected = if (providers.size > 1) {
                providers.first { it.schemaType == SchemaType.USER_SCHEMA }
            } else {
                providers[0]
            }

            val schemaFile = getSchemaForProvider(project, selected)
            return ContainerUtil.createMaybeSingletonList(schemaFile)
        }

        if (onlyUserSchemas) {
            return emptyList()
        }

        if (checkSchemaProperty) {
            schemaUrl = schemaUrl ?: CirJsonCachedValues.getSchemaUrlFromSchemaProperty(file, project)
            val virtualFile = resolveFromSchemaProperty(schemaUrl, file)

            if (virtualFile != null) {
                return Collections.singletonList(virtualFile)
            }
        }

        val schemaFromOtherSources = resolveSchemaFromOtherSources(file)

        if (schemaFromOtherSources != null) {
            return ContainerUtil.createMaybeSingletonList(schemaFromOtherSources)
        }

        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        return ContainerUtil.createMaybeSingletonList(getDynamicSchemaForFile(psiFile))
    }

    fun getProvidersForFile(file: VirtualFile): List<CirJsonSchemaFileProvider> {
        val map = myState.myData.value

        if (map.isEmpty()) {
            return emptyList()
        }

        var result: SmartList<CirJsonSchemaFileProvider>? = null

        for (providers in map.values) {
            for (provider in providers) {
                if (isProviderAvailable(file, provider)) {
                    if (result == null) {
                        result = SmartList()
                    }

                    result.add(provider)
                }
            }
        }

        return result ?: emptyList()
    }

    private fun resolveFromSchemaProperty(schemaUrl: String?, file: VirtualFile): VirtualFile? {
        if (schemaUrl != null) {
            val virtualFile = findSchemaFileByReference(schemaUrl, file)

            if (virtualFile != null) {
                return virtualFile
            }
        }

        return null
    }

    override fun isSchemaFile(schemaObject: CirJsonSchemaObject): Boolean {
        val file = resolveSchemaFile(schemaObject) ?: return false
        return isSchemaFile(file)
    }

    override fun isSchemaFile(file: VirtualFile): Boolean {
        return isMappedSchema(file) || isSchemaByProvider(file) || hasSchemaSchema(file)
    }

    private fun isMappedSchema(file: VirtualFile): Boolean {
        return isMappedSchema(file, true)
    }

    fun isMappedSchema(file: VirtualFile, canRecompute: Boolean): Boolean {
        return (canRecompute || myState.isComputed) && file in myState.files
    }

    private fun isSchemaByProvider(file: VirtualFile): Boolean {
        val provider = myState.getProvider(file)

        if (provider != null) {
            return isSchemaProvider(provider)
        }

        val map = myState.myData.value

        for (providers in map.values) {
            for (p in providers) {
                if (isSchemaProvider(p) && p.isAvailable(file)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isSchemaProvider(provider: CirJsonSchemaFileProvider): Boolean {
        return CirJsonFileResolver.isSchemaUrl(provider.remoteSource)
    }

    private fun getSchemaVersionFromSchemaUrl(file: VirtualFile): CirJsonSchemaVersion? {
        val res = Ref.create<String?>(null)

        ApplicationManager.getApplication().runReadAction {
            res.set(CirJsonCachedValues.getSchemaUrlFromSchemaProperty(file, project))
        }

        if (res.isNull) {
            return null
        }

        return CirJsonSchemaVersion.byId(res.get())
    }

    private fun hasSchemaSchema(file: VirtualFile): Boolean {
        return getSchemaVersionFromSchemaUrl(file) != null
    }

    private fun resolveSchemaFromOtherSources(file: VirtualFile): VirtualFile? {
        return myCatalogManager.getSchemaFileForFile(file)
    }

    override fun getDynamicSchemaForFile(psiFile: PsiFile): VirtualFile? {
        return ContentAwareCirJsonSchemaFileProvider.EP_NAME.extensionList.mapNotNull { it.getSchemaFile(psiFile) }
                .firstOrNull()
    }

    override fun registerReference(ref: String) {
        var realRef = ref
        val index = StringUtil.lastIndexOfAny(realRef, "\\/")

        if (index >= 0) {
            realRef = realRef.substring(index + 1)
        }

        myRefs.add(realRef)
    }

    override fun getSchemaObject(file: VirtualFile): CirJsonSchemaObject? {
        val schemas = getSchemasForFile(file, single = true, onlyUserSchemas = false)

        if (schemas.isEmpty()) {
            return null
        }

        assert(schemas.size == 1)
        val schemaFile = schemas.first()
        return CirJsonCachedValues.getSchemaObject(replaceHttpFileWithBuiltinIfNeeded(schemaFile), project)
    }

    override fun getSchemaObject(file: PsiFile): CirJsonSchemaObject? {
        return CirJsonCachedValues.computeSchemaForFile(file, this)
    }

    fun replaceHttpFileWithBuiltinIfNeeded(schemaFile: VirtualFile): VirtualFile {
        if (schemaFile is HttpVirtualFile
                && (!CirJsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas
                        || CirJsonFileResolver.isSchemaUrl(schemaFile.url))) {
            val url = schemaFile.url
            val first = getLocalSchemaByUrl(url)
            return first ?: schemaFile
        }

        return schemaFile
    }

    fun getLocalSchemaByUrl(url: String): VirtualFile? {
        return myState.files.find {
            val prov = getSchemaProvider(it)
            prov != null && prov.schemaFile !is HttpVirtualFile && url == prov.remoteSource
        }
    }

    override fun getSchemaObjectForSchemaFile(schemaFile: VirtualFile): CirJsonSchemaObject? {
        return CirJsonCachedValues.getSchemaObject(schemaFile, project)
    }

    override fun findSchemaFileByReference(reference: String, referent: VirtualFile?): VirtualFile? {
        val file = findBuiltInSchemaByReference(reference)

        if (file != null) {
            return file
        }

        if (reference.startsWith("#")) {
            return referent
        }

        return CirJsonFileResolver.resolveSchemaByReference(referent, CirJsonPointerUtil.normalizeId(reference))
    }

    private fun findBuiltInSchemaByReference(reference: String): VirtualFile? {
        val id = CirJsonPointerUtil.normalizeId(reference)

        if (id !in myBuiltInSchemaIds.value) {
            return null
        }

        for (file in myState.files) {
            if (id == CirJsonCachedValues.getSchemaId(file, project)) {
                return file
            }
        }

        return null
    }

    override fun getSchemaProvider(schemaFile: VirtualFile): CirJsonSchemaFileProvider? {
        return myState.getProvider(schemaFile)
    }

    override fun getSchemaProvider(schemaObject: CirJsonSchemaObject): CirJsonSchemaFileProvider? {
        val file = resolveSchemaFile(schemaObject) ?: return null
        return getSchemaProvider(file)
    }

    override fun resolveSchemaFile(schemaObject: CirJsonSchemaObject): VirtualFile? {
        val rawFile = schemaObject.rawFile

        if (rawFile != null) {
            return rawFile
        }

        val fileUrl = schemaObject.fileUrl ?: return null

        return VirtualFileManager.getInstance().findFileByUrl(fileUrl)
    }

    override fun reset() {
        myFactories.reset()
        resetWithCurrentFactories()
    }

    override val allUserVisibleSchemas: List<CirJsonSchemaInfo>
        get() {
            TODO()
        }

    override fun isApplicableToFile(file: VirtualFile?): Boolean {
        if (file == null) {
            return false
        }

        for (e in CirJsonSchemaEnabler.EXTENSION_POINT_NAME.extensionList) {
            if (e.isEnabledForFile(file, project)) {
                return true
            }
        }

        return false
    }

    private class MyState(project: Project, factory: () -> List<CirJsonSchemaFileProvider>) {

        val myData = SynchronizedClearableLazy { createFileProviderMap(factory.invoke(), project) }

        fun reset() {
            myData.drop()
        }

        val files: Set<VirtualFile>
            get() {
                return myData.value.keys
            }

        fun getProvider(file: VirtualFile): CirJsonSchemaFileProvider? {
            val providers = myData.value[file]

            if (providers.isNullOrEmpty()) {
                return null
            }

            for (p in providers) {
                if (p.schemaType == SchemaType.USER_SCHEMA) {
                    return p
                }
            }

            return providers[0]
        }

        val isComputed: Boolean
            get() {
                return myData.isInitialized()
            }

        companion object {

            private fun createFileProviderMap(list: List<CirJsonSchemaFileProvider>, project: Project):
                    Map<VirtualFile, List<CirJsonSchemaFileProvider>> {
                val map = HashMap<VirtualFile, MutableList<CirJsonSchemaFileProvider>>()

                for (provider in list) {
                    var schemaFile: VirtualFile?

                    try {
                        schemaFile = getSchemaForProvider(project, provider)
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        LOG.error(e)
                        continue
                    }

                    if (schemaFile != null) {
                        map.computeIfAbsent(schemaFile) { SmartList() }.add(provider)
                    }
                }

                return map
            }

        }

    }

    private inner class CirJsonSchemaProviderFactories {

        @Volatile
        private var myProviders: List<CirJsonSchemaFileProvider>? = null

        val providers: List<CirJsonSchemaFileProvider>
            get() {
                var providers = myProviders

                if (providers == null) {
                    providers = dumbAwareProvidersAndUpdateRestWhenSmart
                    myProviders = providers
                }

                return providers
            }

        fun reset() {
            myProviders = null
        }

        private val dumbAwareProvidersAndUpdateRestWhenSmart: List<CirJsonSchemaFileProvider>
            get() {
                val readyFactories = ArrayList<CirJsonSchemaProviderFactory>()
                val notReadyFactories = ArrayList<CirJsonSchemaProviderFactory>()
                val dumb = DumbService.getInstance(project).isDumb

                for (factory in providerFactories) {
                    if (!dumb || DumbService.isDumbAware(factory)) {
                        readyFactories.add(factory)
                    } else {
                        notReadyFactories.add(factory)
                    }
                }

                val providers = getProvidersFromFactories(readyFactories)
                myProviders = providers

                if (notReadyFactories.isNotEmpty() && !LightEdit.owns(project)) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        if (project.isDisposed) {
                            return@executeOnPooledThread
                        }

                        DumbService.getInstance(project).runReadActionInSmartMode {
                            if (myProviders === providers) {
                                val newProviders = getProvidersFromFactories(notReadyFactories)

                                if (newProviders.isNotEmpty()) {
                                    val oldProviders = myProviders
                                    myProviders = ContainerUtil.concat(oldProviders!!, newProviders)
                                    resetWithCurrentFactories()
                                }
                            }
                        }
                    }
                }

                return providers
            }

        private fun getProvidersFromFactories(
                factories: List<CirJsonSchemaProviderFactory>): List<CirJsonSchemaFileProvider> {
            val providers = ArrayList<CirJsonSchemaFileProvider>()

            for (factory in factories) {
                try {
                    providers.addAll(factory.getProviders(project))
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    PluginException.logPluginError(LOG, e.toString(), e, factory::class.java)
                }
            }

            return providers
        }

    }

    companion object {

        private val LOG = Logger.getInstance(CirJsonSchemaServiceImpl::class.java)

        private fun shouldIgnoreFile(file: VirtualFile, project: Project): Boolean {
            return CirJsonSchemaMappingsProjectConfiguration.getInstance(project).isIgnoredFile(file)
        }

        private fun isProviderAvailable(file: VirtualFile, provider: CirJsonSchemaFileProvider): Boolean {
            return provider.isAvailable(file)
        }

        private fun getSchemaForProvider(project: Project, provider: CirJsonSchemaFileProvider): VirtualFile? {
            if (CirJsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas) {
                val source = provider.remoteSource

                if (source != null && !source.endsWith("!") && !CirJsonFileResolver.isSchemaUrl(source)) {
                    return VirtualFileManager.getInstance().findFileByUrl(source)
                }
            }

            return provider.schemaFile
        }

    }

}