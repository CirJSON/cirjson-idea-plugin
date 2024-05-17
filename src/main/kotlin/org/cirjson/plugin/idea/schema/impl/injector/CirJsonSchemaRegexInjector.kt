package org.cirjson.plugin.idea.schema.impl.injector

import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.impl.CirJsonOriginalPsiWalker
import org.intellij.lang.regexp.ecmascript.EcmaScriptRegexpLanguage

class CirJsonSchemaRegexInjector : CirJsonSchemaInjectorBase() {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is CirJsonStringLiteral) {
            return
        }

        if (!CirJsonSchemaService.isSchemaFile(context.containingFile)) {
            return
        }

        val walker = CirJsonOriginalPsiWalker.INSTANCE
        val isName = walker.isName(context)
        val position = walker.findPosition(context, isName == ThreeState.NO)

        if (position == null || position.empty) {
            return
        }

        if (isName == ThreeState.YES) {
            if (position.lastName == "patternProperties") {
                if (isNestedInPropertiesList(position)) {
                    return
                }

                injectForHost(registrar, context, EcmaScriptRegexpLanguage.INSTANCE)
            }
        } else if (isName == ThreeState.NO) {
            if (position.lastName == "pattern") {
                if (isNestedInPropertiesList(position)) {
                    return
                }

                injectForHost(registrar, context, EcmaScriptRegexpLanguage.INSTANCE)
            }
        }
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private fun isNestedInPropertiesList(position: CirJsonPointerPosition): Boolean {
            val skipped = position.trimTail(1)
            return skipped?.lastName == "properties"
        }

    }

}