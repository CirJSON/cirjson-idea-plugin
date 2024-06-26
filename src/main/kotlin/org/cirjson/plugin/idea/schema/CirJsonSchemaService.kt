package org.cirjson.plugin.idea.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaFileProvider
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion

interface CirJsonSchemaService {

    fun isSchemaFile(file: VirtualFile): Boolean

    fun isSchemaFile(schemaObject: CirJsonSchemaObject): Boolean

    val project: Project

    fun getSchemaVersion(file: VirtualFile): CirJsonSchemaVersion?

    fun getSchemaFilesForFile(file: VirtualFile): Collection<VirtualFile>

    fun getDynamicSchemaForFile(psiFile: PsiFile): VirtualFile?

    fun registerRemoteUpdateCallback(callback: Runnable)

    fun unregisterRemoteUpdateCallback(callback: Runnable)

    fun registerResetAction(action: Runnable)

    fun unregisterResetAction(action: Runnable)

    fun registerReference(ref: String)

    fun possiblyHasReference(ref: String): Boolean

    fun triggerUpdateRemote()

    fun getSchemaObject(file: VirtualFile): CirJsonSchemaObject?

    fun getSchemaObject(file: PsiFile): CirJsonSchemaObject?

    fun getSchemaObjectForSchemaFile(schemaFile: VirtualFile): CirJsonSchemaObject?

    fun findSchemaFileByReference(reference: String, referent: VirtualFile?): VirtualFile?

    fun getSchemaProvider(schemaFile: VirtualFile): CirJsonSchemaFileProvider?

    fun getSchemaProvider(schemaObject: CirJsonSchemaObject): CirJsonSchemaFileProvider?

    fun resolveSchemaFile(schemaObject: CirJsonSchemaObject): VirtualFile?

    fun reset()

    val allUserVisibleSchemas: List<CirJsonSchemaInfo>

    fun isApplicableToFile(file: VirtualFile?): Boolean

    companion object {

        fun get(project: Project): CirJsonSchemaService {
            return project.getService(CirJsonSchemaService::class.java)
        }

        internal fun isSchemaFile(psiFile: PsiFile): Boolean {
            if (CirJsonLikePsiWalker.getWalker(psiFile, CirJsonSchemaObject.NULL_OBJ) == null) {
                return false
            }

            val file = psiFile.viewProvider.virtualFile
            val service = get(psiFile.project)
            return service.isSchemaFile(file) && service.isApplicableToFile(file)
        }

    }

}