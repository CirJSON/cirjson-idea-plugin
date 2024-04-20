package org.cirjson.plugin.idea.breadcrumbs

import com.intellij.lang.Language
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.CirJsonUtil
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameKind
import org.cirjson.plugin.idea.navigation.CirJsonQualifiedNameProvider
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaDocumentationProvider
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

class CirJsonBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        return LANGUAGES
    }

    override fun acceptElement(e: PsiElement): Boolean {
        return e is CirJsonProperty || CirJsonUtil.isArrayElement(e)
    }

    override fun getElementInfo(e: PsiElement): String {
        if (e is CirJsonProperty) {
            return e.name
        } else if (CirJsonUtil.isArrayElement(e)) {
            val i = CirJsonUtil.getArrayIndexOfItem(e)

            if (i != -1) {
                return if (i == 0) CirJsonBundle.message("cirjson.id") else (i - 1).toString()
            }
        }

        throw AssertionError(
                "Breadcrumbs can be extracted only from CirJsonProperty elements or CirJsonArray child items")
    }

    override fun getElementTooltip(element: PsiElement): String? {
        return CirJsonSchemaDocumentationProvider.findSchemaAndGenerateDoc(element, null, true, null)
    }

    override fun getContextActions(element: PsiElement): MutableList<out Action> {
        val values = CirJsonQualifiedNameKind.entries
        val actions = ArrayList<Action>(values.size)

        for (kind in values) {
            actions.add(object : AbstractAction(CirJsonBundle.message("cirjson.copy.to.clipboard", kind.toString())) {

                override fun actionPerformed(e: ActionEvent?) {
                    CopyPasteManager.getInstance().setContents(
                            StringSelection(CirJsonQualifiedNameProvider.generateQualifiedName(element, kind)))
                }

            })
        }

        return actions
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val LANGUAGES = arrayOf<Language>(CirJsonLanguage.INSTANCE)

    }

}