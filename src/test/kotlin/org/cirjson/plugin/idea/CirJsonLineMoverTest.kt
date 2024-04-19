package org.cirjson.plugin.idea

import com.intellij.openapi.actionSystem.IdeActions

class CirJsonLineMoverTest : CirJsonTestCase() {

    private fun doTest(down: Boolean) {
        val testName = getTestName(false)
        val action = if (down) {
            IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION
        } else {
            IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION
        }
        val after = if (down) {
            "afterDown"
        } else {
            "afterUp"
        }

        myFixture.configureByFile("mover/$testName.cirjson")
        myFixture.performEditorAction(action)
        myFixture.checkResultByFile("mover/$testName.$after.cirjson")
    }

    fun testLastArrayElementMovedUp() {
        doTest(false)
    }

    fun testLastObjectPropertyMovedUp() {
        doTest(false)
    }

    fun testArraySelectionMovedDown() {
        doTest(true)
    }

    fun testOutOfScopeHasProp() {
        doTest(true)
    }

    fun testOutOfScopeNoFollowing() {
        doTest(true)
    }

    fun testStatementSetMovedSameLevelDown() {
        doTest(true)
    }

    fun testStatementSetMovedSameLevelUp() {
        doTest(false)
    }

    fun testIntoScope() {
        doTest(false)
    }

    fun testFromUpperIntoScope() {
        doTest(true)
    }

    fun testOutsideArray() {
        doTest(true)
    }

    fun testInsideArray() {
        doTest(false)
    }

    fun testObjectSelectionMovedDown() {
        doTest(true)
    }

}
