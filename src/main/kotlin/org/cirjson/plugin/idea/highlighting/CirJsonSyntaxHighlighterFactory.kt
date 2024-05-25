package org.cirjson.plugin.idea.highlighting

import com.intellij.lexer.LayeredLexer
import com.intellij.lexer.Lexer
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonLexer

class CirJsonSyntaxHighlighterFactory : SyntaxHighlighterFactory() {

    private val myLexer = CirJsonLexer()

    override fun getSyntaxHighlighter(p0: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return CirJsonHighlighter(virtualFile)
    }

    private inner class CirJsonHighlighter(private val myFile: VirtualFile?) : SyntaxHighlighterBase() {

        private val ourAttributes = mutableMapOf<IElementType, TextAttributesKey>()

        init {
            fillMap(ourAttributes, CIRJSON_BRACES, CirJsonElementTypes.L_CURLY, CirJsonElementTypes.R_CURLY)
            fillMap(ourAttributes, CIRJSON_BRACKETS, CirJsonElementTypes.L_BRACKET, CirJsonElementTypes.R_BRACKET)
            fillMap(ourAttributes, CIRJSON_COMMA, CirJsonElementTypes.COMMA)
            fillMap(ourAttributes, CIRJSON_COLON, CirJsonElementTypes.COLON)
            fillMap(ourAttributes, CIRJSON_STRING, CirJsonElementTypes.DOUBLE_QUOTED_STRING)
            fillMap(ourAttributes, CIRJSON_STRING, CirJsonElementTypes.SINGLE_QUOTED_STRING)
            fillMap(ourAttributes, CIRJSON_NUMBER, CirJsonElementTypes.NUMBER)
            fillMap(ourAttributes, CIRJSON_KEYWORD, CirJsonElementTypes.TRUE, CirJsonElementTypes.FALSE,
                    CirJsonElementTypes.NULL)
            fillMap(ourAttributes, CIRJSON_LINE_COMMENT, CirJsonElementTypes.LINE_COMMENT)
            fillMap(ourAttributes, CIRJSON_BLOCK_COMMENT, CirJsonElementTypes.BLOCK_COMMENT)
            fillMap(ourAttributes, CIRJSON_IDENTIFIER, CirJsonElementTypes.IDENTIFIER)
            fillMap(ourAttributes, HighlighterColors.BAD_CHARACTER, TokenType.BAD_CHARACTER)

            fillMap(ourAttributes, CIRJSON_VALID_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN)
            fillMap(ourAttributes, CIRJSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN)
            fillMap(ourAttributes, CIRJSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN)
        }

        override fun getHighlightingLexer(): Lexer {
            val layeredLexer = LayeredLexer(this@CirJsonSyntaxHighlighterFactory.myLexer).apply {
                registerSelfStoppingLayer(StringLiteralLexer('\"', CirJsonElementTypes.DOUBLE_QUOTED_STRING, false, "/",
                        false, false), arrayOf(CirJsonElementTypes.DOUBLE_QUOTED_STRING), IElementType.EMPTY_ARRAY)
            }

            return layeredLexer
        }

        override fun getTokenHighlights(type: IElementType?): Array<TextAttributesKey> {
            return pack(ourAttributes[type])
        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val PERMISSIVE_ESCAPES = StringBuilder().apply {
            append("/")
            var c = '\u0001'
            while (c < '\u00ad') {
                if (c != 'x' && c != 'u' && !c.isDigit() && c != '\n' && c != '\r') {
                    append(c)
                }
                c++
            }
        }.toString()

        val CIRJSON_BRACKETS = TextAttributesKey.createTextAttributesKey("CIRJSON.BRACKETS", BRACKETS)

        val CIRJSON_BRACES = TextAttributesKey.createTextAttributesKey("CIRJSON.BRACES", BRACES)

        val CIRJSON_COMMA = TextAttributesKey.createTextAttributesKey("CIRJSON.COMMA", COMMA)

        val CIRJSON_COLON = TextAttributesKey.createTextAttributesKey("CIRJSON.COLON", SEMICOLON)

        val CIRJSON_NUMBER = TextAttributesKey.createTextAttributesKey("CIRJSON.NUMBER", NUMBER)

        val CIRJSON_STRING = TextAttributesKey.createTextAttributesKey("CIRJSON.STRING", STRING)

        val CIRJSON_ID = TextAttributesKey.createTextAttributesKey("CIRJSON.ID", GLOBAL_VARIABLE)

        val CIRJSON_KEYWORD = TextAttributesKey.createTextAttributesKey("CIRJSON.KEYWORD", KEYWORD)

        val CIRJSON_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("CIRJSON.LINE_COMMENT", LINE_COMMENT)

        val CIRJSON_BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("CIRJSON.BLOCK_COMMENT", BLOCK_COMMENT)

        val CIRJSON_IDENTIFIER = TextAttributesKey.createTextAttributesKey("CIRJSON.IDENTIFIER", IDENTIFIER)

        val CIRJSON_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("CIRJSON.PROPERTY_KEY", INSTANCE_FIELD)

        val CIRJSON_VALID_ESCAPE =
                TextAttributesKey.createTextAttributesKey("CIRJSON.VALID_ESCAPE", VALID_STRING_ESCAPE)

        val CIRJSON_INVALID_ESCAPE =
                TextAttributesKey.createTextAttributesKey("CIRJSON.INVALID_ESCAPE", INVALID_STRING_ESCAPE)

        val CIRJSON_PARAMETER = TextAttributesKey.createTextAttributesKey("CIRJSON.PARAMETER", KEYWORD)

    }

}