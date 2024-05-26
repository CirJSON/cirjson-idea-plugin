package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileManager
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogEntry
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.impl.CirJsonCachedValues
import java.nio.file.*

class CirJsonSchemaCatalogManager(private val myProject: Project) {

    private val myRemoteContentProvider = CirJsonSchemaRemoteContentProvider()

    private var myCatalog: VirtualFile? = null

    private var myResolvedMappings = ContainerUtil.createConcurrentSoftMap<VirtualFile, String>()

    private var myTestSchemaStoreFile: VirtualFile? = null

    private val myDownloadingAdapters = CollectionFactory.createConcurrentWeakMap<Runnable, FileDownloadingAdapter>()

    fun startUpdates() {
        CirJsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler {
            update()
            CirJsonSchemaService.get(myProject).reset()
        }

        val instance = RemoteFileManager.getInstance()
        instance.addRemoteContentProvider(myRemoteContentProvider)
        update()
    }

    private fun update() {
        val application = ApplicationManager.getApplication()

        if (application.isUnitTestMode) {
            myCatalog = myTestSchemaStoreFile
            return
        }

        myCatalog = if (CirJsonFileResolver.isRemoteEnabled(myProject)) {
            CirJsonFileResolver.urlToFile(DEFAULT_CATALOG)
        } else {
            null
        }
    }

    fun getSchemaFileForFile(file: VirtualFile): VirtualFile? {
        if (!CirJsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled) {
            return null
        }

        if (CirJsonSchemaCatalogExclusion.EP_NAME.findFirstSafe { it.isExcluded(file) } != null) {
            return null
        }

        var schemaUrl = myResolvedMappings[file]

        if (schemaUrl == EMPTY) {
            return null
        }

        if (schemaUrl == null && myCatalog != null) {
            schemaUrl = resolveSchemaFile(file, myCatalog!!, myProject)

            if (NO_CACHE == schemaUrl) {
                return null
            }

            myResolvedMappings[file] = schemaUrl ?: EMPTY
        }

        if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl)) {
            return null
        }

        return CirJsonFileResolver.resolveSchemaByReference(file, schemaUrl)
    }

    val allCatalogEntries: List<CirJsonSchemaCatalogEntry>
        get() {
            myCatalog ?: return emptyList()
            return CirJsonCachedValues.getSchemaCatalog(myCatalog!!, myProject) ?: emptyList()
        }

    fun registerCatalogUpdateCallback(callback: Runnable) {
        val info = (myCatalog as? HttpVirtualFile)?.fileInfo ?: return
        val adapter = object : FileDownloadingAdapter() {

            override fun fileDownloaded(file: VirtualFile) {
                callback.run()
            }

        }

        myDownloadingAdapters[callback] = adapter
        info.addDownloadingListener(adapter)
    }

    fun unregisterCatalogUpdateCallback(callback: Runnable) {
        val adapter = myDownloadingAdapters[callback] ?: return

        val info = (myCatalog as? HttpVirtualFile)?.fileInfo ?: return
        info.removeDownloadingListener(adapter)
    }

    fun triggerUpdateCatalog(project: Project) {
        CirJsonFileResolver.startFetchingHttpFileIfNeeded(myCatalog, project)
    }

    private class FileMatcher(val myEntry: CirJsonSchemaCatalogEntry) {

        private var myMatcher: PathMatcher? = null

        fun matches(filePath: Path): Boolean {
            if (myMatcher == null) {
                myMatcher = buildPathMatcher(myEntry.fileMasks)
            }

            return myMatcher!!.matches(filePath)
        }

        companion object {

            private fun buildPathMatcher(fileMasks: Collection<String>): PathMatcher {
                val refinedFileMasks = fileMasks.map { StringUtil.trimStart(it, "**/") }

                return if (refinedFileMasks.size == 1) {
                    FileSystems.getDefault().getPathMatcher("glob:${refinedFileMasks.first()}")
                } else if (refinedFileMasks.isNotEmpty()) {
                    FileSystems.getDefault().getPathMatcher("glob{${StringUtil.join(refinedFileMasks, ",")}}")
                } else {
                    PathMatcher { false }
                }
            }

        }

    }

    companion object {

        const val DEFAULT_CATALOG: String = "http://cirjson.org/api/catalog.cirjson"

        const val DEFAULT_CATALOG_HTTPS: String = "https://cirjson.org/api/catalog.cirjson"

        private const val NO_CACHE = "\$_\$_WS_NO_CACHE_\$_\$"

        private const val EMPTY: String = "\$_\$_WS_EMPTY_\$_\$"

        private val SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS =
                setOf("https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/")

        private fun isIgnoredAsHavingTooManyVariants(schemaUrl: String): Boolean {
            return SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS.any { schemaUrl.startsWith(it) }
        }

        private fun resolveSchemaFile(file: VirtualFile, catalogFile: VirtualFile, project: Project): String? {
            CirJsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile, project)

            val schemaCatalog = CirJsonCachedValues.getSchemaCatalog(catalogFile, project)
                    ?: return if (catalogFile is HttpVirtualFile) NO_CACHE else null

            val fileMatchers = ContainerUtil.map(schemaCatalog) { FileMatcher(it) }

            val fileRelativePathStr = getRelativePath(file, project)
            var url = findMatchedUrl(fileMatchers, fileRelativePathStr)

            if (url == null) {
                val fileName = file.name

                if (fileName != fileRelativePathStr) {
                    url = findMatchedUrl(fileMatchers, fileName)
                }
            }

            return url
        }

        private fun getRelativePath(file: VirtualFile, project: Project): String? {
            var basePath = project.basePath

            if (basePath != null) {
                basePath = StringUtil.trimEnd(basePath, VfsUtilCore.VFS_SEPARATOR_CHAR) + VfsUtilCore.VFS_SEPARATOR_CHAR
                val filePath = file.path

                if (filePath.startsWith(basePath)) {
                    return filePath.substring(basePath.length)
                }
            }

            val contentRoot = ReadAction.compute<VirtualFile?, Throwable> {
                if (project.isDisposed || !file.isValid) {
                    return@compute null
                }

                return@compute ProjectFileIndex.getInstance(project).getContentRootForFile(file, false)
            } ?: return null

            return VfsUtilCore.findRelativePath(contentRoot, file, VfsUtilCore.VFS_SEPARATOR_CHAR)
        }

        private fun findMatchedUrl(matchers: List<FileMatcher>, filePath: String?): String? {
            filePath ?: return null
            val path: Path

            try {
                path = Paths.get(filePath)
            } catch (_: InvalidPathException) {
                return null
            }

            for (matcher in matchers) {
                if (matcher.matches(path)) {
                    return matcher.myEntry.url
                }
            }

            return null
        }

    }

}