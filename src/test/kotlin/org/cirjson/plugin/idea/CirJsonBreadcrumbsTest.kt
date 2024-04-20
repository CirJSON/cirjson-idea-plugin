package org.cirjson.plugin.idea

class CirJsonBreadcrumbsTest : CirJsonTestCase() {

    private fun doTest(vararg components: String) {
        myFixture.configureByFile("breadcrumbs/${getTestName(false)}.cirjson")
        val caret = myFixture.breadcrumbsAtCaret
        assertOrderedEquals(caret.map { it.text }.toTypedArray(), *components)
    }

    fun testComplexItems() {
        doTest("foo", "bar", "0", "0", "baz")
    }

}