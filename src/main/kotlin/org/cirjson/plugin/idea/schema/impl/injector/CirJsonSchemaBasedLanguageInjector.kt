package org.cirjson.plugin.idea.schema.impl.injector

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaResolver

class CirJsonSchemaBasedLanguageInjector : CirJsonSchemaInjectorBase() {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is CirJsonStringLiteral) {
            return
        }

        val language = getLanguageToInject(context, false) ?: return
        injectForHost(registrar, context, language)
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        fun getLanguageToInject(context: PsiElement, relaxPositionCheck: Boolean): InjectedLanguageData? {
            val project = context.project
            val containingFile = context.containingFile
            val schemaObject = CirJsonSchemaService.get(project).getSchemaObject(containingFile) ?: return null
            val walker = CirJsonLikePsiWalker.getWalker(context, schemaObject) ?: return null
            val isName = walker.isName(context)

            if (relaxPositionCheck && isName == ThreeState.YES || !relaxPositionCheck && isName == ThreeState.NO) {
                return null
            }

            val position = walker.findPosition(context, true)

            if (position == null || position.empty) {
                return null
            }

            val schemas = CirJsonSchemaResolver(project, schemaObject, position).resolve()

            for (schema in schemas) {
                val injection = schema.languageInjection ?: continue
                val language = Language.findLanguageByID(injection)

                if (language != null) {
                    return InjectedLanguageData(language, schema.languageInjectionPrefix,
                            schema.languageInjectionSuffix)
                }
            }

            return null
        }

    }

}