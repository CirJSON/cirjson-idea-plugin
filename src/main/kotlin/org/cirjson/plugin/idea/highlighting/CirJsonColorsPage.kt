package org.cirjson.plugin.idea.highlighting

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory
import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.RainbowColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_BLOCK_COMMENT
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_BRACES
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_BRACKETS
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_COLON
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_COMMA
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_INVALID_ESCAPE
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_KEYWORD
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_LINE_COMMENT
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_NUMBER
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_PARAMETER
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_PROPERTY_KEY
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_STRING
import org.cirjson.plugin.idea.highlighting.CirJsonSyntaxHighlighterFactory.Companion.CIRJSON_VALID_ESCAPE
import org.cirjson.plugin.idea.icons.CirJsonIcons
import javax.swing.Icon

class CirJsonColorsPage : RainbowColorSettingsPage, DisplayPrioritySortable {

    override fun getIcon(): Icon {
        return CirJsonIcons.ICON
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(CirJsonLanguage.INSTANCE, null, null)
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        return ourAdditionalHighlighting
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return ourAttributeDescriptors
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return ColorDescriptor.EMPTY_ARRAY
    }

    override fun getDisplayName(): String {
        return CirJsonBundle.message("settings.display.name.cirjson")
    }

    override fun getPriority(): DisplayPriority {
        return DisplayPriority.LANGUAGE_SETTINGS
    }

    override fun isRainbowType(type: TextAttributesKey?): Boolean {
        return CIRJSON_PROPERTY_KEY == type || CIRJSON_BRACES == type || CIRJSON_BRACKETS == type ||
                CIRJSON_STRING == type || CIRJSON_NUMBER == type || CIRJSON_KEYWORD == type
    }

    override fun getLanguage(): Language {
        return CirJsonLanguage.INSTANCE
    }

    override fun getDemoText(): String {
        return """
          {
            // Line comments are not included in standard but nonetheless allowed.
            /* As well as block comments. */
            "__cirJsonId__": "1",
            <propertyKey>"the only keywords are"</propertyKey>: ["2", true, false, null],
            <propertyKey>"strings with"</propertyKey>: {
              "__cirJsonId__": "3",
              <propertyKey>"no escapes"</propertyKey>: "pseudopolinomiality"
              <propertyKey>"valid escapes"</propertyKey>: "C-style\r\n and unicode\u0021",
              <propertyKey>"illegal escapes"</propertyKey>: "\0377\x\"
            },
            <propertyKey>"some numbers"</propertyKey>: [
              "4",
              42,
              -0.0e-0,
              6.626e-34
            ]
          }
        """.trimIndent()
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val ourAdditionalHighlighting = mapOf("propertyKey" to CIRJSON_PROPERTY_KEY)

        private val ourAttributeDescriptors = arrayOf(
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.property.key"),
                        CIRJSON_PROPERTY_KEY),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.braces"), CIRJSON_BRACES),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.brackets"), CIRJSON_BRACKETS),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.comma"), CIRJSON_COMMA),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.colon"), CIRJSON_COLON),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.number"), CIRJSON_NUMBER),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.string"), CIRJSON_STRING),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.keyword"), CIRJSON_KEYWORD),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.line.comment"),
                        CIRJSON_LINE_COMMENT),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.block.comment"),
                        CIRJSON_BLOCK_COMMENT),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.valid.escape.sequence"),
                        CIRJSON_VALID_ESCAPE),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.invalid.escape.sequence"),
                        CIRJSON_INVALID_ESCAPE),
                AttributesDescriptor(CirJsonBundle.messagePointer("color.page.attribute.parameter"), CIRJSON_PARAMETER)
        )

    }

}