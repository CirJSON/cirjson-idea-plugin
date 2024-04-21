package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.psi.PsiElement

/**
 * Represents how the completion should be split.
 *
 * @param destination The existing object where the completion will be inserted
 *
 * @param wrappingPath The keys in which the completion needs to be wrapped.
 */
internal class NestedCompletionMoveData(val destination: PsiElement?, val wrappingPath: List<String>)
