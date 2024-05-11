package org.cirjson.plugin.idea.schema.impl.validations

import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.kotlin.trueOrNull
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

class NotValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        val result = consumer.resolve(schema.not!!)

        // if 'not' uses reference to owning schema back -> do not check, seems it does not make any sense
        if (result.mySchemas.any { schema == it } || result.myExcludingSchemas.flatten().any { schema == it }) {
            return
        }

        val checker = consumer.checkByMatchResult(propValue, result, options.withForceStrict())

        if (checker?.isValid.trueOrNull()) {
            consumer.error(CirJsonBundle.message("schema.validation.against.not"), propValue.delegate,
                    CirJsonErrorPriority.NOT_SCHEMA)
        }
    }

    companion object {

        val INSTANCE = NotValidation()

    }

}