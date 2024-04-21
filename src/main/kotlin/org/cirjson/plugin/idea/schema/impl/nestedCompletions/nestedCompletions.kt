package org.cirjson.plugin.idea.schema.impl.nestedCompletions

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.Stack
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaResolver
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType

/**
 * Collects nested completions for a JSON schema object.
 * If `[node] == null`, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
internal fun CirJsonSchemaObject.collectNestedCompletions(project: Project, node: NestedCompletionsNode?,
        completionPath: SchemaPath?, collector: (path: SchemaPath?, schema: CirJsonSchemaObject) -> Unit) {
    collector(completionPath, this) // Breadth first

    node?.children?.filterIsInstance<ChildNode.OpenNode>()?.forEach { (name, childNode) ->
        for (subSchema in findSubSchemasByName(project, name)) {
            subSchema.collectNestedCompletions(project, childNode, completionPath / name, collector)
        }
    }
}

private fun CirJsonSchemaObject.findSubSchemasByName(project: Project, name: String): Iterable<CirJsonSchemaObject> {
    return CirJsonSchemaResolver(project, this, CirJsonPointerPosition().apply { addFollowingStep(name) }).resolve()
}

internal fun LookupElementBuilder.prefixedBy(path: SchemaPath?,
        treeWalker: CirJsonLikePsiWalker): LookupElementBuilder {
    return path?.let { "${path.prefix()}.$lookupString" }?.let { lookupString ->
        withPresentableText(lookupString).withLookupString(lookupString).withInsertHandler { context, element ->
            insertHandler?.handleInsert(context, element)
            context.file.findElementAt(context.startOffset)?.sideEffect { completedElement ->
                createMoveData(treeWalker.findContainingObjectAdapter(completedElement), path.accessor(),
                        context.document).performMove(treeWalker, context, completedElement)
            }
        }
    } ?: this
}

private inline fun <T> T.sideEffect(block: (T) -> Unit): Unit = block(this)

private fun createMoveData(objectWhereUserIsWithCursor: CirJsonObjectValueAdapter?, accessor: List<String>,
        document: Document): NestedCompletionMoveData {
    var currentNode = objectWhereUserIsWithCursor
    var i = 0

    while (i < accessor.size) {
        val nextNode = currentNode?.childByName(accessor[i])?.asObject ?: break
        currentNode = nextNode
        i++
    }

    return NestedCompletionMoveData(adjustDestinationIfNeeded(currentNode?.takeUnless { i == 0 }?.delegate, document),
            accessor.subList(i, accessor.size))
}

private fun adjustDestinationIfNeeded(element: PsiElement?, document: Document): PsiElement? {
    element ?: return null
    val text = element.text

    if ((text.contains('{') || text.contains('[')) && !text.contains("\n")) {
        // commit the document before changes
        PsiDocumentManager.getInstance(element.project).commitDocument(document)
        val lbrace = element.childrenOfType<LeafPsiElement>().firstOrNull { it.text == "{" || it.text == "[" }
        if (lbrace != null) {
            val rbraceText = if (lbrace.text == "{") "}" else "]"
            val rbrace = element.childrenOfType<LeafPsiElement>().lastOrNull { it.text == rbraceText }
            if (rbrace != null) {
                element.addBefore(createLeaf("\n", element)!!, rbrace)
            }
            element.addAfter(createLeaf("\n", element)!!, lbrace)
        }
    }

    return element
}

@Suppress("SameParameterValue")
private fun createLeaf(content: String, context: PsiElement): LeafPsiElement? {
    val psiFileFactory = PsiFileFactory.getInstance(context.project)
    return psiFileFactory.createFileFromText("dummy.${context.containingFile.virtualFile.extension}",
            context.containingFile.fileType, content)
            .descendantsOfType<LeafPsiElement>().firstOrNull { it.text == content }
}

private fun NestedCompletionMoveData.performMove(treeWalker: CirJsonLikePsiWalker, context: InsertionContext,
        completedElement: PsiElement) {
    performMove(CompletedRange(context.startOffset, context.selectionEndOffset), treeWalker, completedElement,
            context.editor, context.file)
}

/**
 * We assume to be in a state where the key has been correctly completed without any knowledge of nested completions.
 * So there is 3 things we need to do:
 *  1. We need to move the completed key into the existing nested parent.
 *     Example: [org.jetbrains.yaml.schema.YamlByJsonSchemaHeavyNestedCompletionTest.`test nested completion into
 *     property that does not exist yet`]
 *  2. We need to wrap the completed with the non-existing parents:
 *     Example: [org.jetbrains.yaml.schema.YamlByJsonSchemaHeavyNestedCompletionTest.`test nested completion into existing property`]
 *  3. We need to move the selection that was made by the original completion handler back to the same place.
 *
 *  Step 1 xor 2 will be performed
 *  Limitations:
 *    - While it's language agnostic, it's currently only tested for YAML treeWalker
 *    - It does not nest into arrays
 *    - It does not nest enum values
 *    - Step 3 will currently not be performed in the case of having multiple carets.
 *    - Step 3 will have unpredictable results if the selection spans across multiple lines
 *
 *  @param completedRange This represents the range that has already been completed **before** any nesting has been performed
 */
private fun NestedCompletionMoveData.performMove(completedRange: CompletedRange, treeWalker: CirJsonLikePsiWalker,
        completedElement: PsiElement, editor: Editor, file: PsiFile) {
    val pointer = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file,
            TextRange(completedRange.startOffset, completedRange.endOffsetExclusive))
    PsiDocumentManager.getInstance(file.project).doPostponedOperationsAndUnblockDocument(editor.document)
    val elementRange = pointer.range?.let { CompletedRange(it.startOffset, it.endOffset) } ?: completedRange

    val caretModel = editor.caretModel
    val text = editor.document.charsSequence

    val oldCaretState = caretModel.tryCaptureCaretState(editor, elementRange.startOffset)
    val fileIndent = treeWalker.indentOf(file)

    val (additionalCaretOffset, fullTextWithoutCorrectingNewline) = createTextWrapper(treeWalker,
            wrappingPath).wrapText(text.substring(elementRange.toIntRange()), oldCaretState?.relativeCaretPosition,
            destination?.let {
                treeWalker.indentOf(it) + if (shouldReindentDestinationStart(destination, treeWalker)) fileIndent else 0
            }, (completedElement.manuallyDeterminedIndentIn(text) ?: treeWalker.indentOf(completedElement)), fileIndent)

    val startOfLine = elementRange.startOffset.movedToStartOfLine(text)
    val endOfLine = elementRange.endOffsetExclusive.movedToEndOfLine(text)
    val takePrecedingNewline = startOfLine > 0
    val takeSucceedingNewline = !takePrecedingNewline && endOfLine < editor.document.lastIndex
    val fullText = fullTextWithoutCorrectingNewline
            .letIf(takePrecedingNewline) { '\n' + it }
            .letIf(takeSucceedingNewline) { it + '\n' }

    editor.document.applyChangesOrdered(
            documentChangeAt(startOfLine) {
                replaceString(startOfLine - takePrecedingNewline.toInt(), endOfLine + takeSucceedingNewline.toInt(), "")
            },
            documentChangeAt(
                    offsetOfInsertionLine(destination, completedElement.startOffset, treeWalker).movedToStartOfLine(
                            text)) { insertionOffset ->
                insertString(insertionOffset - takePrecedingNewline.toInt(), fullText)
                oldCaretState
                        ?.restored(editor, insertionOffset + (additionalCaretOffset ?: 0))
                        ?.sideEffect { restoredCaret -> caretModel.caretsAndSelections = listOf(restoredCaret) }
            }
    )
}

private fun CaretModel.tryCaptureCaretState(editor: Editor, relativeOffset: Int): CapturedCaretState? {
    fun Int.captured() = this - relativeOffset
    fun LogicalPosition.toOffset() = editor.logicalPositionToOffset(this)

    return caretsAndSelections.singleOrNull()?.let { caretState ->
        CapturedCaretState(currentCaret.offset.captured(), caretState.selectionStart?.toOffset()?.captured(),
                caretState.selectionEnd?.toOffset()?.captured(), caretState.visualColumnAdjustment)
    }
}

private val Document.lastIndex get() = textLength - 1

private fun createTextWrapper(treeWalker: CirJsonLikePsiWalker, accessor: List<String>): WrappedText? {
    val objectCloser = treeWalker.defaultObjectValue.getOrNull(1)?.toString()?.let { "\n$it" } ?: ""
    val objectOpener = (treeWalker.getPropertyValueSeparator(
            CirJsonSchemaType._object) + " " + (treeWalker.defaultObjectValue.getOrNull(0)?.toString() ?: "")).trimEnd()
    return accessor.asReversed().fold(null as WrappedText?) { acc, nextName ->
        WrappedText("${treeWalker.quoted(nextName)}$objectOpener", acc, objectCloser)
    }
}

private fun CirJsonLikePsiWalker.quoted(name: String): String {
    return if (isRequiringNameQuote) """"$name"""" else name
}

/**
 * Wraps this text wrapper around [around]. Additionally, it provides information about how much inserting
 * this text will need to move the caret if it used to be on position [caretOffset].
 *
 * It assumes that [around] is existing text, and it does not need to be considered for the caret offset.
 * (however newlines in [around] will be adjusted according to the indent. These indents might need to be considered for the caret offset)
 * @param caretOffset The position where the caret is relative to the start of [around] or null if there is no caret to consider.
 */
private fun WrappedText?.wrapText(around: String, caretOffset: Int?, destinationIndent: Int?,
        completedElementIndent: Int, fileIndent: Int): TextWithAdditionalCaretOffset {
    return wrapText(around, caretOffset, destinationIndent ?: completedElementIndent, completedElementIndent,
            fileIndent)
}

private fun WrappedText?.wrapText(around: String, caretOffset: Int?, startIndent: Int, completedElementIndent: Int,
        fileIndent: Int): TextWithAdditionalCaretOffset {
    return textWithoutSuffix(startIndent, fileIndent, caretOffset, completedElementIndent, around).withTextSuffixedBy(
            getFullSuffix(startIndent, fileIndent))
}

/**
 * @param indentOnWhichBodyIsBased represents the indent that [body] is based on. Newlines inside body will be always
 * have this indent
 */
private fun WrappedText?.textWithoutSuffix(indent: Int, fileIndent: Int, caretOffset: Int?,
        indentOnWhichBodyIsBased: Int, body: String): TextWithAdditionalCaretOffset {
    return when (this) {
        null -> {
            TextWithAdditionalCaretOffset(caretOffset?.let {
                // for every newline we insert before the caret, we need to offset the caret later
                body.indicesOf('\n')
                        .takeWhile { newLineOffset -> newLineOffset < caretOffset }
                        .count()
                        .let { numberOfLinesBeforeCaret -> numberOfLinesBeforeCaret * (indent - indentOnWhichBodyIsBased) }
            }, if (indent <= indentOnWhichBodyIsBased) body
            else body.replace("\n", "\n" + " ".repeat(indent - indentOnWhichBodyIsBased)))
        }

        else -> {
            wrapped.textWithoutSuffix(indent + fileIndent, fileIndent, caretOffset, indentOnWhichBodyIsBased, body)
                    .withTextPrefixedBy(" ".repeat(indent) + prefix + "\n")
        }
    }
}

private fun String.indicesOf(char: Char) = indices.asSequence().filter { this[it] == char }

private fun TextWithAdditionalCaretOffset.withTextPrefixedBy(prefix: String): TextWithAdditionalCaretOffset {
    return TextWithAdditionalCaretOffset(offset?.plus(prefix.length), "$prefix$text")
}

private fun TextWithAdditionalCaretOffset.withTextSuffixedBy(text: String): TextWithAdditionalCaretOffset {
    return copy(text = "${this.text}$text")
}

private fun WrappedText?.getFullSuffix(indent: Int, fileIndent: Int): String {
    if (this == null) {
        return ""
    }

    // create a reversed stack of wrappers
    val allLayers = Stack<WrappedText>()
    var wr = this

    while (wr != null) {
        allLayers.push(wr)
        wr = wr.wrapped
    }

    var totalIndent = indent + fileIndent * allLayers.size
    var iteration = 0
    val builder = StringBuilder()

    while (allLayers.isNotEmpty()) {
        allLayers.pop().run {
            iteration += 1
            // the indent is decreasing for each next suffix
            totalIndent -= fileIndent

            // indent every line of the suffix; preserve the first newline but drop other blank lines
            if (suffix.isNotEmpty()) {
                builder.append(suffix.split('\n').filter { iteration == 1 || it.isNotBlank() }
                        .map { " ".repeat(totalIndent) + it }.joinToString("\n") + "\n"
                )
            }
        }
    }

    return builder.toString()
}

private fun CompletedRange.toIntRange() = startOffset until endOffsetExclusive

private fun shouldReindentDestinationStart(destination: PsiElement?, treeWalker: CirJsonLikePsiWalker): Boolean {
    destination ?: return false
    val destAdapter = treeWalker.createValueAdapter(destination)
    return when {
        destAdapter?.asObject != null -> treeWalker.defaultObjectValue.isNotEmpty()
        destAdapter?.asArray != null -> treeWalker.defaultArrayValue.isNotEmpty()
        else -> false
    }
}

private fun PsiElement.manuallyDeterminedIndentIn(text: CharSequence): Int? {
    val offsetOfLineStart = startOffset.movedToStartOfLine(text)
    return offsetOfLineStart.movedToFirstOrNull(text) { !it.isWhitespace() }
            ?.let { offsetOfFirstCharInLine -> offsetOfFirstCharInLine - offsetOfLineStart }
}

private fun Int.movedToStartOfLine(text: CharSequence) =
        (this downTo 0).firstOrNull { text[it] == '\n' }?.let { firstIndexBeforeLine -> firstIndexBeforeLine + 1 } ?: 0

private inline fun Int.movedToFirstOrNull(text: CharSequence, predicate: (Char) -> Boolean) =
        (this..text.lastIndex).firstOrNull { predicate(text[it]) }

private fun Int.movedToEndOfLine(text: CharSequence) = movedToFirstOrNull(text) { it == '\n' } ?: text.length

inline fun <R, U : R, T : R> T.letIf(condition: Boolean, block: (T) -> U): R = if (condition) block(this) else this

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun offsetOfInsertionLine(destination: PsiElement?, originOffset: Int, treeWalker: CirJsonLikePsiWalker): Int {
    // If there is no destination, we insert at the origin
    if (destination == null) {
        return originOffset
    }

    // If we have objects and arrays with start/end syntax like {} or [], we should insert after the start or before the end
    // length/2 is not the best heuristic, but let it be for now
    val destAdapter = treeWalker.createValueAdapter(destination)
    val offsetWithinParent = when {
        destAdapter?.asObject != null -> treeWalker.defaultObjectValue.takeIf { it.isNotEmpty() }?.let { it.length / 2 }
                ?: 0

        destAdapter?.asArray != null -> treeWalker.defaultArrayValue.takeIf { it.isNotEmpty() }?.let { it.length / 2 }
                ?: 0

        else -> 0
    }
    return when {
        destination.startOffset > originOffset -> destination.startOffset + offsetWithinParent // Caret is above destination, let's insert at top
        else -> destination.endOffset - offsetWithinParent // Caret is below destination, let's insert at bottom
    }
}

private fun CapturedCaretState.restored(editor: Editor, newRelativeOffset: Int): CaretState {
    fun Int.restoredLogicalPosition(): LogicalPosition = editor.offsetToLogicalPosition(this + newRelativeOffset)

    return CaretState(
            relativeCaretPosition?.restoredLogicalPosition(),
            visualColumnAdjustment,
            relativeSelectionStart?.restoredLogicalPosition(),
            relativeSelectionEnd?.restoredLogicalPosition(),
    )
}

internal fun CirJsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement {
    return path?.let { findContainingObjectAdapter(start)?.findChildBy(path.accessor(), 0)?.delegate } ?: start
}

private fun CirJsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement): CirJsonObjectValueAdapter? {
    return start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }
}

internal fun CirJsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): CirJsonValueAdapter? {
    return if (offset > path.lastIndex) {
        this
    } else {
        childByName(path[offset])?.asObject?.findChildBy(path, offset + 1)
    }
}

private fun CirJsonObjectValueAdapter.childByName(name: String): CirJsonValueAdapter? {
    return propertyList.firstOrNull { it.name == name }?.values?.firstOrNull()
}


