package org.cirjson.plugin.idea.schema.impl

import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.extension.CirJsonErrorPriority

class CirJsonValidationError(val message: String, val fixableIssueKind: FixableIssueKind, val issueData: IssueData?,
        val priority: CirJsonErrorPriority) {

    enum class FixableIssueKind {

        MissingProperty,

        MissingOptionalProperty,

        MissingOneOfProperty,

        MissingAnyOfProperty,

        ProhibitedProperty,

        NonEnumValue,

        ProhibitedType,

        TypeMismatch,

        None

    }

    interface IssueData

    data class ProhibitedPropertyIssueData(val propertyName: String) : IssueData

    data class TypeMismatchIssueData(val expectedTypes: Array<CirJsonSchemaType?>) : IssueData {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != other?.javaClass) {
                return false
            }

            other as TypeMismatchIssueData

            return expectedTypes.contentEquals(other.expectedTypes)
        }

        override fun hashCode(): Int {
            return expectedTypes.contentHashCode()
        }

    }

    class MissingMultiplePropsIssueData(val myMissingPropertyIssues: Collection<MissingPropertyIssueData>) : IssueData {

        fun getMessage(trimIfNeeded: Boolean): String {
            if (myMissingPropertyIssues.size == 1) {
                val prop = myMissingPropertyIssues.first()
                return CirJsonBundle.message("schema.validation.property", getPropertyNameWithComment(prop))
            }

            var namesToDisplay = myMissingPropertyIssues as? MutableCollection<MissingPropertyIssueData>
                    ?: myMissingPropertyIssues.toCollection(ArrayList(myMissingPropertyIssues.size))
            var trimmed = false

            if (trimIfNeeded && namesToDisplay.size > 3) {
                namesToDisplay = arrayListOf()
                val iterator = namesToDisplay.iterator()

                for (i in 0..<3) {
                    namesToDisplay.add(iterator.next())
                }

                trimmed = true
            }

            var allNames = myMissingPropertyIssues.map(MissingMultiplePropsIssueData::getPropertyNameWithComment)
                    .sortedWith { s1, s2 ->
                        val firstHasEq = "=" in s1
                        val secondHasEq = "=" in s2

                        if (firstHasEq == secondHasEq) {
                            s1.compareTo(s2)
                        } else if (firstHasEq) {
                            -1
                        } else {
                            1
                        }
                    }.joinToString(", ")

            if (trimmed) {
                allNames += ", ..."
            }

            return CirJsonBundle.message("schema.validation.property", allNames)
        }

        companion object {

            private fun getPropertyNameWithComment(prop: MissingPropertyIssueData): String {
                var comment = ""

                if (prop.enumItemsCount == 1) {
                    comment = " = ${prop.defaultValue}"
                }

                return "'${prop.propertyName}'$comment"
            }

        }

    }

    data class MissingPropertyIssueData(val propertyName: String, val propertyType: CirJsonSchemaType,
            val defaultValue: Any, val enumItemsCount: Int) : IssueData

    data class MissingOneOfPropsIssueData(val exclusiveOptions: Collection<MissingMultiplePropsIssueData>) : IssueData

}