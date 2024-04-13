package org.cirjson.plugin.idea

import com.intellij.application.options.CodeStyle
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.cirjson.plugin.idea.formatter.CirJsonCodeStyleSettings

@TestDataPath("\$PROJECT_ROOT/src/test/resources")
abstract class CirJsonTestCase : BasePlatformTestCase() {

    val codeStyleSettings: CodeStyleSettings
        get() = CodeStyle.getSettings(project)

    val commonCodeStyleSettings: CommonCodeStyleSettings
        get() = codeStyleSettings.getCommonSettings(CirJsonLanguage.INSTANCE)

    val customCodeStyleSettings: CirJsonCodeStyleSettings
        get() = codeStyleSettings.getCustomSettings(CirJsonCodeStyleSettings::class.java)

    val indentOptions: CommonCodeStyleSettings.IndentOptions
        get() = commonCodeStyleSettings.indentOptions!!

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

}