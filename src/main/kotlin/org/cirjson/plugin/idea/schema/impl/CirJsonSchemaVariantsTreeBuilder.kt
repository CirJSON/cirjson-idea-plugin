package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import java.util.stream.Collectors

object CirJsonSchemaVariantsTreeBuilder {

    fun buildTree(project: Project, schema: CirJsonSchemaObject, position: CirJsonPointerPosition,
            skipLastExpand: Boolean): CirJsonSchemaTreeNode {
        val root = CirJsonSchemaTreeNode(null, schema)
        val service = CirJsonSchemaService.get(project)
        expandChildSchema(root, schema, service)

        // set root's position since this children are just variants of root
        for (treeNode in root.children) {
            treeNode.position = position
        }

        val queue = ArrayDeque(root.children)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.any || node.nothing || node.position.empty || node.schema == null) {
                continue
            }

            val step = node.position

            if (!typeMatches(step.isObject(0), node.schema)) {
                node.nothingChild()
                continue
            }

            val pair = doSingleStep(step, node.schema, true)

            if (pair.first == ThreeState.NO) {
                node.nothingChild()
            } else if (pair.first == ThreeState.YES) {
                node.anyChild()
            } else {
                // process step results
                assert(pair.second != null)
                if (node.position.size > 1 || !skipLastExpand) {
                    expandChildSchema(node, pair.second!!, service)
                } else {
                    node.child = pair.second!!
                }
            }

            queue.addAll(node.children)
        }

        return root
    }

    private fun typeMatches(isObject: Boolean, schema: CirJsonSchemaObject): Boolean {
        val requiredType = if (isObject) CirJsonSchemaType._object else CirJsonSchemaType._array
        val type = schema.type

        if (type != null) {
            return requiredType == type
        }

        val typeVariants = schema.typeVariants

        if (typeVariants != null) {
            for (schemaType in typeVariants) {
                if (requiredType == schemaType) {
                    return true
                }
            }

            return false
        }

        return true
    }

    private fun expandChildSchema(node: CirJsonSchemaTreeNode, childSchema: CirJsonSchemaObject,
            service: CirJsonSchemaService) {
        if (interestingSchema(childSchema)) {
            node.createChildrenFromOperation(getOperation(service, childSchema))
        } else {
            node.child = childSchema
        }
    }

    private fun getOperation(service: CirJsonSchemaService, param: CirJsonSchemaObject): Operation {
        val expand = ProcessDefinitionsOperation(param, service)
        expand.doMap(HashSet())
        expand.doReduce()
        return expand
    }

    fun doSingleStep(step: CirJsonPointerPosition, parent: CirJsonSchemaObject,
            processAllBranches: Boolean): Pair<ThreeState, CirJsonSchemaObject?> {
        val name = step.firstName

        return if (name != null) {
            propertyStep(name, parent, processAllBranches)
        } else {
            val idx = step.firstIndex
            assert(idx >= 0)
            arrayOrNumericPropertyElementStep(idx, parent)
        }
    }

    private fun andGroups(g1: List<CirJsonSchemaObject>,
            g2: List<CirJsonSchemaObject>): MutableList<CirJsonSchemaObject> {
        val result = ArrayList<CirJsonSchemaObject>(g1.size * g2.size)

        for (s in g1) {
            result.addAll(andGroup(s, g2))
        }

        return result
    }

    private fun andGroup(obj: CirJsonSchemaObject, group: List<CirJsonSchemaObject>): MutableList<CirJsonSchemaObject> {
        val list = ArrayList<CirJsonSchemaObject>(group.size)

        for (s in group) {
            val schemaObject = CirJsonSchemaObject.merge(obj, s, s)

            if (schemaObject.isValidByExclusion) {
                list.add(schemaObject)
            }
        }

        return list
    }

    private fun interestingSchema(schema: CirJsonSchemaObject): Boolean {
        return schema.anyOf != null || schema.oneOf != null || schema.allOf != null || schema.ref != null
                || schema.ifThenElse != null
    }

    private fun propertyStep(name: String, parent: CirJsonSchemaObject,
            processAllBranches: Boolean): Pair<ThreeState, CirJsonSchemaObject?> {
        val child = parent.properties[name]

        if (child != null) {
            return Pair.create(ThreeState.UNSURE, child)
        }

        val schema = parent.getMatchingPatternPropertySchema(name)

        if (schema != null) {
            return Pair.create(ThreeState.UNSURE, schema)
        }

        val additionalPropertySchema = parent.additionalPropertiesSchema

        if (additionalPropertySchema != null) {
            return Pair.create(ThreeState.UNSURE, additionalPropertySchema)
        }

        if (processAllBranches) {
            val ifThenElseList = parent.ifThenElse

            if (ifThenElseList != null) {
                for (ifThenElse in ifThenElseList) {
                    // resolve inside V7 if-then-else conditionals

                    var childObject: CirJsonSchemaObject? = null

                    // NOTE: do not resolve inside 'if' itself - it is just a condition, but not an actual validation!
                    // only 'then' and 'else' branches provide actual validation sources, but not the 'if' branch

                    val then = ifThenElse.thenBranch

                    if (then != null) {
                        childObject = then.properties[name]

                        if (childObject != null) {
                            return Pair.create(ThreeState.UNSURE, childObject)
                        }
                    }

                    val elseBranch = ifThenElse.elseBranch

                    if (elseBranch != null) {
                        childObject = elseBranch.properties[name]

                        if (childObject != null) {
                            return Pair.create(ThreeState.UNSURE, childObject)
                        }
                    }
                }
            }
        }

        if (!parent.additionalPropertiesAllowed!!) {
            return Pair.create(ThreeState.NO, null)
        }

        // by default, additional properties are allowed
        return Pair.create(ThreeState.YES, null)
    }

    private fun arrayOrNumericPropertyElementStep(idx: Int,
            parent: CirJsonSchemaObject): Pair<ThreeState, CirJsonSchemaObject?> {
        val itemsSchema = parent.itemsSchema

        if (itemsSchema != null) {
            return Pair.create(ThreeState.UNSURE, itemsSchema)
        }

        val list = parent.itemsSchemaList

        if (list != null) {
            if (idx >= 0 && idx < list.size) {
                return Pair.create(ThreeState.UNSURE, list[idx])
            }
        }

        val keyAsString = idx.toString()

        if (parent.properties.containsKey(keyAsString)) {
            return Pair.create(ThreeState.UNSURE, parent.properties[keyAsString])
        }

        val matchingPatternPropertySchema = parent.getMatchingPatternPropertySchema(keyAsString)

        if (matchingPatternPropertySchema != null) {
            return Pair.create(ThreeState.UNSURE, matchingPatternPropertySchema)
        }

        val additionalItemsSchema = parent.additionalItemsSchema

        if (additionalItemsSchema != null) {
            return Pair.create(ThreeState.UNSURE, additionalItemsSchema)
        }

        if (!parent.additionalItemsAllowed!!) {
            return Pair.create(ThreeState.NO, null)
        }

        return Pair.create(ThreeState.YES, null)
    }

    internal abstract class Operation protected constructor(protected val mySourceNode: CirJsonSchemaObject) {

        val myAnyOfGroup = SmartList<CirJsonSchemaObject>()

        val myOneOfGroup = SmartList<MutableList<CirJsonSchemaObject>>()

        protected val myChildOperations = ArrayList<Operation>()

        var myState = SchemaResolveState.normal

        abstract fun map(visited: MutableSet<CirJsonSchemaObject>)

        abstract fun reduce()

        fun doMap(visited: MutableSet<CirJsonSchemaObject>) {
            map(visited)
            for (operation in myChildOperations) {
                operation.doMap(visited)
            }
        }

        fun doReduce() {
            if (SchemaResolveState.normal != myState) {
                myChildOperations.clear()
                myAnyOfGroup.clear()
                myOneOfGroup.clear()
                return
            }

            // lets do that to make the returned object smaller
            myAnyOfGroup.forEach { clearVariants(it) }
            myOneOfGroup.forEach { list -> list.forEach { clearVariants(it) } }

            for (myChildOperation in myChildOperations) {
                myChildOperation.doReduce()
            }

            reduce()
            myChildOperations.clear()
        }

        protected fun createExpandOperation(schema: CirJsonSchemaObject, service: CirJsonSchemaService): Operation? {
            val forConflict = createOperationForConflict(schema, service)

            if (forConflict != null) {
                return forConflict
            }

            return if (schema.anyOf != null) {
                AnyOfOperation(schema, service)
            } else if (schema.oneOf != null) {
                OneOfOperation(schema, service)
            } else if (schema.allOf != null) {
                AllOfOperation(schema, service)
            } else {
                null
            }
        }

        companion object {

            private fun clearVariants(obj: CirJsonSchemaObject) {
                obj.allOf = null
                obj.anyOf = null
                obj.oneOf = null
            }

            private fun createOperationForConflict(schema: CirJsonSchemaObject,
                    service: CirJsonSchemaService): Operation? {
                // in case of several incompatible operations, choose the most permissive one
                val anyOf = schema.anyOf
                val oneOf = schema.oneOf
                val allOf = schema.allOf

                if (anyOf != null && (oneOf != null || allOf != null)) {
                    return object : AnyOfOperation(schema, service) {

                        init {
                            myState = SchemaResolveState.conflict
                        }

                    }
                } else if (oneOf != null && allOf != null) {
                    return object : OneOfOperation(schema, service) {

                        init {
                            myState = SchemaResolveState.conflict
                        }

                    }
                }

                return null
            }

            @JvmStatic
            protected fun mergeOneOf(op: Operation): MutableList<CirJsonSchemaObject> {
                return op.myOneOfGroup.stream().flatMap { it.stream() }.collect(Collectors.toList())
            }

        }

    }

    private class ProcessDefinitionsOperation(sourceNode: CirJsonSchemaObject,
            private val myService: CirJsonSchemaService) : Operation(sourceNode) {

        override fun map(visited: MutableSet<CirJsonSchemaObject>) {
            var current = mySourceNode

            while (!StringUtil.isEmptyOrSpaces(current.ref)) {
                val definition = current.resolveRefSchema(myService)

                if (definition == null) {
                    myState = SchemaResolveState.brokenDefinition
                    return
                }

                if (!visited.add(definition)) {
                    break
                }

                current = CirJsonSchemaObject.merge(current, definition, current)
            }

            val expandOperation = createExpandOperation(current, myService)

            if (expandOperation != null) {
                myChildOperations.add(expandOperation)
            } else {
                myAnyOfGroup.add(current)
            }
        }

        override fun reduce() {
            if (myChildOperations.isEmpty()) {
                return
            }

            assert(myChildOperations.size == 1)
            val operation = myChildOperations[0]
            myAnyOfGroup.addAll(operation.myAnyOfGroup)
            myOneOfGroup.addAll(operation.myOneOfGroup)
        }

    }

    private class AllOfOperation(sourceNode: CirJsonSchemaObject, private val myService: CirJsonSchemaService) :
            Operation(sourceNode) {

        override fun map(visited: MutableSet<CirJsonSchemaObject>) {
            val allOf = mySourceNode.allOf
            assert(allOf != null)
            myChildOperations.addAll(ContainerUtil.map(allOf!!) { sourceNode ->
                ProcessDefinitionsOperation(sourceNode, myService)
            })
        }

        override fun reduce() {
            myAnyOfGroup.add(mySourceNode)

            for (op in myChildOperations) {
                if (op.myState != SchemaResolveState.normal) {
                    continue
                }

                val mergedAny = andGroups(op.myAnyOfGroup, myAnyOfGroup)
                val mergedExclusive = ArrayList<MutableList<CirJsonSchemaObject>>(
                        op.myAnyOfGroup.size * maxSize(myOneOfGroup) + myAnyOfGroup.size * maxSize(op.myOneOfGroup) +
                                maxSize(myOneOfGroup) * maxSize(op.myOneOfGroup))

                for (objects in myOneOfGroup) {
                    mergedExclusive.add(andGroups(op.myAnyOfGroup, objects))
                }

                for (objects in op.myOneOfGroup) {
                    mergedExclusive.add(andGroups(objects, myAnyOfGroup))
                }

                for (group in op.myOneOfGroup) {
                    for (otherGroup in myOneOfGroup) {
                        mergedExclusive.add(andGroups(group, otherGroup))
                    }
                }

                myAnyOfGroup.clear()
                myOneOfGroup.clear()
                myAnyOfGroup.addAll(mergedAny)
                myOneOfGroup.addAll(mergedExclusive)
            }
        }

        companion object {

            private fun <T> maxSize(items: List<List<T>>): Int {
                if (items.isEmpty()) {
                    return 0
                }

                var maxSize = -1

                for (item in items) {
                    val size = item.size

                    if (maxSize < size) {
                        maxSize = size
                    }
                }

                return maxSize
            }

        }

    }

    private open class OneOfOperation(sourceNode: CirJsonSchemaObject, private val myService: CirJsonSchemaService) :
            Operation(sourceNode) {

        override fun map(visited: MutableSet<CirJsonSchemaObject>) {
            val oneOf = mySourceNode.oneOf
            assert(oneOf != null)
            myChildOperations.addAll(ContainerUtil.map(oneOf!!) { sourceNode ->
                ProcessDefinitionsOperation(sourceNode, myService)
            })
        }

        override fun reduce() {
            val oneOf = SmartList<CirJsonSchemaObject>()

            for (op in myChildOperations) {
                if (op.myState != SchemaResolveState.normal) {
                    continue
                }

                oneOf.addAll(andGroup(mySourceNode, op.myAnyOfGroup))
                oneOf.addAll(andGroup(mySourceNode, mergeOneOf(op)))
            }

            // here it is not a mistake - all children of this node come to oneOf group
            myOneOfGroup.add(oneOf)
        }

    }

    private open class AnyOfOperation(sourceNode: CirJsonSchemaObject, private val myService: CirJsonSchemaService) :
            Operation(sourceNode) {

        override fun map(visited: MutableSet<CirJsonSchemaObject>) {
            val anyOf = mySourceNode.anyOf
            assert(anyOf != null)
            myChildOperations.addAll(ContainerUtil.map(anyOf!!) { sourceNode ->
                ProcessDefinitionsOperation(sourceNode, myService)
            })
        }

        override fun reduce() {
            for (op in myChildOperations) {
                if (op.myState != SchemaResolveState.normal) {
                    continue
                }

                myAnyOfGroup.addAll(andGroup(mySourceNode, op.myAnyOfGroup))

                for (group in op.myOneOfGroup) {
                    myOneOfGroup.add(andGroup(mySourceNode, group))
                }
            }
        }

    }

    class SchemaUrlSplitter(ref: String) {

        val schemaId: String?

        val relativePath: String

        init {
            if (CirJsonPointerUtil.isSelfReference(ref)) {
                schemaId = null
                relativePath = ""
            } else if (!ref.startsWith("#/")) {
                val idx = ref.indexOf("#/")

                if (idx == -1) {
                    schemaId = if (ref.endsWith("#")) ref.substring(0, ref.length - 1) else ref
                    relativePath = ""
                } else {
                    schemaId = ref.substring(0, idx)
                    relativePath = ref.substring(idx)
                }
            } else {
                schemaId = null
                relativePath = ref
            }
        }

        val isAbsolute: Boolean
            get() = schemaId != null

    }

}