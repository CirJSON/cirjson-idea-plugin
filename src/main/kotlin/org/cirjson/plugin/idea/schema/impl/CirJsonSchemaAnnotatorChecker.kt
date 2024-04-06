package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter

class CirJsonSchemaAnnotatorChecker(private val myProject: Project,
        private val myOptions: CirJsonComplianceCheckerOptions) {

    private val myErrors = HashMap<PsiElement, CirJsonValidationError>()

    fun checkByScheme(value: CirJsonValueAdapter, schema: CirJsonSchemaObject) {
        TODO()
    }

    val isCorrect: Boolean
        get() {
            return myErrors.isEmpty()
        }

}
