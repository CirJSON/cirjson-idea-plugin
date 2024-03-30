package org.cirjson.plugin.idea.extentions.kotlin

import com.intellij.openapi.util.Pair


fun <A, B> kotlin.Pair<A, B>.toIntelliJPair(): Pair<A, B> {
    return Pair.create(this.first, this.second)
}