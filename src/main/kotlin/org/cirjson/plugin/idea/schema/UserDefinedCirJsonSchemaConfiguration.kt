package org.cirjson.plugin.idea.schema

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.xmlb.annotations.Tag
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver

@Tag("SchemaInfo")
class UserDefinedCirJsonSchemaConfiguration {

    val patterns = SmartList<Item>()

    var isIgnoredFile = false

    class Item(path: String?, val mappingKind: CirJsonMappingKind) {

        val path = path?.let { neutralizePath(it) }

        val pathParts: Array<String>
            get() {
                return pathToParts(path!!)
            }

        companion object {

            private fun preserveSlashes(path: String): Boolean {
                return StringUtil.startsWith(path, "http:") || StringUtil.startsWith(path, "https:")
                        || CirJsonFileResolver.isTempOrMockUrl(path)
            }

            fun neutralizePath(path: String): String {
                if (preserveSlashes(path)) {
                    return path
                }

                return StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(path), "/")
            }

        }

    }

    companion object {

        private fun pathToPartsList(path: String): List<String> {
            return StringUtil.split(path, "/").filter { it != "." }
        }

        private fun pathToParts(path: String): Array<String> {
            return pathToPartsList(path).toTypedArray()
        }

    }

}