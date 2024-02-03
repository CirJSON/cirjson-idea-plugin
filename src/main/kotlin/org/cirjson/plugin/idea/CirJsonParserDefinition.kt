package org.cirjson.plugin.idea

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.cirjson.plugin.idea.psi.impl.CirJsonFileImpl

class CirJsonParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer {
        return CirJsonLexer()
    }

    override fun createParser(project: Project): PsiParser {
        return CirJsonParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun getCommentTokens(): TokenSet {
        return CirJsonTokenSets.CIRJSON_COMMENTS
    }

    override fun getStringLiteralElements(): TokenSet {
        return CirJsonTokenSets.STRING_LITERALS
    }

    override fun createElement(astNode: ASTNode): PsiElement {
        return CirJsonElementTypes.Factory.createElement(astNode)
    }

    override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
        return CirJsonFileImpl(fileViewProvider, CirJsonLanguage.INSTANCE)
    }

    override fun spaceExistenceTypeBetweenTokens(astNode: ASTNode?, astNode2: ASTNode?): SpaceRequirements {
        return SpaceRequirements.MAY
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        val FILE = IFileElementType(CirJsonLanguage.INSTANCE)

    }

}