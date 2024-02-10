package org.cirjson.plugin.idea.formatter

import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.CirJsonElementTypes.*

class CirJsonFormattingBuilderModel : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val customSettings = settings.getCustomSettings(CirJsonCodeStyleSettings::class.java)
        val spacingBuilder = createSpacingBuilder(settings)
        val block = CirJsonBlock(null, formattingContext.node, customSettings, null,
                Indent.getSmartIndent(Indent.Type.CONTINUATION), null, spacingBuilder)
        return FormattingModelProvider.createFormattingModelForPsiFile(formattingContext.containingFile, block,
                settings)
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
            val cirJsonSettings = settings.getCustomSettings(CirJsonCodeStyleSettings::class.java)
            val commonSettings = settings.getCommonSettings(CirJsonLanguage.INSTANCE)

            val spacesBeforeComma = if (commonSettings.SPACE_BEFORE_COMMA) 1 else 0
            val spacesBeforeColon = if (cirJsonSettings.SPACE_BEFORE_COLON) 1 else 0
            val spacesAfterColon = if (cirJsonSettings.SPACE_AFTER_COLON) 1 else 0

            return SpacingBuilder(settings, CirJsonLanguage.INSTANCE)
                    .before(COLON).spacing(spacesBeforeColon, spacesBeforeColon, 0, false, 0)
                    .after(COLON).spacing(spacesAfterColon, spacesAfterColon, 0, false, 0)
                    .withinPair(L_BRACKET, R_BRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS, true)
                    .withinPair(L_CURLY, R_CURLY).spaceIf(commonSettings.SPACE_WITHIN_BRACES, true)
                    .before(COMMA).spacing(spacesBeforeComma, spacesBeforeComma, 0, false, 0)
                    .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
        }

    }

}