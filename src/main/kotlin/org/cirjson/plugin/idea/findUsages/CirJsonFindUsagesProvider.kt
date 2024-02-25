package org.cirjson.plugin.idea.findUsages

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.psi.CirJsonProperty

class CirJsonFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner {
        return CirJsonWordScanner()
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        return psiElement is PsiNamedElement
    }

    override fun getHelpId(psiElement: PsiElement): String {
        return HelpID.FIND_OTHER_USAGES
    }

    override fun getType(element: PsiElement): String {
        if (element is CirJsonProperty) {
            return CirJsonBundle.message("cirjson.property")
        }

        return ""
    }

    override fun getDescriptiveName(element: PsiElement): String {
        return (if (element is PsiNamedElement) element.name else null) ?: CirJsonBundle.message("unnamed.desc")
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        return getDescriptiveName(element)
    }

}