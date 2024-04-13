package org.cirjson.plugin.idea

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

}