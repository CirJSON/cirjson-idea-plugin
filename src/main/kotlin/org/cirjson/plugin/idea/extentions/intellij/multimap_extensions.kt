package org.cirjson.plugin.idea.extentions.intellij

import com.intellij.util.containers.MultiMap

operator fun <K, V> MultiMap<K, V>.set(key: K, value: V) {
    putValue(key, value)
}