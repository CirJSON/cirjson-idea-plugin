package org.cirjson.plugin.idea.editor

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.cirjson.plugin.idea.psi.CirJsonArray
import org.cirjson.plugin.idea.psi.CirJsonObject

@Suppress("UnstableApiUsage")
class CirJsonFocusModeProvider : FocusModeProvider {

    override fun calcFocusZones(file: PsiFile): MutableList<out Segment> {
        return SyntaxTraverser.psiTraverser(file).postOrderDfsTraversal()
                .filter { element -> element is CirJsonObject || element is CirJsonArray }
                .map { element -> element.textRange }.toList()
    }

}