package org.cirjson.plugin.idea.findUsages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.CirJsonLexer
import org.cirjson.plugin.idea.CirJsonTokenSets

class CirJsonWordScanner : DefaultWordsScanner(CirJsonLexer(), TokenSet.create(CirJsonElementTypes.IDENTIFIER),
        CirJsonTokenSets.CIRJSON_COMMENTS, CirJsonTokenSets.CIRJSON_LITERALS) {

    init {
        setMayHaveFileRefsInLiterals(true)
    }

}