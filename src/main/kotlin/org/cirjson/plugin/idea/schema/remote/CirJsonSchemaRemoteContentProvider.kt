package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.DefaultRemoteContentProvider
import com.intellij.util.Url
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.HttpRequests
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.extentions.kotlin.startsWithAny
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.nio.file.Files
import java.time.Duration

class CirJsonSchemaRemoteContentProvider : DefaultRemoteContentProvider() {

    private var myLastUpdateTime = 0L

    override fun getDefaultConnectionTimeout(): Int {
        return DEFAULT_CONNECT_TIMEOUT
    }

    override fun canProvideContent(url: Url): Boolean {
        val externalForm = url.toExternalForm()
        return externalForm.startsWithAny(STORE_URL_PREFIX_HTTP, STORE_URL_PREFIX_HTTPS, SCHEMA_URL_PREFIX,
                SCHEMA_URL_PREFIX_HTTPS) || externalForm.endsWith(".cirjson")
    }

    override fun saveAdditionalData(request: HttpRequests.Request, file: File) {
        val connection = request.connection

        if (saveTag(file, connection, ETAG_HEADER)) {
            return
        }

        saveTag(file, connection, LAST_MODIFIED_HEADER)
    }

    override fun adjustFileType(type: FileType?, url: Url): FileType? {
        if (type == null) {
            val fullUrl = url.toExternalForm()

            if (fullUrl.startsWithAny(SCHEMA_URL_PREFIX, SCHEMA_URL_PREFIX_HTTPS)) {
                return CirJsonFileType.INSTANCE
            }
        }

        return super.adjustFileType(type, url)
    }

    override fun isUpToDate(url: Url, local: VirtualFile): Boolean {
        val now = System.currentTimeMillis()

        // don't update more frequently than once in 4 hours
        if (now - myLastUpdateTime < UPDATE_DELAY) {
            return true
        }

        myLastUpdateTime = now
        val path = local.path

        if (now - File(path).lastModified() < UPDATE_DELAY) {
            return true
        }

        if (checkUpToDate(url, path, ETAG_HEADER)) {
            return true
        }

        if (checkUpToDate(url, path, LAST_MODIFIED_HEADER)) {
            return true
        }

        return false
    }

    private fun checkUpToDate(url: Url, path: String, header: String): Boolean {
        val file = File("$path.$header")

        return try {
            isUpToDate(url, file, header)
        } catch (_: IOException) {
            File(path).setLastModified(System.currentTimeMillis())
            true
        }
    }

    @Throws(IOException::class)
    private fun isUpToDate(url: Url, file: File, header: String): Boolean {
        val strings = if (file.exists()) Files.readAllLines(file.toPath()) else emptyList()

        val currentTag = strings.firstOrNull() ?: return false

        val remoteTag = connect(url, HttpRequests.head(url.toExternalForm())) { it.connection.getHeaderField(header) }

        return currentTag == remoteTag
    }

    companion object {

        private const val DEFAULT_CONNECT_TIMEOUT: Int = 10000

        private val UPDATE_DELAY: Long = Duration.ofHours(4).toMillis()

        const val STORE_URL_PREFIX_HTTP: String = "http://cirjson.org/store"

        const val STORE_URL_PREFIX_HTTPS: String = "https://cirjson.org/store"

        private const val SCHEMA_URL_PREFIX: String = "http://cirjson.org/schema/"

        private const val SCHEMA_URL_PREFIX_HTTPS: String = "https://cirjson.org/schema/"

        private const val ETAG_HEADER: String = "ETag"

        private const val LAST_MODIFIED_HEADER: String = "Last-Modified"

        private fun saveTag(file: File, connection: URLConnection, header: String): Boolean {
            val tag = connection.getHeaderField(header) ?: return false

            var path = file.absolutePath

            if (!path.endsWith(".cirjson")) {
                path += ".cirjson"
            }

            val tagFile = File("$path.$header")
            saveToFile(tagFile, tag)
            return true
        }

        private fun saveToFile(tagFile: File, headerValue: String) {
            if (!tagFile.exists()) {
                if (!tagFile.createNewFile()) {
                    return
                }
            }

            Files.write(tagFile.toPath(), ContainerUtil.createMaybeSingletonList(headerValue))
        }

    }

}