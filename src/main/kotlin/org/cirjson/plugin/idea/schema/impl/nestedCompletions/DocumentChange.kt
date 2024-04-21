package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.openapi.editor.Document

internal class DocumentChange<T : Comparable<T>>(val key: T, val sideEffect: Document.(T) -> Unit)