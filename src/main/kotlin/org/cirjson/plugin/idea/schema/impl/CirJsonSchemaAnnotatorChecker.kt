package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.validations.*

class CirJsonSchemaAnnotatorChecker(private val myProject: Project,
        private val myOptions: CirJsonComplianceCheckerOptions) : CirJsonValidationHost {

    private val myErrors = HashMap<PsiElement, CirJsonValidationError>()

    fun checkByScheme(value: CirJsonValueAdapter, schema: CirJsonSchemaObject) {
        val type = CirJsonSchemaType.getType(value)

        for (validation in getAllValidations(schema, type, value)) {
            validation.validate(value, schema, type, this, myOptions)
        }
    }

    val isCorrect: Boolean
        get() {
            return myErrors.isEmpty()
        }

    companion object {

        private fun getAllValidations(schema: CirJsonSchemaObject, type: CirJsonSchemaType?,
                value: CirJsonValueAdapter): Collection<CirJsonSchemaValidation> {
            val validations = LinkedHashSet<CirJsonSchemaValidation>()
            validations.add(EnumValidation.INSTANCE)

            if (type != null) {
                validations.add(TypeValidation.INSTANCE)

                when (type) {
                    CirJsonSchemaType._string_number -> {
                        validations.add(NumericValidation.INSTANCE)
                        validations.add(StringValidation.INSTANCE)
                    }

                    CirJsonSchemaType._number, CirJsonSchemaType._integer -> {
                        validations.add(NumericValidation.INSTANCE)
                    }

                    CirJsonSchemaType._string -> {
                        validations.add(StringValidation.INSTANCE)
                    }

                    CirJsonSchemaType._array -> {
                        validations.add(ArrayValidation.INSTANCE)
                    }

                    CirJsonSchemaType._object -> {
                        validations.add(ObjectValidation.INSTANCE)
                    }

                    else -> {}
                }
            }

            if (!value.shouldBeIgnored) {
                if (schema.hasNumericChecks && value.isNumberLiteral) {
                    validations.add(NumericValidation.INSTANCE)
                }

                if (schema.hasStringChecks && value.isStringLiteral) {
                    validations.add(StringValidation.INSTANCE)
                }

                if (schema.hasArrayChecks && value.isArray) {
                    validations.add(ArrayValidation.INSTANCE)
                }

                if (hasMinMaxLengthChecks(schema)) {
                    if (value.isStringLiteral) {
                        validations.add(StringValidation.INSTANCE)
                    }

                    if (value.isArray) {
                        validations.add(ArrayValidation.INSTANCE)
                    }
                }

                if (schema.hasObjectChecks && value.isObject) {
                    validations.add(ObjectValidation.INSTANCE)
                }
            }

            if (schema.not != null) {
                validations.add(NotValidation.INSTANCE)
            }

            if (schema.ifThenElse != null) {
                validations.add(IfThenElseValidation.INSTANCE)
            }

            return validations
        }

        private fun hasMinMaxLengthChecks(schema: CirJsonSchemaObject): Boolean {
            return schema.minLength != null || schema.maxLength != null
        }

    }

}
