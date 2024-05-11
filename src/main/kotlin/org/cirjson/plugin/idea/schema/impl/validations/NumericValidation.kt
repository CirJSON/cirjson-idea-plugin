package org.cirjson.plugin.idea.schema.impl.validations

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.*
import kotlin.math.abs

class NumericValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        checkNumber(propValue.delegate, schema, schemaType, consumer)
    }

    companion object {

        val INSTANCE = NumericValidation()

        private fun checkNumber(propValue: PsiElement, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
                consumer: CirJsonValidationHost) {
            val value: Number?
            val valueText = CirJsonSchemaAnnotatorChecker.getValue(propValue, schema) ?: return

            if (schemaType == CirJsonSchemaType._integer) {
                value = CirJsonSchemaType.getIntegerValue(valueText)

                if (value == null) {
                    consumer.error(CirJsonBundle.message("schema.validation.integer.expected"), propValue,
                            CirJsonValidationError.FixableIssueKind.TypeMismatch,
                            CirJsonValidationError.TypeMismatchIssueData(arrayOf(schemaType)),
                            CirJsonErrorPriority.TYPE_MISMATCH)
                    return
                }
            } else {
                try {
                    value = valueText.toDouble()
                } catch (e: NumberFormatException) {
                    if (schemaType != CirJsonSchemaType._string_number) {
                        consumer.error(CirJsonBundle.message("schema.validation.number.expected"), propValue,
                                CirJsonValidationError.FixableIssueKind.TypeMismatch,
                                CirJsonValidationError.TypeMismatchIssueData(arrayOf(schemaType)),
                                CirJsonErrorPriority.TYPE_MISMATCH)
                    }

                    return
                }
            }

            val multipleOf = schema.multipleOf

            if (multipleOf != null) {
                val leftOver = value.toDouble() % multipleOf.toDouble()

                if (leftOver > 0.000001) {
                    val realMultipleOf = if (abs(multipleOf.toDouble() - multipleOf.toInt()) < 0.000001) {
                        multipleOf.toInt()
                    } else {
                        multipleOf
                    }
                    val multipleOfValue = realMultipleOf.toString()
                    consumer.error(CirJsonBundle.message("schema.validation.not.multiple.of", multipleOfValue),
                            propValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            }

            checkMinimum(schema, value, propValue, consumer)
            checkMaximum(schema, value, propValue, consumer)
        }

        private fun checkMinimum(schema: CirJsonSchemaObject, value: Number, propertyValue: PsiElement,
                consumer: CirJsonValidationHost) {
            val exclusiveMinimumNumber = schema.exclusiveMinimumNumber

            if (exclusiveMinimumNumber != null) {
                val doubleValue = exclusiveMinimumNumber.toDouble()

                if (value.toDouble() <= doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.less.than.exclusive.minimum",
                            exclusiveMinimumNumber), propertyValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            }

            val minimum = schema.minimum ?: return
            val isExclusive = schema.isExclusiveMinimum
            val doubleValue = minimum.toDouble()

            if (isExclusive) {
                if (value.toDouble() <= doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.less.than.exclusive.minimum", minimum),
                            propertyValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            } else {
                if (value.toDouble() < doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.less.than.minimum", minimum), propertyValue,
                            CirJsonErrorPriority.LOW_PRIORITY)
                }
            }
        }

        private fun checkMaximum(schema: CirJsonSchemaObject, value: Number, propertyValue: PsiElement,
                consumer: CirJsonValidationHost) {
            val exclusiveMaximumNumber = schema.exclusiveMaximumNumber

            if (exclusiveMaximumNumber != null) {
                val doubleValue = exclusiveMaximumNumber.toDouble()

                if (value.toDouble() >= doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.greater.than.exclusive.maximum",
                            exclusiveMaximumNumber), propertyValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            }

            val maximum = schema.maximum ?: return
            val isExclusive = schema.isExclusiveMaximum
            val doubleValue = maximum.toDouble()

            if (isExclusive) {
                if (value.toDouble() >= doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.greater.than.exclusive.maximum", maximum),
                            propertyValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            } else {
                if (value.toDouble() > doubleValue) {
                    consumer.error(CirJsonBundle.message("schema.validation.greater.than.maximum", maximum),
                            propertyValue, CirJsonErrorPriority.LOW_PRIORITY)
                }
            }
        }

    }

}