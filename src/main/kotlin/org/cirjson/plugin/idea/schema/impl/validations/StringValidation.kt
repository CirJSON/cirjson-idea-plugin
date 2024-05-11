package org.cirjson.plugin.idea.schema.impl.validations

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonComplianceCheckerOptions
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaAnnotatorChecker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

class StringValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        checkString(propValue.delegate, schema, consumer)
    }

    companion object {

        val INSTANCE = StringValidation()

        private fun checkString(propValue: PsiElement, schema: CirJsonSchemaObject, consumer: CirJsonValidationHost) {
            val v = CirJsonSchemaAnnotatorChecker.getValue(propValue, schema) ?: return
            val value = StringUtil.unquoteString(v)

            if (schema.minLength != null) {
                if (value.length < schema.minLength!!) {
                    consumer.error(CirJsonBundle.message("schema.validation.string.shorter.than", schema.minLength!!),
                            propValue, CirJsonErrorPriority.LOW_PRIORITY)
                    return
                }
            }

            if (schema.maxLength != null) {
                if (value.length > schema.maxLength!!) {
                    consumer.error(CirJsonBundle.message("schema.validation.string.longer.than", schema.maxLength!!),
                            propValue, CirJsonErrorPriority.LOW_PRIORITY)
                    return
                }
            }

            if (schema.pattern != null) {
                if (schema.patternError != null) {
                    consumer.error(CirJsonBundle.message("schema.validation.invalid.string.pattern",
                            StringUtil.convertLineSeparators(schema.patternError!!)), propValue,
                            CirJsonErrorPriority.LOW_PRIORITY)
                }

                if (!schema.checkByPattern(value)) {
                    consumer.error(CirJsonBundle.message("schema.validation.string.violates.pattern",
                            StringUtil.convertLineSeparators(schema.patternError!!)), propValue,
                            CirJsonErrorPriority.LOW_PRIORITY)
                }
            }
        }

    }

}