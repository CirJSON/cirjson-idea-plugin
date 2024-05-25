package org.cirjson.plugin.idea.formatter

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage
import java.lang.reflect.Field

class CirJsonLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions("SPACE_WITHIN_BRACKETS", "SPACE_WITHIN_BRACES", "SPACE_AFTER_COMMA",
                        "SPACE_BEFORE_COMMA")
                consumer.renameStandardOption("SPACE_WITHIN_BRACES",
                        CirJsonBundle.message("formatter.space_within_braces.label"))
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "SPACE_BEFORE_COLON",
                        CirJsonBundle.message("formatter.space_before_colon.label"), getInstance().SPACES_OTHER)
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "SPACE_AFTER_COLON",
                        CirJsonBundle.message("formatter.space_after_colon.label"), getInstance().SPACES_OTHER)
            }

            SettingsType.BLANK_LINES_SETTINGS -> consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE")
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING", "KEEP_LINE_BREAKS", "WRAP_LONG_LINES")
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "KEEP_TRAILING_COMMA",
                        CirJsonBundle.message("formatter.trailing_comma.label"), getInstance().WRAPPING_KEEP)
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "ARRAY_WRAPPING",
                        CirJsonBundle.message("formatter.wrapping_arrays.label"), null, getInstance().WRAP_OPTIONS,
                        CodeStyleSettingsCustomizable.WRAP_VALUES)
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "OBJECT_WRAPPING",
                        CirJsonBundle.message("formatter.objects.label"), null, getInstance().WRAP_OPTIONS,
                        CodeStyleSettingsCustomizable.WRAP_VALUES)
                consumer.showCustomOption(CirJsonCodeStyleSettings::class.java, "PROPERTY_ALIGNMENT",
                        CirJsonBundle.message("formatter.align.properties.caption"),
                        CirJsonBundle.message("formatter.objects.label"), Holder.ALIGN_OPTIONS, Holder.ALIGN_VALUES)
            }

            else -> {}
        }
    }

    override fun getLanguage(): Language {
        return CirJsonLanguage.INSTANCE
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor {
        return SmartIndentOptionsEditor()
    }

    override fun getCodeSample(settingsType: SettingsType): String {
        return Holder.SAMPLE
    }

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings,
            indentOptions: CommonCodeStyleSettings.IndentOptions) {
        indentOptions.INDENT_SIZE = 2
        // strip all blank lines by default
        commonSettings.KEEP_BLANK_LINES_IN_CODE = 0
    }

    @Suppress("UnstableApiUsage")
    override fun getAccessor(codeStyleObject: Any, field: Field): CodeStyleFieldAccessor<Int, String>? {
        if (codeStyleObject is CirJsonCodeStyleSettings && field.name == "PROPERTY_ALIGNMENT") {
            return MagicIntegerConstAccessor(codeStyleObject, field,
                    intArrayOf(
                            CirJsonCodeStyleSettings.PropertyAlignment.DO_NOT_ALIGN.id,
                            CirJsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_VALUE.id,
                            CirJsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_COLON.id,
                    ), arrayOf("do_not_align", "align_on_value", "align_on_colon"))
        }

        return null
    }

    private class Holder {

        companion object {

            val ALIGN_OPTIONS = CirJsonCodeStyleSettings.PropertyAlignment.entries
                    .map { alignment -> alignment.description }.toTypedArray()

            val ALIGN_VALUES = CirJsonCodeStyleSettings.PropertyAlignment.entries.map { alignment -> alignment.id }
                    .toIntArray()

            val SAMPLE = """
                {
                    "__cirJsonId__": "1",
                    "cirjson literals are": {
                        "__cirJsonId__": "2",
                        "strings": ["3", "foo", "bar", "\u0062\u0061\u0072"],
                        "numbers": ["4", 42, 6.62606975e-34],
                        "boolean values": ["5", true, false,],
                        "objects": {"__cirJsonId__": "6","null": null,"another": null,}
                    }
                }
            """.trimIndent()

        }

    }

}