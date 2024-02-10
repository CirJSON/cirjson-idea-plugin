package org.cirjson.plugin.idea.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import org.cirjson.plugin.idea.CirJsonElementTypes.*
import org.cirjson.plugin.idea.CirJsonTokenSets.CIRJSON_CONTAINERS
import org.cirjson.plugin.idea.formatter.CirJsonCodeStyleSettings.Companion.ALIGN_PROPERTY_ON_COLON
import org.cirjson.plugin.idea.formatter.CirJsonCodeStyleSettings.Companion.ALIGN_PROPERTY_ON_VALUE
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil.hasElementType

class CirJsonBlock(private val myParent: CirJsonBlock?, private val myNode: ASTNode,
        private val myCustomSettings: CirJsonCodeStyleSettings, private val myAlignment: Alignment?,
        private val myIndent: Indent, private val myWrap: Wrap?, private val mySpacingBuilder: SpacingBuilder) :
        ASTBlock {

    private lateinit var mySubBlocks: MutableList<Block>

    private val myPsiElement = myNode.psi

    private val myChildWrap = when (myPsiElement) {
        is CirJsonObject -> Wrap.createWrap(myCustomSettings.OBJECT_WRAPPING, true)
        is CirJsonArray -> Wrap.createWrap(myCustomSettings.ARRAY_WRAPPING, true)
        else -> null
    }

    private val myPropertyValueAlignment = if (myPsiElement is CirJsonObject) {
        Alignment.createAlignment(true)
    } else {
        null
    }

    override fun getNode(): ASTNode {
        return myNode
    }

    override fun getTextRange(): TextRange {
        return myNode.textRange
    }

    override fun getSubBlocks(): MutableList<Block> {
        if (!this::mySubBlocks.isInitialized) {
            val propertyAlignment = myCustomSettings.PROPERTY_ALIGNMENT
            val children = myNode.getChildren(null)
            mySubBlocks = ArrayList(children.size)

            for (child in children) {
                if (isWhitespaceOrEmpty(child)) {
                    continue
                }

                mySubBlocks.add(makeSubBlock(child, propertyAlignment))
            }
        }

        return mySubBlocks
    }

    private fun makeSubBlock(childNode: ASTNode, propertyAlignment: Int): Block {
        var indent = Indent.getNoneIndent()
        var alignment: Alignment? = null
        var wrap: Wrap? = null

        if (hasElementType(myNode, CIRJSON_CONTAINERS)) {
            if (hasElementType(childNode, COMMA)) {
                wrap = Wrap.createWrap(WrapType.NONE, true)
            } else if (!hasElementType(childNode, CIRJSON_ALL_BRACES)) {
                assert(myChildWrap != null)
                wrap = myChildWrap
                indent = Indent.getNormalIndent()
            } else if (hasElementType(childNode, CIRJSON_OPEN_BRACES)) {
                if (CirJsonPsiUtil.isPropertyValue(myPsiElement) && propertyAlignment == ALIGN_PROPERTY_ON_VALUE) {
                    assert(myParent?.myParent?.myPropertyValueAlignment != null)
                    alignment = myParent!!.myParent!!.myPropertyValueAlignment
                }
            }
        } else if (hasElementType(myNode, PROPERTY)) {
            assert(myParent?.myPropertyValueAlignment != null)
            if (hasElementType(childNode, COLON) && propertyAlignment == ALIGN_PROPERTY_ON_COLON) {
                alignment = myParent!!.myPropertyValueAlignment
            } else if (CirJsonPsiUtil.isPropertyValue(childNode.psi) && propertyAlignment == ALIGN_PROPERTY_ON_VALUE) {
                if (!hasElementType(childNode, CIRJSON_CONTAINERS)) {
                    alignment = myParent!!.myPropertyValueAlignment
                }
            }
        }

        return CirJsonBlock(this, childNode, myCustomSettings, alignment, indent, wrap, mySpacingBuilder)
    }

    override fun getWrap(): Wrap? {
        return myWrap
    }

    override fun getIndent(): Indent {
        return myIndent
    }

    override fun getAlignment(): Alignment? {
        return myAlignment
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return mySpacingBuilder.getSpacing(this, child1, child2)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return if (hasElementType(myNode, CIRJSON_CONTAINERS)) {
            ChildAttributes(Indent.getNormalIndent(), null)
        } else if (myNode.psi is PsiFile) {
            ChildAttributes(Indent.getNoneIndent(), null)
        } else {
            ChildAttributes(null, null)
        }
    }

    override fun isIncomplete(): Boolean {
        val lastChildNode = myNode.lastChildNode

        return if (hasElementType(myNode, OBJECT)) {
            lastChildNode != null && lastChildNode.elementType == R_CURLY
        } else if (hasElementType(myNode, ARRAY)) {
            lastChildNode != null && lastChildNode.elementType == R_BRACKET
        } else if (hasElementType(myNode, PROPERTY)) {
            (myPsiElement as CirJsonProperty).value == null
        } else {
            false
        }
    }

    override fun isLeaf(): Boolean {
        return myNode.firstChildNode == null
    }

    companion object {

        private val CIRJSON_OPEN_BRACES = TokenSet.create(L_BRACKET, L_CURLY)

        private val CIRJSON_CLOSE_BRACES = TokenSet.create(R_BRACKET, R_CURLY)

        private val CIRJSON_ALL_BRACES = TokenSet.orSet(CIRJSON_OPEN_BRACES, CIRJSON_CLOSE_BRACES)

        private fun isWhitespaceOrEmpty(node: ASTNode): Boolean {
            return node.elementType == TokenType.WHITE_SPACE || node.textLength == 0
        }

    }

}