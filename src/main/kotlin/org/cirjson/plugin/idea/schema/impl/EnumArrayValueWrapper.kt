package org.cirjson.plugin.idea.schema.impl

import java.util.stream.Collectors

class EnumArrayValueWrapper(val values: Array<Any>) {

    override fun toString(): String {
        return "[${values.map { it.toString() }.stream().collect(Collectors.joining(", "))}]"
    }

}