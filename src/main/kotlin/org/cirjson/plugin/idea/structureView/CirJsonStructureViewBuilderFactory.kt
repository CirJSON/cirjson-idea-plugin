package org.cirjson.plugin.idea.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.psi.CirJsonFile

class CirJsonStructureViewBuilderFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is CirJsonFile) {
            return null
        }

        return object : TreeBasedStructureViewBuilder() {

            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return CirJsonStructureViewModel(psiFile, editor)
            }

        }
    }

}