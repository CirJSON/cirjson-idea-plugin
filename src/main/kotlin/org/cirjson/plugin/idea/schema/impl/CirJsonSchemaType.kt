package org.cirjson.plugin.idea.schema.impl

enum class CirJsonSchemaType {

    _string,

    _number,

    _integer,

    _object,

    _array,

    _boolean,

    _null,

    _any,

    _string_number;

    val realName: String = name.substring(1)

    val description: String
        get() = if (this == _any) "*" else realName

}