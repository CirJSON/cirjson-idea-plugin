package org.cirjson.plugin.idea.schema.extension

import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.*

interface CirJsonValidationHost {

    fun error(error: String, holder: PsiElement, priority: CirJsonErrorPriority)

    fun error(newHolder: PsiElement, error: CirJsonValidationError)

    fun error(error: String, holder: PsiElement, fixableIssueKind: CirJsonValidationError.FixableIssueKind,
            data: CirJsonValidationError.IssueData?, priority: CirJsonErrorPriority)

    fun typeError(value: PsiElement, currentType: CirJsonSchemaType?, vararg allowedTypes: CirJsonSchemaType)

    fun resolve(schemaObject: CirJsonSchemaObject): MatchResult

    fun checkByMatchResult(adapter: CirJsonValueAdapter, result: MatchResult,
            options: CirJsonComplianceCheckerOptions): CirJsonValidationHost?

    fun checkObjectBySchemaRecordErrors(schema: CirJsonSchemaObject, obj: CirJsonValueAdapter)

    val isValid: Boolean

    fun addErrorsFrom(otherHost: CirJsonValidationHost)

}