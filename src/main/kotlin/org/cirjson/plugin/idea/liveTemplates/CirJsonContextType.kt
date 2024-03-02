package org.cirjson.plugin.idea.liveTemplates

import com.intellij.codeInsight.template.FileTypeBasedContextType
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.psi.CirJsonFile

class CirJsonContextType :
        FileTypeBasedContextType(CirJsonBundle.message("cirjson.template.context.type"), CirJsonFileType.INSTANCE) {

    override fun isInContext(file: PsiFile, offset: Int): Boolean {
        return file is CirJsonFile
    }

}