package org.cirjson.plugin.idea.extentions.kotlin

fun String.startsWithAny(vararg strings: String) = strings.any { this.startsWith(it) }