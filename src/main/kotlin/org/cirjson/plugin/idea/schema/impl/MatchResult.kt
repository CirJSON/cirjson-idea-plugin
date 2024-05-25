package org.cirjson.plugin.idea.schema.impl

import com.intellij.util.Processor
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.*
import kotlin.collections.ArrayDeque

class MatchResult private constructor(schemas: List<CirJsonSchemaObject>,
        excludingSchemas: List<Collection<CirJsonSchemaObject>>) {

    val mySchemas = Collections.unmodifiableList(schemas)

    val myExcludingSchemas = Collections.unmodifiableList(excludingSchemas)

    companion object {

        fun create(root: CirJsonSchemaTreeNode): MatchResult {
            val schemas = ArrayList<CirJsonSchemaObject>()
            val oneOfGroups = Int2ObjectOpenHashMap<MutableList<CirJsonSchemaObject>>()

            iterateTree(root) { node ->
                if (node.any) {
                    return@iterateTree true
                }

                val groupNumber = node.excludingGroupNumber

                if (groupNumber < 0) {
                    schemas.add(node.schema!!)
                } else {
                    oneOfGroups.computeIfAbsent(groupNumber,
                            Int2ObjectFunction<MutableList<CirJsonSchemaObject>> { ArrayList() }).add(node.schema!!)
                }

                return@iterateTree true
            }

            val result: List<Collection<CirJsonSchemaObject>> = if (oneOfGroups.isEmpty()) {
                emptyList()
            } else {
                ArrayList(oneOfGroups.values)
            }

            return MatchResult(schemas, result)
        }

        fun iterateTree(root: CirJsonSchemaTreeNode, processor: Processor<in CirJsonSchemaTreeNode>) {
            val queue = ArrayDeque(root.children)

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()

                if (node.children.isEmpty()) {
                    if (!node.nothing && node.resolveState == SchemaResolveState.normal && !processor.process(node)) {
                        break
                    }
                } else {
                    queue.addAll(node.children)
                }
            }
        }

    }

}