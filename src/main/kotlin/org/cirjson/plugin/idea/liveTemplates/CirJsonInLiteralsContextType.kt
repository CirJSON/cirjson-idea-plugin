package org.cirjson.plugin.idea.liveTemplates

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral

class CirJsonInLiteralsContextType : TemplateContextType(CirJsonBundle.message("cirjson.string.values")) {

    override fun isInContext(file: PsiFile, offset: Int): Boolean {
        return file is CirJsonFile &&
                PlatformPatterns.psiElement().inside(CirJsonStringLiteral::class.java)
                        .accepts(file.findElementAt(offset))
    }

}