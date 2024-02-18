package org.cirjson.plugin.idea.schema

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil

object CirJsonPointerUtil {

    fun escapeFromCirJsonPointer(name: String): String {
        if (name.isBlank()) {
            return URLUtil.encodeURIComponent(name)
        }

        return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1")
    }

}