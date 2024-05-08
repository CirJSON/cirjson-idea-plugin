package org.cirjson.plugin.idea.schema.impl

import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.stream
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.validations.*
import java.util.stream.Collectors

class CirJsonSchemaAnnotatorChecker(private val myProject: Project,
        private val myOptions: CirJsonComplianceCheckerOptions) : CirJsonValidationHost {

    val errors = HashMap<PsiElement, CirJsonValidationError>()

    var hadTypeError = false

    override fun error(error: String, holder: PsiElement, priority: CirJsonErrorPriority) {
        error(error, holder, CirJsonValidationError.FixableIssueKind.None, null, priority)
    }

    override fun error(newHolder: PsiElement, error: CirJsonValidationError) {
        error(error.message, newHolder, error.fixableIssueKind, error.issueData, error.priority)
    }

    override fun error(error: String, holder: PsiElement, fixableIssueKind: CirJsonValidationError.FixableIssueKind,
            data: CirJsonValidationError.IssueData?, priority: CirJsonErrorPriority) {
        if (holder in errors) {
            return
        }

        errors[holder] = CirJsonValidationError(error, fixableIssueKind, data, priority)
    }

    override fun typeError(value: PsiElement, currentType: CirJsonSchemaType?, vararg allowedTypes: CirJsonSchemaType) {
        if (allowedTypes.isEmpty()) {
            return
        }

        val currentTypeDesc = if (currentType != null) {
            " ${CirJsonBundle.message("schema.validation.actual")} ${currentType.name}."
        } else {
            ""
        }
        val prefix = "${CirJsonBundle.message("schema.validation.incompatible.types")}\n"

        if (allowedTypes.size == 1) {
            error("$prefix ${
                CirJsonBundle.message("schema.validation.required.one", allowedTypes.first().name, currentTypeDesc)
            }", value, CirJsonValidationError.FixableIssueKind.ProhibitedType,
                    CirJsonValidationError.TypeMismatchIssueData(allowedTypes as Array<CirJsonSchemaType>),
                    CirJsonErrorPriority.TYPE_MISMATCH)
        } else {
            val typesText =
                    allowedTypes.map(CirJsonSchemaType::name).sortedWith(Comparator.naturalOrder()).joinToString(", ")
            error("$prefix ${CirJsonBundle.message("schema.validation.required.one", typesText, currentTypeDesc)}",
                    value, CirJsonValidationError.FixableIssueKind.ProhibitedType,
                    CirJsonValidationError.TypeMismatchIssueData(allowedTypes as Array<CirJsonSchemaType>),
                    CirJsonErrorPriority.TYPE_MISMATCH)
        }

        hadTypeError = true
    }

    override fun resolve(schemaObject: CirJsonSchemaObject): MatchResult {
        return CirJsonSchemaResolver(myProject, schemaObject).detailedResolve()
    }

    override fun checkByMatchResult(adapter: CirJsonValueAdapter, result: MatchResult,
            options: CirJsonComplianceCheckerOptions) {
        checkByMatchResult(myProject, adapter, result, options)
    }

    override fun checkObjectBySchemaRecordErrors(schema: CirJsonSchemaObject, obj: CirJsonValueAdapter) {
        checkObjectBySchemaRecordErrors(schema, obj, CirJsonPointerPosition())
    }

    fun checkObjectBySchemaRecordErrors(schema: CirJsonSchemaObject, obj: CirJsonValueAdapter,
            position: CirJsonPointerPosition) {
        checkByMatchResult(myProject, obj, CirJsonSchemaResolver(myProject, schema, position).detailedResolve(),
                myOptions)?.let {
            hadTypeError = it.hadTypeError
            errors.putAll(it.errors)
        }
    }

    override val isValid: Boolean
        get() = errors.isNotEmpty() && !hadTypeError

    fun checkByScheme(value: CirJsonValueAdapter, schema: CirJsonSchemaObject) {
        val type = CirJsonSchemaType.getType(value)

        for (validation in getAllValidations(schema, type, value)) {
            validation.validate(value, schema, type, this, myOptions)
        }
    }

    val isCorrect: Boolean
        get() {
            return errors.isEmpty()
        }

    private fun processOneOf(value: CirJsonValueAdapter, oneOf: List<CirJsonSchemaObject>): CirJsonSchemaObject? {
        val candidateErroneousCheckers = arrayListOf<CirJsonSchemaAnnotatorChecker>()
        val candidateErroneousSchemas = arrayListOf<CirJsonSchemaObject>()
        val correct = SmartList<CirJsonSchemaObject>()

        for (obj in oneOf) {
            if (obj.shouldValidateAgainstJSType) {
                continue
            }

            val checker = CirJsonSchemaAnnotatorChecker(myProject, myOptions)
            checker.checkByScheme(value, obj)

            if (checker.isCorrect) {
                candidateErroneousCheckers.clear()
                candidateErroneousSchemas.clear()
                correct.add(obj)
            } else {
                candidateErroneousCheckers.add(checker)
                candidateErroneousSchemas.add(obj)
            }
        }

        if (correct.size == 1) {
            return correct[0]
        }

        if (correct.isNotEmpty()) {
            val type = CirJsonSchemaType.getType(value)

            if (type != null) {
                if (HashSet(correct).size > 1 && !schemesDifferWithNotCheckedProperties(correct)) {
                    error(CirJsonBundle.message("schema.validation.to.more.than.one"), value.delegate,
                            CirJsonErrorPriority.MEDIUM_PRIORITY)
                }
            }

            return correct.last()
        }

        return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, true)
    }

    private fun processAnyOf(value: CirJsonValueAdapter, anyOf: List<CirJsonSchemaObject>): CirJsonSchemaObject? {
        val candidateErroneousCheckers = arrayListOf<CirJsonSchemaAnnotatorChecker>()
        val candidateErroneousSchemas = arrayListOf<CirJsonSchemaObject>()

        for (obj in anyOf) {
            val checker = CirJsonSchemaAnnotatorChecker(myProject, myOptions).apply { checkByScheme(value, obj) }

            if (checker.isCorrect) {
                return obj
            }

            candidateErroneousCheckers.add(checker)
            candidateErroneousSchemas.add(obj)
        }

        return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, false)
    }

    /**
     * Filters schema validation results to get the result with the "minimal" amount of errors.
     * This is needed in case of oneOf or anyOf conditions, when there exist no match.
     * I.e., when we have multiple schema candidates, but none is applicable.
     * In this case we need to show the most "suitable" error messages
     *   - by detecting the most "likely" schema corresponding to the current entity
     */
    private fun showErrorsAndGetLeastErroneous(candidateErroneousCheckers: List<CirJsonSchemaAnnotatorChecker>,
            candidateErroneousSchemas: List<CirJsonSchemaObject>, isOneOf: Boolean): CirJsonSchemaObject? {
        var current: CirJsonSchemaObject? = null
        var currentWithMinAverage: CirJsonSchemaObject? = null
        val minAverage = candidateErroneousCheckers.map { getAverageFailureAmount(it) }
                .minWithOrNull(Comparator.comparingInt { it.ordinal })
        val min = (minAverage ?: AverageFailureAmount.Hard).ordinal

        val minErrorCount = candidateErroneousCheckers.map { it.errors.size }.minOrNull() ?: Int.MAX_VALUE

        val errorsWithMinAverage = MultiMap<PsiElement, CirJsonValidationError>()
        var allErrors = MultiMap<PsiElement, CirJsonValidationError>()

        for (i in candidateErroneousCheckers.indices) {
            val checker = candidateErroneousCheckers[i]
            val isMoreThanMinErrors = checker.errors.size > minErrorCount
            val isMoreThanAverage = getAverageFailureAmount(checker).ordinal > min

            if (!isMoreThanMinErrors) {
                if (isMoreThanAverage) {
                    currentWithMinAverage = candidateErroneousSchemas[i]
                } else {
                    current = candidateErroneousSchemas[i]
                }

                for (entry in checker.errors) {
                    val insertedIn = if (isMoreThanAverage) errorsWithMinAverage else allErrors
                    insertedIn[entry.key] = entry.value
                }
            }
        }

        if (allErrors.isEmpty) {
            allErrors = errorsWithMinAverage
        }

        for (entry in allErrors.entrySet()) {
            val value = entry.value

            if (value.isEmpty()) {
                continue
            }

            if (value.size == 1) {
                error(entry.key, value.first())
                continue
            }

            val error = tryMergeErrors(value, isOneOf)

            if (error != null) {
                error(entry.key, error)
            } else {
                for (validationError in value) {
                    error(entry.key, validationError)
                }
            }
        }

        current = current ?: currentWithMinAverage ?: candidateErroneousSchemas.lastOrNull()
        return current
    }

    private enum class AverageFailureAmount {

        Light,

        MissingItems,

        Medium,

        Hard,

        NotSchema

    }

    companion object {

        private val PRIMITIVE_TYPES =
                setOf(CirJsonSchemaType._integer, CirJsonSchemaType._number, CirJsonSchemaType._boolean,
                        CirJsonSchemaType._string, CirJsonSchemaType._null)

        fun checkByMatchResult(project: Project, elementToCheck: CirJsonValueAdapter, result: MatchResult,
                options: CirJsonComplianceCheckerOptions): CirJsonSchemaAnnotatorChecker? {
            val checkers = arrayListOf<CirJsonSchemaAnnotatorChecker>()

            if (result.myExcludingSchemas.isEmpty() && result.mySchemas.size == 1) {
                val checker = CirJsonSchemaAnnotatorChecker(project, options)
                checker.checkByScheme(elementToCheck, result.mySchemas.first())
                checkers.add(checker)
            } else {
                if (result.mySchemas.isNotEmpty()) {
                    checkers.add(
                            processSchemasVariants(project, result.mySchemas, elementToCheck, false, options).second)
                }

                if (result.myExcludingSchemas.isNotEmpty()) {
                    val list = result.myExcludingSchemas.map {
                        processSchemasVariants(project, it, elementToCheck, true, options).second
                    }
                    checkers.add(mergeErrors(project, list, options, result.myExcludingSchemas))
                }
            }

            if (checkers.isEmpty()) {
                return null
            }

            if (checkers.size == 1) {
                return checkers[0]
            }

            return checkers.firstOrNull { !it.hadTypeError } ?: checkers[0]
        }

        private fun processSchemasVariants(project: Project, collection: Collection<CirJsonSchemaObject>,
                value: CirJsonValueAdapter, isOneOf: Boolean,
                options: CirJsonComplianceCheckerOptions): Pair<CirJsonSchemaObject?, CirJsonSchemaAnnotatorChecker> {
            val checker = CirJsonSchemaAnnotatorChecker(project, options)
            val type = CirJsonSchemaType.getType(value)
            var selected: CirJsonSchemaObject? = null

            if (type == null) {
                if (!value.shouldBeIgnored) {
                    checker.typeError(value.delegate, null, *getExpectedTypes(collection))
                }
            } else {
                val filtered = ArrayList<CirJsonSchemaObject>(collection.size)
                val altType = value.getAlternateType(type)!!

                for (schema in collection) {
                    if (!areSchemaTypesCompatible(schema, type) && !areSchemaTypesCompatible(schema, altType)) {
                        continue
                    }

                    filtered.add(schema)
                }

                if (filtered.isEmpty()) {
                    checker.typeError(value.delegate, altType, *getExpectedTypes(collection))
                } else if (filtered.size == 1) {
                    selected = filtered[0]
                    checker.checkByScheme(value, selected)
                } else {
                    selected = if (isOneOf) {
                        checker.processOneOf(value, filtered)
                    } else {
                        checker.processAnyOf(value, filtered)
                    }
                }
            }

            return Pair.create(selected, checker)
        }

        private fun getExpectedTypes(schemas: Collection<CirJsonSchemaObject>): Array<CirJsonSchemaType> {
            val list = arrayListOf<CirJsonSchemaType>()

            for (schema in schemas) {
                val type = schema.type

                if (type != null) {
                    list.add(type)
                } else {
                    schema.typeVariants?.let { list.addAll(it) }
                }
            }

            return list.toTypedArray()
        }

        private fun areSchemaTypesCompatible(schema: CirJsonSchemaObject, type: CirJsonSchemaType): Boolean {
            val matchingSchemaType = getMatchingSchemaType(schema, type)

            return if (matchingSchemaType != null) {
                matchingSchemaType == type
            } else if (schema.enum != null) {
                type in PRIMITIVE_TYPES
            } else {
                true
            }
        }

        fun getMatchingSchemaType(schema: CirJsonSchemaObject, type: CirJsonSchemaType): CirJsonSchemaType? {
            val matchType = schema.type

            return if (matchType != null) {
                if (type == CirJsonSchemaType._integer && matchType == CirJsonSchemaType._number) {
                    type
                } else if (type == CirJsonSchemaType._string_number && (matchType == CirJsonSchemaType._integer
                                || matchType == CirJsonSchemaType._number || matchType == CirJsonSchemaType._string)) {
                    type
                } else {
                    matchType
                }
            } else if (schema.typeVariants != null) {
                val matchTypes = schema.typeVariants!!

                if (type in matchTypes) {
                    type
                } else if (type == CirJsonSchemaType._integer && CirJsonSchemaType._number in matchTypes) {
                    type
                } else if (type == CirJsonSchemaType._string_number && (CirJsonSchemaType._integer in matchTypes
                                || CirJsonSchemaType._number in matchTypes
                                || CirJsonSchemaType._string in matchTypes)) {
                    type
                } else {
                    matchTypes.first()
                }
            } else if (schema.hasProperties && type == CirJsonSchemaType._object) {
                type
            } else {
                null
            }
        }

        private fun mergeErrors(project: Project, list: List<CirJsonSchemaAnnotatorChecker>,
                options: CirJsonComplianceCheckerOptions,
                excludingSchemas: List<Collection<CirJsonSchemaObject>>): CirJsonSchemaAnnotatorChecker {
            val checker = CirJsonSchemaAnnotatorChecker(project, options)

            for (ch in list) {
                for (element in ch.errors) {
                    val error = element.value

                    if (error.fixableIssueKind == CirJsonValidationError.FixableIssueKind.ProhibitedProperty) {
                        val propertyName =
                                (error.issueData as CirJsonValidationError.ProhibitedPropertyIssueData).propertyName
                        var skip = false

                        for (objects in excludingSchemas) {
                            val propExists = objects.filter { !it.hasOwnExtraPropertyProhibition }
                                    .any { it.getPropertyByName(propertyName) != null }

                            if (propExists) {
                                skip = true
                            }
                        }

                        if (skip) {
                            continue
                        }
                    }

                    checker.errors[element.key] = error
                }
            }

            return checker
        }

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

        private fun schemesDifferWithNotCheckedProperties(list: List<CirJsonSchemaObject>): Boolean {
            return list.any { !StringUtil.isEmptyOrSpaces(it.format) }
        }

        private fun getAverageFailureAmount(checker: CirJsonSchemaAnnotatorChecker): AverageFailureAmount {
            var lowPriorityCount = 0
            var hasMedium = false
            var hasMissing = false
            var hasHard = false
            val values = checker.errors.values

            for (value in values) {
                when (value.priority) {
                    CirJsonErrorPriority.LOW_PRIORITY -> lowPriorityCount++
                    CirJsonErrorPriority.MISSING_PROPS -> hasMissing = true
                    CirJsonErrorPriority.MEDIUM_PRIORITY -> hasMedium = true
                    CirJsonErrorPriority.TYPE_MISMATCH -> hasHard = true
                    CirJsonErrorPriority.NOT_SCHEMA -> return AverageFailureAmount.NotSchema
                }
            }

            return if (hasHard) {
                AverageFailureAmount.Hard
            } else if (hasMissing) {
                AverageFailureAmount.MissingItems
            } else if (hasMedium || lowPriorityCount > 3) {
                AverageFailureAmount.Medium
            } else {
                AverageFailureAmount.Light
            }
        }

        private fun tryMergeErrors(errors: Collection<CirJsonValidationError>,
                isOneOf: Boolean): CirJsonValidationError? {
            var commonIssueKind: CirJsonValidationError.FixableIssueKind? = null

            for (error in errors) {
                val currentIssueKind = error.fixableIssueKind

                if (currentIssueKind == CirJsonValidationError.FixableIssueKind.None) {
                    return null
                } else if (commonIssueKind == null) {
                    commonIssueKind = currentIssueKind
                } else if (currentIssueKind != commonIssueKind) {
                    return null
                }
            }

            return when (commonIssueKind) {
                CirJsonValidationError.FixableIssueKind.NonEnumValue -> {
                    val prefix = CirJsonBundle.message("schema.validation.enum.mismatch", "")
                    val text = errors.map { StringUtil.trimEnd(StringUtil.trimStart(it.message, prefix), prefix) }
                            .map { StringUtil.split(it, ", ") }.stream().flatMap { it.stream() }.distinct()
                            .collect(Collectors.joining(", "))
                    CirJsonValidationError(prefix + text, commonIssueKind, null, errors.first().priority)
                }

                CirJsonValidationError.FixableIssueKind.MissingProperty -> {
                    val sets = errors.map { it.issueData as CirJsonValidationError.MissingMultiplePropsIssueData }
                            .map { it.getMessage(false) }.stream().collect(NlsMessages.joiningOr())
                    val message = if (isOneOf) {
                        CirJsonBundle.message("schema.validation.one.of.property.sets.required", sets)
                    } else {
                        CirJsonBundle.message("schema.validation.at.least.one.of.property.sets.required", sets)
                    }
                    val kind = if (isOneOf) {
                        CirJsonValidationError.FixableIssueKind.MissingOneOfProperty
                    } else {
                        CirJsonValidationError.FixableIssueKind.MissingAnyOfProperty
                    }
                    CirJsonValidationError(message, kind, CirJsonValidationError.MissingOneOfPropsIssueData(
                            errors.map { it.issueData as CirJsonValidationError.MissingMultiplePropsIssueData }),
                            errors.first().priority)
                }

                CirJsonValidationError.FixableIssueKind.ProhibitedType -> {
                    val allTypes = errors.map { it.issueData as CirJsonValidationError.TypeMismatchIssueData }.stream()
                            .flatMap { it.expectedTypes.stream() }.collect(Collectors.toSet())

                    if (allTypes.size == 1) {
                        return errors.first()
                    }

                    val actualInfos =
                            errors.map { it.message }.map(CirJsonSchemaAnnotatorChecker::fetchActual).distinct()
                                    .toList()
                    val actualInfo = if (actualInfos.size == 1) " ${
                        CirJsonBundle.message("schema.validation.actual")
                    } ${actualInfos[0]}." else ""
                    val required = allTypes.map { it.description }.sorted().joinToString(", ")
                    val commonTypeMessage = "${CirJsonBundle.message("schema.validation.incompatible.types")}\n${
                        CirJsonBundle.message("schema.validation.required.one.of", required, actualInfo)
                    }"
                    CirJsonValidationError(commonTypeMessage, CirJsonValidationError.FixableIssueKind.TypeMismatch,
                            CirJsonValidationError.TypeMismatchIssueData(allTypes.toTypedArray()),
                            errors.first().priority)
                }

                else -> null
            }
        }

        private fun fetchActual(message: String): String? {
            val actualMessage = "${CirJsonBundle.message("schema.validation.actual")} "
            val actual = message.indexOf(actualMessage)

            if (actual == -1) {
                return null
            }

            val substring = if (message.endsWith(actualMessage)) {
                message.substring(0, actual)
            } else {
                message.substring(actual + actualMessage.length)
            }

            return StringUtil.trimEnd(substring, ".")
        }

    }

}
