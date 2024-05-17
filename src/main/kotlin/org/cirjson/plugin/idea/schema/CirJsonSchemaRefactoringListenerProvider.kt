package org.cirjson.plugin.idea.schema

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilBase
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter
import org.cirjson.plugin.idea.CirJsonLanguage

class CirJsonSchemaRefactoringListenerProvider : RefactoringElementListenerProvider {

    override fun getListener(element: PsiElement?): RefactoringElementListener? {
        element ?: return null

        val oldFile = PsiUtilBase.asVirtualFile(element) ?: return null

        if (oldFile.fileType !is LanguageFileType
                || !(oldFile.fileType as LanguageFileType).language.isKindOf(CirJsonLanguage.INSTANCE)) {
            return null
        }

        val project = element.project

        if (project.baseDir == null) {
            return null
        }

        val oldRelativePath = VfsUtil.getRelativePath(oldFile, project.baseDir) ?: return null
        val configuration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)
        return object : UndoRefactoringElementAdapter() {

            override fun refactored(element: PsiElement, oldQualifiedName: String?) {
                val newFile = PsiUtilBase.asVirtualFile(element) ?: return
                val newRelativePath = VfsUtil.getRelativePath(newFile, project.baseDir) ?: return
                configuration.schemaFileMoved(project, oldRelativePath, newRelativePath)
            }

        }
    }

}