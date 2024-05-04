package org.cirjson.plugin.idea.pointer

import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonPointerResolver(private val myRoot: CirJsonValue, private val myPointer: String) {

    fun resolve(): CirJsonValue? {
        var root: CirJsonValue? = myRoot
        val steps = CirJsonPointerPosition.parsePointer(myPointer).steps

        for (step in steps) {
            val name = step.name

            if (name != null) {
                if (root !is CirJsonObject) {
                    return null
                }

                val property = root.findProperty(name)
                root = property?.value
            } else {
                val idx = step.idx

                if (idx < 0) {
                    return null
                }

                when (root) {
                    is CirJsonObject -> {
                        val property = root.findProperty(idx.toString()) ?: return null
                        root = property.value
                    }

                    is CirJsonArray -> {
                        val list = root.valueList

                        if (idx >= list.size) {
                            return null
                        }

                        root = list[idx]
                    }

                    else -> return null
                }
            }
        }

        return root
    }

}