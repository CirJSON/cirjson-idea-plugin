package org.cirjson.plugin.idea

abstract class CirJsonHighlightingTestBase : CirJsonTestCase() {

    protected fun doTest() {
        doTestHighlighting(checkInfo = true, checkWeakWarning = true, checkWarning = true)
    }

    protected abstract val extension: String

    protected fun doTestHighlighting(checkInfo: Boolean, checkWeakWarning: Boolean, checkWarning: Boolean) {
        myFixture.testHighlighting(checkWarning, checkInfo, checkWeakWarning,
                "/highlighting/${getTestName(false)}.$extension")
    }

}