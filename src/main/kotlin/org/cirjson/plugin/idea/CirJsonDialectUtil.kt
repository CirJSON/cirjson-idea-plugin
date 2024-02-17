package org.cirjson.plugin.idea

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.util.ObjectUtils

object CirJsonDialectUtil {

    fun isStandardCirJson(language: Language): Boolean {
        return language == CirJsonLanguage.INSTANCE
    }

    fun isStandardCirJson(element: PsiElement): Boolean {
        return isStandardCirJson(getLanguageOrDefaultCirJson(element))
    }

    fun getLanguageOrDefaultCirJson(element: PsiElement): Language {
        val file = element.containingFile

        if (file != null) {
            val language = file.language

            if (language is CirJsonLanguage) {
                return language
            }
        }

        return ObjectUtils.coalesce(ObjectUtils.tryCast(element.language, CirJsonLanguage::class.java),
                CirJsonLanguage.INSTANCE)
    }

}