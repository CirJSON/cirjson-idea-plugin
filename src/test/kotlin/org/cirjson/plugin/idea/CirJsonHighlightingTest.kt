package org.cirjson.plugin.idea

import org.cirjson.plugin.idea.codeinsight.CirJsonDifferentDataTypesShareIDInspection
import org.cirjson.plugin.idea.codeinsight.CirJsonDuplicatePropertyKeysInspection
import org.cirjson.plugin.idea.codeinsight.CirJsonStandardComplianceInspection

class CirJsonHighlightingTest : CirJsonHighlightingTestBase() {

    override val extension: String = "cirjson"

    private fun enableStandardComplianceInspection(checkComments: Boolean, checkTopLevelValues: Boolean) {
        val inspection = CirJsonStandardComplianceInspection().apply {
            myWarnAboutComments = checkComments
            myWarnAboutMultipleTopLevelValues = checkTopLevelValues
        }
        myFixture.enableInspections(inspection)
    }

    fun testComplianceProblemsLiteralTopLevelValueIsAllowed() {
        enableStandardComplianceInspection(checkComments = true, checkTopLevelValues = false)
        doTest()
    }

    fun testComplianceProblems() {
        enableStandardComplianceInspection(checkComments = true, checkTopLevelValues = true)
        doTestHighlighting(checkInfo = false, checkWeakWarning = true, checkWarning = true)
    }

    fun testDuplicatePropertyKeys() {
        myFixture.enableInspections(CirJsonDuplicatePropertyKeysInspection::class.java)
        doTestHighlighting(checkInfo = false, checkWeakWarning = true, checkWarning = true)
    }

    fun testIncompleteFloatingPointLiteralsWithExponent() {
        doTestHighlighting(checkInfo = false, checkWeakWarning = false, checkWarning = false)
    }

    fun testEmptyIds() {
        enableStandardComplianceInspection(checkComments = false, checkTopLevelValues = false)
        doTestHighlighting(checkInfo = false, checkWeakWarning = false, checkWarning = false)
    }

    fun testDifferentTypesSameID() {
        myFixture.enableInspections(CirJsonDifferentDataTypesShareIDInspection::class.java)
        doTestHighlighting(checkInfo = false, checkWeakWarning = true, checkWarning = true)
    }

}