package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import kotlin.math.max
import kotlin.math.min

class CirJsonSchemaComplianceChecker(private val myRootSchema: CirJsonSchemaObject,
        private val myHolder: ProblemsHolder, private val myWalker: CirJsonLikePsiWalker,
        private val mySession: LocalInspectionToolSession, private val myOptions: CirJsonComplianceCheckerOptions,
        private val myMessagePrefix: String?) {

    constructor(rootSchema: CirJsonSchemaObject, holder: ProblemsHolder, walker: CirJsonLikePsiWalker,
            session: LocalInspectionToolSession, options: CirJsonComplianceCheckerOptions) : this(rootSchema, holder,
            walker, session, options, null)

    fun annotate(element: PsiElement) {
        val project = element.project
        val firstProp = myWalker.getParentPropertyAdapter(element)

        if (firstProp != null) {
            val position = myWalker.findPosition(firstProp.delegate, true) ?: return

            if (position.empty) {
                return
            }

            val result = CirJsonSchemaResolver(project, myRootSchema, position).detailedResolve()

            for (value in firstProp.values) {
                createWarnings(CirJsonSchemaAnnotatorChecker.checkByMatchResult(project, value, result, myOptions))
            }
        }

        checkRoot(element, firstProp)
    }

    private fun createWarnings(checker: CirJsonSchemaAnnotatorChecker?) {
        if (checker == null || checker.isCorrect) {
            return
        }

        val ranges = arrayListOf<TextRange>()
        val entries = arrayListOf<MutableList<Map.Entry<PsiElement, CirJsonValidationError>>>()

        for (entry in checker.errors) {
            val range = myWalker.adjustErrorHighlightingRange(entry.key)
            var processed = false

            for (i in ranges.indices) {
                val currRange = ranges[i]

                if (currRange.intersects(range)) {
                    ranges[i] = TextRange(min(currRange.startOffset, range.startOffset),
                            max(currRange.endOffset, range.endOffset))
                    entries[i].add(entry)
                    processed = true
                    break
                }
            }

            if (processed) {
                continue
            }

            ranges.add(range)
            entries.add(SmartList(entry))
        }

        for (entryList in entries) {
            val min = entryList.map { it.value.priority.ordinal }.minOrNull() ?: Int.MAX_VALUE

            for (entry in entryList) {
                val validationError = entry.value
                val element = entry.key

                if (validationError.priority.ordinal > min) {
                    continue
                }

                var range = myWalker.adjustErrorHighlightingRange(element)
                range = range.shiftLeft(element.textRange.startOffset)
                registerError(element, range, validationError)
            }
        }
    }

    private fun registerError(element: PsiElement, range: TextRange, validationError: CirJsonValidationError) {
        if (checkIfAlreadyProcessed(element)) {
            return
        }

        var value = validationError.message

        if (myMessagePrefix != null) {
            value = "$myMessagePrefix$value"
        }

        val fix = validationError.createFixes(myWalker.getSyntaxAdapter(myHolder.project))

        val psiElement = if (range.isEmpty) element.containingFile else element

        if (fix.isEmpty()) {
            myHolder.registerProblem(psiElement, range, value)
        } else {
            myHolder.registerProblem(psiElement, range, value, *fix)
        }
    }

    private fun checkIfAlreadyProcessed(property: PsiElement): Boolean {
        var data = mySession.getUserData(ANNOTATED_PROPERTIES)

        if (data == null) {
            data = hashSetOf()
            mySession.putUserData(ANNOTATED_PROPERTIES, data)
        }

        if (property in data) {
            return true
        }

        data.add(property)
        return false
    }

    private fun checkRoot(element: PsiElement, firstProp: CirJsonPropertyAdapter?) {
        val rootToCheck = if (firstProp != null) {
            firstProp.parentObject?.takeIf { !myWalker.isTopCirJsonElement(it.delegate.parent) }
        } else {
            findTopLevelElement(myWalker, element)
        } ?: return

        val project = element.project
        val result = CirJsonSchemaResolver(project, myRootSchema).detailedResolve()
        createWarnings(CirJsonSchemaAnnotatorChecker.checkByMatchResult(project, rootToCheck, result, myOptions))
    }

    companion object {

        private val ANNOTATED_PROPERTIES = Key.create<MutableSet<PsiElement>>("CirJsonSchema.Properties.Annotated")

        private fun findTopLevelElement(walker: CirJsonLikePsiWalker, element: PsiElement): CirJsonValueAdapter? {
            val ref = Ref<PsiElement>()
            PsiTreeUtil.findFirstParent(element) {
                val isTop = walker.isTopCirJsonElement(it)

                if (!isTop) {
                    ref.set(it)
                }

                isTop
            }

            return if (ref.isNull) {
                if (walker.isAcceptsEmptyRoot) {
                    walker.createValueAdapter(element)
                } else {
                    null
                }
            } else {
                walker.createValueAdapter(ref.get())
            }
        }

    }

}