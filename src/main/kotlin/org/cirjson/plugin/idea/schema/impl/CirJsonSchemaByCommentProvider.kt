package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.regex.Pattern

object CirJsonSchemaByCommentProvider {

    fun getCommentSchema(file: VirtualFile, project: Project): String? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        return getCommentSchema(psiFile)
    }

    fun getCommentSchema(psiFile: PsiFile): String? {
        for (comment in PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)) {
            val chars = comment.node?.chars?.let { StringUtil.newBombedCharSequence(it, 300) } ?: continue
            detectInComment(chars)?.let {
                return it.subSequence(chars).toString()
            }
        }
        return null
    }

    fun detectInComment(chars: CharSequence): TextRange? {
        for (schemaComment in availableComments) {
            schemaComment.detect(chars)?.let {
                return it
            }
        }
        return null
    }

    private val availableComments: List<SchemaComment> = listOf(SchemaComment("yaml-language-server: ${'$'}schema=",
            Pattern.compile("#\\s*yaml-language-server:\\s*\\\$schema=(?<id>\\S+).*", Pattern.CASE_INSENSITIVE),
            forCompletion = false), SchemaComment("${'$'}schema: ",
            Pattern.compile("#\\s*\\\$schema:\\s*(?<id>\\S+).*", Pattern.CASE_INSENSITIVE)))

    val schemaCommentsForCompletion: List<String> = availableComments.filter { it.forCompletion }.map { it.commentText }

    private class SchemaComment(val commentText: String, val detectPattern: Pattern,
            val forCompletion: Boolean = true) {

        fun detect(chars: CharSequence): TextRange? {
            val matcher = detectPattern.matcher(chars)
            if (!matcher.matches()) return null
            return TextRange.create(matcher.start("id"), matcher.end("id"))
        }

    }

}