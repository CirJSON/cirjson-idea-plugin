package org.cirjson.plugin.idea

import com.intellij.psi.tree.TokenSet
import org.cirjson.plugin.idea.CirJsonElementTypes.*

object CirJsonTokenSets {

    val STRING_LITERALS = TokenSet.create(SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING)

    val CIRJSON_CONTAINERS = TokenSet.create(OBJECT, ARRAY)

    val CIRJSON_KEYWORDS = TokenSet.create(TRUE, FALSE, NULL)

    val CIRJSON_LITERALS = TokenSet.create(ID_KEY_LITERAL, STRING_LITERAL, NUMBER_LITERAL, NULL_LITERAL, TRUE, FALSE)

    val CIRJSON_COMMENTS = TokenSet.create(BLOCK_COMMENT, LINE_COMMENT)

}