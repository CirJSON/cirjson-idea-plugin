package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.openapi.editor.Document

internal fun documentChangeAt(offset: Int, task: Document.(Int) -> Unit) = DocumentChange(offset, task)

/** This utility will run tasks from right to left, which makes comprehending mutations easier. */
internal fun <T : Comparable<T>> Document.applyChangesOrdered(vararg tasks: DocumentChange<T>) {
    tasks.sortByDescending { it.key }

    for (task in tasks) {
        task.sideEffect(this, task.key)
    }
}