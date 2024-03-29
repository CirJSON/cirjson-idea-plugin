package org.cirjson.plugin.idea.schema.impl

import java.util.stream.Collectors

class EnumObjectValueWrapper(val values: Map<String, Any>) {

    override fun toString(): String {
        return "[${values.map { "\"${it.key}\": ${it.value}" }.stream().collect(Collectors.joining(", "))}]"
    }

}