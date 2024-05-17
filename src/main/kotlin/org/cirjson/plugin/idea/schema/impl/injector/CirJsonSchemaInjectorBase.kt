package org.cirjson.plugin.idea.schema.impl.injector

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import java.util.*

abstract class CirJsonSchemaInjectorBase : MultiHostInjector {

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return Collections.singletonList(CirJsonStringLiteral::class.java)
    }

    class InjectedLanguageData(val language: Language, val prefix: String?, val suffix: String?) : Injection {

        override fun getInjectedLanguage(): Language {
            return language
        }

        override fun getInjectedLanguageId(): String {
            return language.id
        }

        override fun getPrefix(): String {
            return prefix ?: ""
        }

        override fun getSuffix(): String {
            return suffix ?: ""
        }

        override fun getSupportId(): String? {
            return null
        }

    }

    protected companion object {

        fun injectForHost(registrar: MultiHostRegistrar, host: CirJsonStringLiteral, language: Language) {
            injectForHost(registrar, host, InjectedLanguageData(language, null, null))
        }

        fun injectForHost(registrar: MultiHostRegistrar, host: CirJsonStringLiteral, language: InjectedLanguageData) {
            val fragments = host.textFragments

            if (fragments.isEmpty()) {
                return
            }

            registrar.startInjecting(language.language)

            for (fragment in fragments) {
                registrar.addPlace(language.prefix, language.suffix, host as PsiLanguageInjectionHost, fragment.first)
            }

            registrar.doneInjecting()
        }

    }

}