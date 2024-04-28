package org.cirjson.plugin.idea.schema

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil

object CirJsonPointerUtil {

    fun escapeForCirJsonPointer(name: String): String {
        if (name.isBlank()) {
            return URLUtil.encodeURIComponent(name)
        }

        return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1")
    }

    fun unescapeCirJsonPointerPart(part: String): String {
        val realPart = URLUtil.unescapePercentSequences(part)
        return StringUtil.replace(StringUtil.replace(realPart, "~0", "~"), "~1", "/")
    }

    fun isSelfReference(ref: String?): Boolean {
        return ref == "#" || ref == "#/" || StringUtil.isEmpty(ref)
    }

    fun split(pointer: String): List<String> {
        return StringUtil.split(pointer, "/", true, false)
    }

    fun normalizeSlashes(ref: String): String {
        return StringUtil.trimStart(ref.replace('\\', '/'), "/")
    }

    fun normalizeId(id: String): String {
        var realId = id
        realId = if (realId.endsWith("#")) realId.substring(0, realId.length - 1) else realId
        return if (realId.startsWith("#")) realId.substring(1) else realId
    }

}