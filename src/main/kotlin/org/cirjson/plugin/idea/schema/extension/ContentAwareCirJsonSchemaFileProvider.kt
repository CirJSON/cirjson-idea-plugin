package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

interface ContentAwareCirJsonSchemaFileProvider {

    fun getSchemaFile(psiFile: PsiFile): VirtualFile?

    companion object {

        val EP_NAME = ExtensionPointName.create<ContentAwareCirJsonSchemaFileProvider>(
                "org.cirjson.plugin.idea.javaScript.cirJsonSchema.contentAwareSchemaFileProvider")

    }

}