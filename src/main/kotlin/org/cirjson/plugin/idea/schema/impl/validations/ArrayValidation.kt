package org.cirjson.plugin.idea.schema.impl.validations

import com.intellij.util.containers.MultiMap
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.extentions.intellij.set
import org.cirjson.plugin.idea.extentions.kotlin.trueOrNull
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
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
            if (schema.isUniqueItem) {
                val valueTexts = MultiMap<String, CirJsonValueAdapter>()
                val walker = CirJsonLikePsiWalker.getWalker(array.delegate, schema)!!

                for (adapter in list) {
                    valueTexts[walker.getNodeTextForValidation(adapter.delegate)] = adapter
                }

                for (entry in valueTexts.entrySet()) {
                    if (entry.value.size > 1) {
                        for (item in entry.value) {
                            if (!item.shouldCheckAsValue) {
                                continue
                            }

                            consumer.error(CirJsonBundle.message("schema.validation.not.unique"), item.delegate,
                                    CirJsonErrorPriority.TYPE_MISMATCH)
                        }
                    }
                }
            }

            if (schema.containsSchema != null) {
                var match = false

                for (item in list) {
                    val checker = consumer.checkByMatchResult(item, consumer.resolve(schema.containsSchema!!), options)

                    if (checker?.isValid.trueOrNull()) {
                        match = true
                        break
                    }
                }

                if (!match) {
                    consumer.error(CirJsonBundle.message("schema.validation.array.not.contains"), array.delegate,
                            CirJsonErrorPriority.MEDIUM_PRIORITY)
                }
            }

            if (schema.itemsSchema != null) {
                for (item in list) {
                    consumer.checkObjectBySchemaRecordErrors(schema.itemsSchema!!, item)
                }
            } else if (schema.itemsSchemaList != null) {
                val iterator = schema.itemsSchemaList!!.iterator()

                for (arrayValue in list) {
                    if (iterator.hasNext()) {
                        consumer.checkObjectBySchemaRecordErrors(iterator.next(), arrayValue)
                    } else {
                        if (schema.additionalItemsAllowed != true) {
                            consumer.error(CirJsonBundle.message("schema.validation.array.no.extra"), array.delegate,
                                    CirJsonErrorPriority.LOW_PRIORITY)
                        } else if (schema.additionalItemsSchema != null) {
                            consumer.checkObjectBySchemaRecordErrors(schema.additionalItemsSchema!!, arrayValue)
                        }
                    }
                }
            }

            if (schema.minItems != null && list.size < schema.minItems!!) {
                consumer.error(CirJsonBundle.message("schema.validation.array.shorter.than", schema.minItems!!),
                        array.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }

            if (schema.maxItems != null && list.size > schema.maxItems!!) {
                consumer.error(CirJsonBundle.message("schema.validation.array.longer.than", schema.maxItems!!),
                        array.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }

            if (schema.minLength != null && list.size < schema.minLength!!) {
                consumer.error(CirJsonBundle.message("schema.validation.array.shorter.than", schema.minLength!!),
                        array.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }

            if (schema.maxLength != null && list.size > schema.maxLength!!) {
                consumer.error(CirJsonBundle.message("schema.validation.array.longer.than", schema.maxLength!!),
                        array.delegate, CirJsonErrorPriority.LOW_PRIORITY)
            }
        }

    }

}