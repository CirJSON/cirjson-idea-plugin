package org.cirjson.plugin.idea.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.cirjson.plugin.idea.CirJsonFileType

class CirJsonElementGenerator(private val myProject: Project) {

    /**
     * Create lightweight in-memory [CirJsonFile] filled with `content`.
     *
     * @param content content of the file to be created
     * @return created file
     */
    fun createDummyFile(content: String): PsiFile {
        val psiFileFactory = PsiFileFactory.getInstance(myProject)
        return psiFileFactory.createFileFromText("dummy.${CirJsonFileType.INSTANCE.defaultExtension}",
                CirJsonFileType.INSTANCE, content)
    }

    /**
     * Create CirJSON value from supplied content.
     *
     * @param content properly escaped text of CirJSON value, e.g. Java literal `"\"new\\nline\""` if you want to create
     * string literal
     * @param T type of the CirJSON value desired
     *
     * @return element created from given text
     *
     * @see createStringLiteral
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CirJsonValue> createValue(content: String): T {
        val file = createDummyFile("{\"__cirJsonId__\": \"1\", \"foo\": $content}")
        return (file.firstChild as CirJsonObject).propertyList[0].value as T
    }

    /**
     * Create CirJSON string literal from supplied *unescaped* content.
     *
     * @param unescapedContent unescaped content of string literal, e.g. Java literal `"new\nline"` (compare with
     * [createValue]).
     *
     * @return CirJSON string literal created from given text
     */
    fun createStringLiteral(unescapedContent: String): CirJsonStringLiteral {
        return createValue("\"${StringUtil.escapeStringCharacters(unescapedContent)}\"")
    }

    fun createProperty(name: String, value: String): CirJsonProperty {
        val file = createDummyFile("{\"__cirJsonId__\": \"1\", \"$name\": \"$value\"}")
        return (file.firstChild as CirJsonObject).propertyList[0]
    }

    fun createComma(): PsiElement {
        val cirJsonArray = createValue<CirJsonArray>("[\"1\", 1, 2]")
        return cirJsonArray.valueList[0].nextSibling
    }

}