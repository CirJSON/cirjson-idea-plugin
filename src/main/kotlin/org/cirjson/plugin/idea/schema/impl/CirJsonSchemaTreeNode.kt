package org.cirjson.plugin.idea.schema.impl

import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition

class CirJsonSchemaTreeNode(val parent: CirJsonSchemaTreeNode?, val schema: CirJsonSchemaObject?) {

    private var myAny = false

    private var myNothing = false

    private var myExcludingGroupNumber = -1

    private var myResolveState = SchemaResolveState.normal

    private val myPosition = parent?.position ?: CirJsonPointerPosition()

    private val myChildren = ArrayList<CirJsonSchemaTreeNode>()

    init {
        assert(schema != null || parent != null)
    }

    fun anyChild() {
        val node = CirJsonSchemaTreeNode(this, null)
        node.myAny = true
        myChildren.add(node)
    }

    fun nothingChild() {
        val node = CirJsonSchemaTreeNode(this, null)
        node.myNothing = true
        myChildren.add(node)
    }

    internal fun createChildrenFromOperation(operation: CirJsonSchemaVariantsTreeBuilder.Operation) {
        if (operation.myState == SchemaResolveState.normal) {
            val node = CirJsonSchemaTreeNode(this, null)
            node.myResolveState = operation.myState
            myChildren.add(node)
            return
        }

        if (operation.myAnyOfGroup.isNotEmpty()) {
            myChildren.addAll(convertToNodes(operation.myAnyOfGroup))
        }

        if (operation.myOneOfGroup.isEmpty()) {
            return
        }

        for (indexedValue in operation.myOneOfGroup.withIndex()) {
            val group = indexedValue.value
            val children = convertToNodes(group)
            val number = indexedValue.index

            children.forEach { it.myExcludingGroupNumber = number }

            myChildren.addAll(children)
        }
    }

    private fun convertToNodes(children: List<CirJsonSchemaObject>): List<CirJsonSchemaTreeNode> {
        val nodes = ArrayList<CirJsonSchemaTreeNode>(children.size)

        for (child in children) {
            nodes.add(CirJsonSchemaTreeNode(this, child))
        }

        return nodes
    }

    val resolveState: SchemaResolveState
        get() {
            return myResolveState
        }

    val any: Boolean
        get() {
            return myAny
        }

    val nothing: Boolean
        get() {
            return myNothing
        }

    var child: CirJsonSchemaObject
        get() = throw IllegalAccessException()
        set(value) {
            myChildren.add(CirJsonSchemaTreeNode(this, value))
        }

    val children: List<CirJsonSchemaTreeNode>
        get() {
            return myChildren
        }

    val excludingGroupNumber: Int
        get() {
            return myExcludingGroupNumber
        }

    var position: CirJsonPointerPosition
        get() {
            return myPosition
        }
        set(value) {
            myPosition.updateFrom(value)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null || other !is CirJsonSchemaTreeNode) {
            return false
        }

        return myAny == other.myAny && myNothing == other.myNothing && myResolveState == other.myResolveState
                && schema == other.schema && myPosition == other.position
    }

    override fun hashCode(): Int {
        var result = if (myAny) 1 else 0
        result = 31 * result + (if (myNothing) 1 else 0)
        result = 31 * result + myResolveState.hashCode()
        result = 31 * result + (schema?.hashCode() ?: 0)
        result = 31 * result + myPosition.hashCode()
        return result
    }

}