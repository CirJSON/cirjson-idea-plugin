package org.cirjson.plugin.idea.schema.impl.fixes

import org.cirjson.plugin.idea.schema.impl.CirJsonValidationError

data class CirJsonSchemaPropertiesInfo(
        val missingRequiredProperties: CirJsonValidationError.MissingMultiplePropsIssueData,
        val missingKnownProperties: CirJsonValidationError.MissingMultiplePropsIssueData) {

    val hasOnlyRequiredPropertiesMissing: Boolean
        get() = missingRequiredProperties.myMissingPropertyIssues.size ==
                missingKnownProperties.myMissingPropertyIssues.size

    val hasNoRequiredPropertiesMissing: Boolean
        get() = missingRequiredProperties.myMissingPropertyIssues.isEmpty()

}
