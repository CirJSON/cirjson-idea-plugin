package org.cirjson.plugin.idea

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonSpellcheckerStrategy : SpellcheckingStrategy() {

    val myStringLiteralTokenizer = object : Tokenizer<CirJsonStringLiteral>() {

        override fun tokenize(element: CirJsonStringLiteral, consumer: TokenConsumer) {
            val textSplitter = PlainTextSplitter.getInstance()

            if (!element.textContains('\\')) {
                consumer.consumeToken(element, textSplitter)
                return
            }

            val fragments = element.textFragments

            for (fragment in fragments) {
                val fragmentRange = fragment.first
                val escaped = fragment.second

                // Fragment without escaping, also not a broken escape sequence or a unicode code point
                if (escaped.length == fragmentRange.length && !escaped.startsWith("\\")) {
                    consumer.consumeToken(element, escaped, false, fragmentRange.startOffset, TextRange.allOf(escaped),
                            textSplitter)
                }
            }
        }

    }

    override fun getTokenizer(element: PsiElement?): Tokenizer<*> {
        if (element is CirJsonStringLiteral) {
            if (isInjectedLanguageFragment(element)) {
                return EMPTY_TOKENIZER
            }

            return if (CirJsonSchemaSpellcheckerClientForCirJson(element).matchesNameFromSchema()) {
                EMPTY_TOKENIZER
            } else {
                myStringLiteralTokenizer
            }
        }

        return super.getTokenizer(element)
    }

    private class CirJsonSchemaSpellcheckerClientForCirJson(override val element: CirJsonStringLiteral) :
            CirJsonSchemaSpellcheckerClient() {

        override val value: String
            get() {
                return element.value
            }

    }

}