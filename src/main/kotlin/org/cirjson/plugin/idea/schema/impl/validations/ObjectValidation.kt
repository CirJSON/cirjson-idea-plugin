package org.cirjson.plugin.idea.schema.impl.validations

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.*
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class ObjectValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        checkObject(propValue, schema, consumer, options)
    }

    companion object {

        val INSTANCE = ObjectValidation()

        private fun checkObject(value: CirJsonValueAdapter, schema: CirJsonSchemaObject,
                consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
            val obj = value.asObject ?: return

            val propertyList = obj.propertyList
            val set = hashSetOf<String>()

            for (property in propertyList) {
                val name = StringUtil.notNullize(property.name)
                val propertyNamesSchema = schema.propertyNamesSchema

                if (propertyNamesSchema != null) {
                    val nameValueAdapter = property.nameValueAdapter

                    if (nameValueAdapter != null) {
                        val checker =
                                consumer.checkByMatchResult(nameValueAdapter, consumer.resolve(propertyNamesSchema),
                                        options)

                        if (checker != null) {
                            consumer.addErrorsFrom(checker)
                        }
                    }
                }

                val step = CirJsonPointerPosition.createSingleProperty(name)
                val pair = CirJsonSchemaVariantsTreeBuilder.doSingleStep(step, schema, false)

                if (pair.first == ThreeState.NO && name !in set) {
                    consumer.error(CirJsonBundle.message("cirjson.schema.annotation.not.allowed.property", name),
                            property.delegate, CirJsonValidationError.FixableIssueKind.ProhibitedProperty,
                            CirJsonValidationError.ProhibitedPropertyIssueData(name), CirJsonErrorPriority.LOW_PRIORITY)
                } else if (pair.first == ThreeState.UNSURE) {
                    for (propertyValue in property.values) {
                        consumer.checkObjectBySchemaRecordErrors(pair.second!!, propertyValue)
                    }
                }

                set.add(name)
            }

            reportMissingOptionalProperties(value, schema, consumer, options)

            if (!options.isForceStrict) {
                return
            }

            val required = schema.required

            if (required != null) {
                val requiredNames = LinkedHashSet(required)
                requiredNames.removeAll(set)

                if (requiredNames.isNotEmpty()) {
                    val data = createMissingPropertiesData(schema, requiredNames, consumer)
                    consumer.error(CirJsonBundle.message("schema.validation.missing.required.property.or.properties",
                            data.getMessage(false)), value.delegate,
                            CirJsonValidationError.FixableIssueKind.MissingProperty, data,
                            CirJsonErrorPriority.MISSING_PROPS)
                }
            }

            if (schema.minProperties != null && propertyList.size < schema.minProperties!!) {
                consumer.error(
                        CirJsonBundle.message("schema.validation.number.of.props.less.than", schema.minProperties!!),
                        value.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }

            if (schema.maxProperties != null && propertyList.size > schema.maxProperties!!) {
                consumer.error(
                        CirJsonBundle.message("schema.validation.number.of.props.greater.than", schema.maxProperties!!),
                        value.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }

            val dependencies = schema.propertyDependencies

            if (dependencies != null) {
                for (entry in dependencies) {
                    if (entry.key in set) {
                        val list = entry.value
                        val deps = HashSet(list)
                        deps.removeAll(set)

                        if (deps.isNotEmpty()) {
                            val data = createMissingPropertiesData(schema, deps, consumer)
                            consumer.error(CirJsonBundle.message("schema.validation.violated.dependency",
                                    data.getMessage(false), entry.key), value.delegate,
                                    CirJsonValidationError.FixableIssueKind.MissingProperty, data,
                                    CirJsonErrorPriority.MISSING_PROPS)
                        }
                    }
                }
            }

            val schemaDependencies = schema.schemaDependencyNames
            schemaDependencies?.forEach {
                val dependency = schema.getSchemaDependencyByName(it)

                if (dependency != null && it in set) {
                    consumer.checkObjectBySchemaRecordErrors(dependency, value)
                }
            }
        }

        private fun reportMissingOptionalProperties(inspectedValue: CirJsonValueAdapter, schema: CirJsonSchemaObject,
                validationHost: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
            val objectValueAdapter = inspectedValue.asObject ?: return

            if (!options.isReportMissingOptionalProperties) {
                return
            }

            val existingProperties = objectValueAdapter.propertyList.map { it.name }

            val iter = Iterable { schema.propertyNames.iterator() }
            val missingProperties = StreamSupport.stream(iter.spliterator(), false).filter { it !in existingProperties }
                    .collect(Collectors.toSet())
            val missingPropertiesData = createMissingPropertiesData(schema, missingProperties, validationHost)
            validationHost.error(CirJsonBundle.message("schema.validation.missing.not.required.property.or.properties",
                    missingPropertiesData.getMessage(false)), inspectedValue.delegate,
                    CirJsonValidationError.FixableIssueKind.MissingOptionalProperty, missingPropertiesData,
                    CirJsonErrorPriority.MISSING_PROPS)
        }

        private fun createMissingPropertiesData(schema: CirJsonSchemaObject, requiredNames: Set<String>,
                consumer: CirJsonValidationHost): CirJsonValidationError.MissingMultiplePropsIssueData {
            val allProps = arrayListOf<CirJsonValidationError.MissingPropertyIssueData>()

            for (req in requiredNames) {
                val propertySchema = resolvePropertySchema(schema, req)
                var defaultValue = propertySchema?.default

                if (defaultValue == null) {
                    val example = schema.example
                    defaultValue = example?.get(req)
                }

                val enumCount = Ref.create(0)

                var type: CirJsonSchemaType? = null

                if (propertySchema != null) {
                    var result: MatchResult? = null
                    var valueFromEnum = getDefaultValueFromEnum(propertySchema, enumCount)

                    if (valueFromEnum != null) {
                        defaultValue = valueFromEnum
                    } else {
                        result = consumer.resolve(propertySchema)

                        if (result.mySchemas.size == 1) {
                            valueFromEnum = getDefaultValueFromEnum(result.mySchemas[0], enumCount)

                            if (valueFromEnum != null) {
                                defaultValue = valueFromEnum
                            }
                        }
                    }

                    type = propertySchema.type

                    if (type == null) {
                        if (result == null) {
                            result = consumer.resolve(propertySchema)

                            if (result.mySchemas.size == 1) {
                                type = result.mySchemas[0].type
                            }
                        }
                    }
                }

                allProps.add(
                        CirJsonValidationError.MissingPropertyIssueData(req, type!!, defaultValue!!, enumCount.get()))
            }

            return CirJsonValidationError.MissingMultiplePropsIssueData(allProps)
        }

        private fun resolvePropertySchema(schema: CirJsonSchemaObject, req: String): CirJsonSchemaObject? {
            val propOrNull = schema.getPropertyByName(req)

            if (propOrNull != null) {
                return propOrNull
            }

            val propertySchema = schema.getMatchingPatternPropertySchema(req)

            if (propertySchema != null) {
                return propertySchema
            }

            val additionalPropertiesSchema = schema.additionalPropertiesSchema

            if (additionalPropertiesSchema != null) {
                return additionalPropertiesSchema
            }

            return null
        }

        private fun getDefaultValueFromEnum(propertySchema: CirJsonSchemaObject, enumCount: Ref<Int>): Any? {
            val enumValues = propertySchema.enum ?: return null
            enumCount.set(enumValues.size)

            if (enumValues.isNotEmpty()) {
                val defaultObj = enumValues[0]
                return if (defaultObj is String) StringUtil.unquoteString(defaultObj) else defaultObj
            }

            return null
        }

    }

}