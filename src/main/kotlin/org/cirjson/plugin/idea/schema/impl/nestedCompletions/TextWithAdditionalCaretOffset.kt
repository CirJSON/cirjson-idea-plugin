package org.cirjson.plugin.idea.schema.impl.nestedCompletions

/**
 * Is used to carry information around regarding how much the caret needs to be offset to insert this string
 *
 * @param offset null implies that there is no caret to be moved
 */
data class TextWithAdditionalCaretOffset(val offset: Int?, val text: String)
