package org.cirjson.plugin.idea.formatter

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage

class CirJsonCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

    override fun createConfigurable(settings: CodeStyleSettings,
            modelSettings: CodeStyleSettings): CodeStyleConfigurable {
        return object : CodeStyleAbstractConfigurable(settings, modelSettings,
                CirJsonBundle.message("settings.display.name.cirjson")) {

            override fun createPanel(p0: CodeStyleSettings): CodeStyleAbstractPanel {
                val language = CirJsonLanguage.INSTANCE
                val currentSettings = this.currentSettings

                return object : TabbedLanguageCodeStylePanel(language, currentSettings, settings) {

                    override fun initTabs(settings: CodeStyleSettings?) {
                        addIndentOptionsTab(settings)
                        addSpacesTab(settings)
                        addBlankLinesTab(settings)
                        addWrappingAndBracesTab(settings)
                    }

                }
            }

        }
    }

    override fun getConfigurableDisplayName(): String {
        return CirJsonLanguage.INSTANCE.displayName
    }

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return CirJsonCodeStyleSettings(settings)
    }

    override fun getLanguage(): Language {
        return CirJsonLanguage.INSTANCE
    }

}