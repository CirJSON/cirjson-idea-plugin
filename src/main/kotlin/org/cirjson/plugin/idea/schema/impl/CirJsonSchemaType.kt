package org.cirjson.plugin.idea.schema.impl

import java.math.BigInteger

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

    companion object {

        fun isInteger(text: String): Boolean {
            return getIntegerValue(text) != null
        }

        fun getIntegerValue(text: String): Number? {
            return try {
                text.toInt()
            } catch (_: NumberFormatException) {
                try {
                    BigInteger.valueOf(text.toLong())
                } catch (_: NumberFormatException) {
                    null
                }
            }
        }

    }

}