package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter

/**
 * This performs a simple search for properties that must **not** be present.
 * It uses either:
 *   - `{ "not": { "required": ["x"] } }`
 *
 *     most likely to be found in a condition as follows:
 *
 *     `{ "if": { <y> }, "else": { "not": { "required": "x" } } }`
 *
 *     (Read this as: If not `<y>`, must not have property "x")
 *   - Or `{ "if": { "required": { "x" } }, "then": { <y> } }`
 *
 *     (Read this as: If we have property "x", we must `<y>`)
 * @param existingProperties to understand why this knowledge is required, see [com.jetbrains.jsonSchema.impl.JsonBySchemaNotRequiredCompletionTest.test not required x, y and z, then it will not complete the last of the three fields]
 */
internal fun findPropertiesThatMustNotBePresent(schema: CirJsonSchemaObject, position: PsiElement, project: Project,
        existingProperties: Set<String>): Set<String> {
    return schema.mapEffectiveSchemasNotNull(position, project) {
        it.not?.required?.minus(existingProperties)?.singleOrNull()
    }.plusLikelyEmpty(schema.flatMapIfThenElseBranches(position) { ifThenElse, parent ->
        ifThenElse.thenBranch?.let { then ->
            ifThenElse.condition.mapEffectiveSchemasNotNull(position, project) {
                it.required?.minus(existingProperties)?.singleOrNull()
            }.takeIf { it.isNotEmpty() && parent.adheresTo(then, project) }
        }
    })
}

/**
 * Traverses the graph of effective schema's and returns a set of all values where [selector] returned a non-null value.
 * Effective schema's includes schema's inside `"allOf"` and `"if" "then" "else"` blocks.
 */
private fun <T : Any> CirJsonSchemaObject.mapEffectiveSchemasNotNull(position: PsiElement, project: Project,
        selector: (CirJsonSchemaObject) -> T?): Set<T> {
    return setOfNotNull(selector(this)).plusLikelyEmpty(
            allOf?.flatMapLikelyEmpty { it.mapEffectiveSchemasNotNull(position, project, selector) }).plusLikelyEmpty(
            flatMapIfThenElseBranches(position) { ifThenElse, parent ->
                ifThenElse.effectiveBranchOrNull(project, parent)
                        ?.mapEffectiveSchemasNotNull(position, project, selector)
            })
}

private inline fun <T> CirJsonSchemaObject.flatMapIfThenElseBranches(position: PsiElement,
        mapper: (IfThenElse, parent: CirJsonObjectValueAdapter) -> Set<T>?): Set<T> {
    val ifThenElseList = ifThenElse?.takeIf { it.isNotEmpty() } ?: return emptySet()
    val parent = CirJsonLikePsiWalker.getWalker(position, this)?.getParentPropertyAdapter(position)?.parentObject
            ?: return emptySet()

    return ifThenElseList.flatMapLikelyEmpty { mapper(it, parent) }
}

internal fun IfThenElse.effectiveBranchOrNull(project: Project,
        parent: CirJsonObjectValueAdapter): CirJsonSchemaObject? {
    return if (parent.adheresTo(condition, project)) thenBranch else elseBranch
}

private fun CirJsonObjectValueAdapter.adheresTo(schema: CirJsonSchemaObject, project: Project): Boolean {
    return CirJsonSchemaAnnotatorChecker(project,
            CirJsonComplianceCheckerOptions.RELAX_ENUM_CHECK).also { it.checkByScheme(this, schema) }.isCorrect
}

// These allocation optimizations are in place because the checks in this file are often performed, but don't frequently yield results */
private fun <T> Set<T>.plusLikelyEmpty(elements: Set<T>?): Set<T> = when {
    this.isEmpty() -> elements ?: emptySet()
    elements.isNullOrEmpty() -> this
    else -> this + elements
}

private inline fun <T, R> Iterable<T>.flatMapLikelyEmpty(transform: (T) -> Collection<R>?): Set<R> {
    var destination: MutableSet<R>? = null
    for (element in this) {
        val list = transform(element)
        if (list.isNullOrEmpty()) continue

        destination = destination ?: HashSet()
        destination.addAll(list)
    }
    return destination ?: emptySet()
}
