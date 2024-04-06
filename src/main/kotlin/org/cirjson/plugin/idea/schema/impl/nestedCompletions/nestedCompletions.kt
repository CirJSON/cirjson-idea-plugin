package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.openapi.project.Project
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
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
