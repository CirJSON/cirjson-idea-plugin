package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import java.util.*

class CirJsonSchemaResolver(private val myProject: Project, private val mySchema: CirJsonSchemaObject,
        private val myPosition: CirJsonPointerPosition) {

    constructor(myProject: Project, mySchema: CirJsonSchemaObject) : this(myProject, mySchema, CirJsonPointerPosition())

    fun detailedResolve(): MatchResult {
        val node = CirJsonSchemaVariantsTreeBuilder.buildTree(myProject, mySchema, myPosition, false)
        return MatchResult.create(node)
    }

    fun resolve(): Collection<CirJsonSchemaObject> {
        val result = detailedResolve()
        val list = LinkedList(result.mySchemas)

        for (myExcludingSchema in result.myExcludingSchemas) {
            list.addAll(myExcludingSchema)
        }

        return list
    }

}