package org.cirjson.plugin.idea.schema.impl.validations

import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaAnnotatorChecker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

class TypeValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        val otherType = CirJsonSchemaAnnotatorChecker.getMatchingSchemaType(schema, schemaType!!)

        if (otherType != null && otherType != schemaType && otherType != propValue.getAlternateType(schemaType)) {
            consumer.typeError(propValue.delegate, propValue.getAlternateType(schemaType),
                    *CirJsonSchemaAnnotatorChecker.getExpectedTypes(setOf(schema)))
        }
    }

    companion object {

        val INSTANCE: TypeValidation = TypeValidation()

    }

}