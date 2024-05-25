package org.cirjson.plugin.idea.structureView

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty

class CirJsonStructureViewModel(psiFile: PsiFile, editor: Editor?) :
        StructureViewModelBase(psiFile, editor, CirJsonStructureViewElement(psiFile as CirJsonFile)),
        StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(CirJsonFile::class.java, CirJsonProperty::class.java, CirJsonObject::class.java,
                CirJsonArray::class.java)
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
        return false
    }

    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
        return false
    }

}