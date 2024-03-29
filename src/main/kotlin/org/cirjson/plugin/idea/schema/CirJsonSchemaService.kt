package org.cirjson.plugin.idea.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaFileProvider
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject

interface CirJsonSchemaService {

    fun isSchemaFile(file: VirtualFile): Boolean

    fun isSchemaFile(schemaObject: CirJsonSchemaObject): Boolean

    val project: Project

    fun getDynamicSchemaForFile(psiFile: PsiFile): VirtualFile?

    fun registerReference(ref: String)

    fun getSchemaObject(file: VirtualFile): CirJsonSchemaObject?

    fun getSchemaObject(file: PsiFile): CirJsonSchemaObject?

    fun getSchemaObjectForSchemaFile(schemaFile: VirtualFile): CirJsonSchemaObject?

    fun findSchemaFileByReference(reference: String, referent: VirtualFile?): VirtualFile?

    fun getSchemaProvider(schemaFile: VirtualFile): CirJsonSchemaFileProvider?

    fun resolveSchemaFile(schemaObject: CirJsonSchemaObject): VirtualFile?

    fun reset()

    fun isApplicableToFile(file: VirtualFile?): Boolean

    companion object {

        fun get(project: Project): CirJsonSchemaService {
            return project.getService(CirJsonSchemaService::class.java)
        }

    }

}