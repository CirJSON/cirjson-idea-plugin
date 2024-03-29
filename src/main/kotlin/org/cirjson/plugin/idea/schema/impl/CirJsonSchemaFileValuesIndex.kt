package org.cirjson.plugin.idea.schema.impl

import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.CirJsonLexer

class CirJsonSchemaFileValuesIndex {

    companion object {

        val INDEX_ID = ID.create<String, String>("cirJson.file.root.values")

        const val NULL = "\$NULL$"

        const val SCHEMA_PROPERTY_NAME = "\$schema"

        fun getCachedValue(project: Project, file: VirtualFile, requestedKey: String): String? {
            if (project.isDisposed || !file.isValid || DumbService.isDumb(project)) {
                return NULL
            }

            return FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project)[requestedKey]
        }

        fun readTopLevelProps(fileType: FileType, content: CharSequence): Map<String, String> {
            if (fileType !is CirJsonFileType) {
                return HashMap()
            }

            val lexer = CirJsonLexer()
            val map = HashMap<String, String>()
            lexer.start(content)

            var nesting = 0
            var idFound = false
            var obsoleteIdFound = false
            var schemaFound = false

            while (!(idFound && schemaFound && obsoleteIdFound) && lexer.tokenStart < lexer.bufferEnd) {
                val token = lexer.tokenType

                if (token == CirJsonElementTypes.L_CURLY) {
                    nesting++
                } else if (token == CirJsonElementTypes.R_CURLY) {
                    nesting--
                } else if (nesting == 1
                        && (token == CirJsonElementTypes.DOUBLE_QUOTED_STRING
                                || token == CirJsonElementTypes.SINGLE_QUOTED_STRING
                                || token == CirJsonElementTypes.IDENTIFIER)) {
                    when (lexer.tokenText) {
                        "\$id", "\"\$id\"", "'\$id'" -> {
                            idFound = idFound or captureValueIfString(lexer, map, CirJsonCachedValues.ID_CACHE_KEY)
                        }

                        "id", "\"id\"", "'id'" -> {
                            obsoleteIdFound = obsoleteIdFound or captureValueIfString(lexer, map,
                                    CirJsonCachedValues.OBSOLETE_ID_CACHE_KEY)
                        }

                        SCHEMA_PROPERTY_NAME, "\"\$schema\"", "'\$schema'" -> {
                            schemaFound =
                                    schemaFound or captureValueIfString(lexer, map, CirJsonCachedValues.URL_CACHE_KEY)
                        }
                    }
                }

                lexer.advance()
            }

            map.putIfAbsent(CirJsonCachedValues.ID_CACHE_KEY, NULL)
            map.putIfAbsent(CirJsonCachedValues.OBSOLETE_ID_CACHE_KEY, NULL)
            map.putIfAbsent(CirJsonCachedValues.URL_CACHE_KEY, NULL)

            return map
        }

        private fun captureValueIfString(lexer: Lexer, destMap: HashMap<String, String>, key: String): Boolean {
            lexer.advance()
            var token = skipWhitespacesAndGetTokenType(lexer)

            if (token == CirJsonElementTypes.COLON) {
                lexer.advance()
                token = skipWhitespacesAndGetTokenType(lexer)

                if (token == CirJsonElementTypes.DOUBLE_QUOTED_STRING || token == CirJsonElementTypes.SINGLE_QUOTED_STRING) {
                    val text = lexer.tokenText
                    destMap[key] = if (text.length <= 1) "" else text.substring(1, text.length - 1)
                    return true
                }
            }

            return false
        }

        private fun skipWhitespacesAndGetTokenType(lexer: Lexer): IElementType? {
            while (lexer.tokenType == TokenType.WHITE_SPACE || lexer.tokenType == CirJsonElementTypes.LINE_COMMENT
                    || lexer.tokenType == CirJsonElementTypes.BLOCK_COMMENT) {
                lexer.advance()
            }

            return lexer.tokenType
        }

    }

}