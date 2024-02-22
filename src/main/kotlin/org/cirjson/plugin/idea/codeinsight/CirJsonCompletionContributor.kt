package org.cirjson.plugin.idea.codeinsight

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, AFTER_COLON_IN_PROPERTY, MyKeywordsCompletionProvider.INSTANCE)
        extend(CompletionType.BASIC, AFTER_COMMA_OR_BRACKET_IN_ARRAY, MyKeywordsCompletionProvider.INSTANCE)
    }

    private class MyKeywordsCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                result: CompletionResultSet) {
            for (keyword in KEYWORDS) {
                result.addElement(LookupElementBuilder.create(keyword).bold())
            }
        }

        companion object {

            val INSTANCE = MyKeywordsCompletionProvider()

            val KEYWORDS = arrayOf("null", "true", "false")

        }

    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        val AFTER_COLON_IN_PROPERTY = PlatformPatterns.psiElement()
                .afterLeaf(":").withSuperParent(2, CirJsonProperty::class.java)
                .andNot(PlatformPatterns.psiElement().withParent(CirJsonStringLiteral::class.java))

        val AFTER_COMMA_OR_BRACKET_IN_ARRAY = PlatformPatterns.psiElement()
                .afterLeaf("[", ",").withSuperParent(2, CirJsonArray::class.java)
                .andNot(PlatformPatterns.psiElement().withParent(CirJsonStringLiteral::class.java))

    }

}