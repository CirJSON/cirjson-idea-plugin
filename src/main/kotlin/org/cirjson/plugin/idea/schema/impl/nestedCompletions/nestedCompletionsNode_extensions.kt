package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition

internal fun NestedCompletionsNode?.navigate(cirJsonPointer: CirJsonPointerPosition): NestedCompletionsNode? {
    return this?.navigate(0, cirJsonPointer.toPathItems())
}

private fun CirJsonPointerPosition.toPathItems(): List<String> {
    return toCirJsonPointer().takeIf { it != "/" }?.drop(1)?.split("/") ?: emptyList()
}

private fun NestedCompletionsNode.navigate(index: Int, steps: List<String>): NestedCompletionsNode? {
    return if (index !in steps.indices) {
        this
    } else {
        children.firstOrNull { it.matches(steps[index]) }?.node?.navigate(index + 1, steps)
    }
}

private fun ChildNode.matches(name: String): Boolean {
    return when (this) {
        is ChildNode.Isolated.RegexNode -> regex.matches(name)
        is ChildNode.NamedChildNode -> name == this.name
    }
}

internal operator fun SchemaPath?.div(name: String): SchemaPath {
    return SchemaPath(name, this)
}