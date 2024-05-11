package org.cirjson.plugin.idea.schema.impl.validations

import com.intellij.openapi.util.text.StringUtil
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaValidation
import org.cirjson.plugin.idea.schema.extension.CirJsonValidationHost
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.*

class EnumValidation private constructor() : CirJsonSchemaValidation {

    override fun validate(propValue: CirJsonValueAdapter, schema: CirJsonSchemaObject, schemaType: CirJsonSchemaType?,
            consumer: CirJsonValidationHost, options: CirJsonComplianceCheckerOptions) {
        val enumItems = schema.enum ?: return
        val walker = CirJsonLikePsiWalker.getWalker(propValue.delegate, schema) ?: return
        val text = StringUtil.notNullize(walker.getNodeTextForValidation(propValue.delegate))
        val caseInsensitive = schema.isForceCaseInsensitive
        val eq: (String, String) -> Boolean = if (caseInsensitive || options.isCaseInsensitiveEnumCheck) {
            { s1, s2 -> s1.equals(s2, ignoreCase = true) }
        } else {
            String::equals
        }

        for (obj in enumItems) {
            if (checkEnumValue(obj, walker, propValue, text, eq)) {
                return
            }
        }

        consumer.error(CirJsonBundle.message("schema.validation.enum.mismatch", enumItems.joinToString(", ")),
                propValue.delegate, CirJsonValidationError.FixableIssueKind.NonEnumValue, null,
                CirJsonErrorPriority.MEDIUM_PRIORITY)
    }

    companion object {

        val INSTANCE = EnumValidation()

        private fun checkEnumValue(obj: Any, walker: CirJsonLikePsiWalker, adapter: CirJsonValueAdapter?, text: String,
                stringEq: (String, String) -> Boolean): Boolean {
            if (adapter != null && !adapter.shouldCheckAsValue) {
                return true
            }

            if (obj is EnumArrayValueWrapper) {
                if (adapter is CirJsonArrayValueAdapter) {
                    val elements = adapter.elements
                    val values = obj.values

                    if (elements.size == values.size) {
                        for (i in values.indices) {
                            if (!checkEnumValue(values[i], walker, elements[i],
                                            walker.getNodeTextForValidation(elements[i].delegate), stringEq)) {
                                return false
                            }
                        }

                        return true
                    }
                }
            } else if (obj is EnumObjectValueWrapper) {
                if (adapter is CirJsonObjectValueAdapter) {
                    val props = adapter.propertyList
                    val values = obj.values

                    if (props.size == values.size) {
                        for (prop in props) {
                            if (prop.name !in values) {
                                return false
                            }

                            for (value in prop.values) {
                                if (!checkEnumValue(values[prop.name]!!, walker, value,
                                                walker.getNodeTextForValidation(value.delegate), stringEq)) {
                                    return false
                                }
                            }
                        }

                        return true
                    }
                }
            } else {
                return if (!walker.isAllowingSingleQuotes) {
                    stringEq.invoke(obj.toString(), text)
                } else {
                    equalsIgnoreQuotes(obj.toString(), text, walker.isRequiringValueQuote, stringEq)
                }
            }

            return false
        }

        private fun equalsIgnoreQuotes(s1: String, s2: String, requireQuotedValues: Boolean,
                eq: (String, String) -> Boolean): Boolean {
            val quoted1 = StringUtil.isQuotedString(s1)
            val quoted2 = StringUtil.isQuotedString(s2)

            return if (requireQuotedValues && quoted1 != quoted2) {
                false
            } else if (requireQuotedValues && !quoted1) {
                eq.invoke(s1, s2)
            } else {
                eq.invoke(StringUtil.unquoteString(s1), StringUtil.unquoteString(s2))
            }
        }

    }

}