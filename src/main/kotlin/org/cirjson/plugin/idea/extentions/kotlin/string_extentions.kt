package org.cirjson.plugin.idea.extentions.kotlin

fun String.startsWithAny(vararg strings: String) = strings.any { this.startsWith(it) }

fun String.compareToWithIgnoreCare(other: String) = compareTo(other, true)