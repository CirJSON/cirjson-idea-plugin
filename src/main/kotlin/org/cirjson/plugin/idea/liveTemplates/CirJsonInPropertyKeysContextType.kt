package org.cirjson.plugin.idea.liveTemplates

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonElementTypes
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonPsiUtil
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonInPropertyKeysContextType : TemplateContextType(CirJsonBundle.message("cirjson.property.keys")) {

    override fun isInContext(file: PsiFile, offset: Int): Boolean {
        return file is CirJsonFile && PlatformPatterns.psiElement()
                .inside(PlatformPatterns.psiElement(CirJsonValue::class.java)
                        .with(object : PatternCondition<PsiElement>("insidePropertyKey") {

                            override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
                                return CirJsonPsiUtil.isPropertyKey(element)
                            }

                        })).beforeLeaf(PlatformPatterns.psiElement(CirJsonElementTypes.COLON))
                .accepts(file.findElementAt(offset))
    }

}