package org.cirjson.plugin.idea

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class CirJsonBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> {
        return PAIRS
    }

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val PAIRS = arrayOf(
                BracePair(CirJsonElementTypes.L_BRACKET, CirJsonElementTypes.R_BRACKET, true),
                BracePair(CirJsonElementTypes.L_CURLY, CirJsonElementTypes.R_CURLY, true),
        )

    }

}