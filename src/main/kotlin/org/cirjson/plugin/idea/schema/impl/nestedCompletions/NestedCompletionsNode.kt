package org.cirjson.plugin.idea.schema.impl.nestedCompletions

/**
 * Represents a tree structure of how completions can be nested through a schema.
 *
 * If you request completions in a configuration node that has a corresponding
 * [org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject] as well as a corresponding [NestedCompletionsNode], you
 * will see completions for the entire subtree of the [NestedCompletionsNode]. (subtrees are not expanded below
 * [ChildNode.Isolated] nodes)
 *
 * See tests for details
 */
class NestedCompletionsNode(val children: List<ChildNode>) {

    internal fun merge(other: NestedCompletionsNode): NestedCompletionsNode {
        return NestedCompletionsNode(children + other.children)
    }

}