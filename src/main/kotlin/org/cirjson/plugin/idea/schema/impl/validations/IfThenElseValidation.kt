package org.cirjson.plugin.idea.schema.impl.validations

import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

class IfThenElseValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        val ifThenElseList = schema.ifThenElse!!

        for (ifThenElse in ifThenElseList) {
            val result = consumer.resolve(ifThenElse.condition)

            if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) {
                return
            }

            val checker = consumer.checkByMatchResult(propValue, result, options.withForceStrict()) ?: continue

            if (checker.isValid) {
                val then = ifThenElse.thenBranch ?: continue
                consumer.checkObjectBySchemaRecordErrors(then, propValue)
            } else {
                val elseBranch = ifThenElse.elseBranch ?: continue
                consumer.checkObjectBySchemaRecordErrors(elseBranch, propValue)
            }
        }
    }

    companion object {

        val INSTANCE = IfThenElseValidation()

    }

}