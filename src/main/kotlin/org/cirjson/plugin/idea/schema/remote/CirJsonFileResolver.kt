package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.util.UriUtil
import com.intellij.util.Urls
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import java.io.File

object CirJsonFileResolver {

    private const val MOCK_URL = "mock://"

    private const val TEMP_URL = "temp://"

    private val DOWNLOAD_STARTED = Key.create<Boolean>("DOWNLOAD_STARTED")

    fun isRemoteEnabled(project: Project): Boolean {
        return !ApplicationManager.getApplication().isUnitTestMode
                && CirJsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled
    }

    fun urlToFile(urlString: String): VirtualFile? {
        if (urlString.startsWith(CirJsonSchemaObject.TEMP_URL)) {
            return TempFileSystem.getInstance().findFileByPath(urlString.substring(CirJsonSchemaObject.TEMP_URL.length))
        }

        return null
    }

    fun resolveSchemaByReference(currentFile: VirtualFile?, schemaUrl: String?): VirtualFile? {
        var realSchemaUrl: String? = schemaUrl ?: return null

        val isHttpPath = isHttpPath(realSchemaUrl!!)

        if (!isHttpPath && currentFile is HttpVirtualFile) {
            val url = StringUtil.trimEnd(currentFile.url, "/")
            val lastSlash = url.lastIndexOf('/')
            assert(lastSlash != -1)
            realSchemaUrl = "${url.substring(0, lastSlash)}/$realSchemaUrl"
        } else if (StringUtil.startsWithChar(schemaUrl, '.')) {
            val parent = currentFile?.parent
            realSchemaUrl = if (parent == null) {
                null
            } else if (parent.url.startsWith(CirJsonSchemaObject.TEMP_URL)) {
                "temp:///${parent.path}/$realSchemaUrl"
            } else {
                VfsUtilCore.pathToUrl(parent.path + File.separator + realSchemaUrl)
            }
        }

        if (realSchemaUrl != null) {
            val virtualFile = urlToFile(realSchemaUrl)

            if (virtualFile is HttpVirtualFile) {
                val url = virtualFile.url
                val parse = Urls.parse(url, false)

                if (parse == null || StringUtil.isEmpty(parse.authority) || StringUtil.isEmpty(parse.path)) {
                    return null
                }
            }

            if (virtualFile != null) {
                return virtualFile
            }
        }

        return null
    }

    fun startFetchingHttpFileIfNeeded(path: VirtualFile?, project: Project) {
        if (path !is HttpVirtualFile) {
            return
        }

        if (!isRemoteEnabled(project)) {
            return
        }

        val info = path.fileInfo

        if (info == null || info.state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
            if (path.getUserData(DOWNLOAD_STARTED) != true) {
                path.putUserData(DOWNLOAD_STARTED, true)
                path.refresh(true, false)
            }
        }
    }

    fun isHttpPath(schemaFieldText: String): Boolean {
        val couple = UriUtil.splitScheme(schemaFieldText)
        return couple.first.startsWith("http")
    }

    fun isTempOrMockUrl(path: String): Boolean {
        return path.startsWith(CirJsonSchemaObject.TEMP_URL) || path.startsWith(CirJsonSchemaObject.MOCK_URL)
    }

    fun isSchemaUrl(url: String?): Boolean {
        return url != null && url.startsWith("http://cirjson.org/")
                && (url.endsWith("/schema") || url.endsWith("/schema#"))
    }

    fun isAbsoluteUrl(path: String): Boolean {
        return isHttpPath(path) || path.startsWith(TEMP_URL) || FileUtil.toSystemIndependentName(path)
                .startsWith(JarFileSystem.PROTOCOL_PREFIX)
    }

}