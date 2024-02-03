package org.cirjson.plugin.idea

import com.intellij.lexer.FlexAdapter

class CirJsonLexer: FlexAdapter(CirJsonFlexLexer())
