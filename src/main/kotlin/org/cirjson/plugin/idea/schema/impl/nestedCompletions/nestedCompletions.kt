package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaResolver

/**
 * Collects nested completions for a JSON schema object.
 * If `[node] == null`, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
internal fun CirJsonSchemaObject.collectNestedCompletions(project: Project, node: NestedCompletionsNode?,
        completionPath: SchemaPath?, collector: (path: SchemaPath?, schema: CirJsonSchemaObject) -> Unit) {
    collector(completionPath, this) // Breadth first

    node?.children?.filterIsInstance<ChildNode.OpenNode>()?.forEach { (name, childNode) ->
        for (subSchema in findSubSchemasByName(project, name)) {
            subSchema.collectNestedCompletions(project, childNode, completionPath / name, collector)
        }
    }
}

private fun CirJsonSchemaObject.findSubSchemasByName(project: Project, name: String): Iterable<CirJsonSchemaObject> {
    return CirJsonSchemaResolver(project, this, CirJsonPointerPosition().apply { addFollowingStep(name) }).resolve()
}

internal fun CirJsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement {
    return path?.let { findContainingObjectAdapter(start)?.findChildBy(path.accessor(), 0)?.delegate } ?: start
}

private fun CirJsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement): CirJsonObjectValueAdapter? {
    return start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }
}

internal fun CirJsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): CirJsonValueAdapter? {
    return if (offset > path.lastIndex) {
        this
    } else {
        childByName(path[offset])?.asObject?.findChildBy(path, offset + 1)
    }
}

private fun CirJsonObjectValueAdapter.childByName(name: String): CirJsonValueAdapter? {
    return propertyList.firstOrNull { it.name == name }?.values?.firstOrNull()
}


