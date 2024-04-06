package org.cirjson.plugin.idea.schema.impl.nestedCompletions

sealed interface ChildNode {

    val node: NestedCompletionsNode

    sealed interface NamedChildNode : ChildNode {

        val name: String

    }

    sealed class Isolated : ChildNode {

        data class RegexNode(val regex: Regex, override val node: NestedCompletionsNode) : Isolated()

        data class NamedNode(override val name: String, override val node: NestedCompletionsNode) : Isolated(),
                NamedChildNode

    }

    data class OpenNode(override val name: String, override val node: NestedCompletionsNode) : ChildNode, NamedChildNode

}