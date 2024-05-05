package org.cirjson.plugin.idea.schema.impl.validations

import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

class ArrayValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        checkArray(propValue, schema, consumer, options)
    }

    companion object {

        val INSTANCE = ArrayValidation()

        private fun checkArray(value: CirJsonValueAdapter, schema: CirJsonSchemaObject, consumer: CirJsonValidationHost,
                options: CirJsonComplianceCheckerOptions) {
            val asArray = value.asArray ?: return
            val elements = asArray.elements
            checkArrayItems(value, elements, schema, consumer, options)
        }

        private fun checkArrayItems(array: CirJsonValueAdapter, list: List<CirJsonValueAdapter>,
                schema: CirJsonSchemaObject, consumer: CirJsonValidationHost,
                options: CirJsonComplianceCheckerOptions) {
            TODO()
        }

    }

}