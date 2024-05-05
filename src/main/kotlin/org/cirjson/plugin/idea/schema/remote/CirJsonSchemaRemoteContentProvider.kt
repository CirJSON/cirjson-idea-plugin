package org.cirjson.plugin.idea.schema.remote

import com.intellij.openapi.vfs.impl.http.DefaultRemoteContentProvider
import java.time.Duration

// TODO: the provider
class CirJsonSchemaRemoteContentProvider : DefaultRemoteContentProvider() {

    companion object {

        private const val DEFAULT_CONNECT_TIMEOUT: Int = 10000

        private val UPDATE_DELAY: Long = Duration.ofHours(4).toMillis()

        const val STORE_URL_PREFIX_HTTP: String = "http://cirjson.org/store"

        const val STORE_URL_PREFIX_HTTPS: String = "https://cirjson.org/store"

        private const val SCHEMA_URL_PREFIX: String = "http://cirjson.org/schema/"

        private const val SCHEMA_URL_PREFIX_HTTPS: String = "https://cirjson.org/schema/"

        private const val ETAG_HEADER: String = "ETag"

        private const val LAST_MODIFIED_HEADER: String = "Last-Modified"

    }

}