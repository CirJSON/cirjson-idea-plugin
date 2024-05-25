package org.cirjson.plugin.idea.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.PsiTreeUtil
import org.cirjson.plugin.idea.psi.CirJsonFile
import org.cirjson.plugin.idea.psi.CirJsonValue

class CirJsonFileImpl(fileViewProvider: FileViewProvider, language: Language) : PsiFileBase(fileViewProvider, language),
        CirJsonFile {

    override fun getFileType(): FileType {
        return viewProvider.fileType
    }

    override val topLevelValue: CirJsonValue?
        get() = PsiTreeUtil.getChildOfType(this, CirJsonValue::class.java)

    override val allTopLevelValue: List<CirJsonValue>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, CirJsonValue::class.java)

    override fun toString(): String {
        return "CirJsonFile: $name"
    }

}