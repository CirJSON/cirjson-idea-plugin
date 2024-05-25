package org.cirjson.plugin.idea.psi

import com.intellij.psi.PsiFile

interface CirJsonFile : CirJsonElement, PsiFile {

    val topLevelValue: CirJsonValue?

    val allTopLevelValue: List<CirJsonValue>

}