package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class CirJsonSchemaCacheManager : Disposable {

    private val cache =
            CollectionFactory.createConcurrentWeakMap<VirtualFile, CachedValue<CompletableFuture<CirJsonSchemaObject?>>>()

    /**
     *  Computes [CirJsonSchemaObject] preventing multiple concurrent computations of the same schema.
     */
    fun computeSchemaObject(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile): CirJsonSchemaObject? {
        val newFuture = CompletableFuture<CirJsonSchemaObject?>()
        val cachedValue = getUpToDateFuture(schemaVirtualFile, schemaPsiFile, newFuture)
        val cachedFuture = cachedValue.value

        if (cachedFuture === newFuture) {
            completeSync(schemaVirtualFile, schemaPsiFile, cachedFuture)
        }

        return try {
            ProgressIndicatorUtils.awaitWithCheckCanceled(cachedFuture, ProgressManager.getInstance().progressIndicator)
        } catch (e: ProcessCanceledException) {
            ProgressManager.checkCanceled()

            cache.remove(schemaVirtualFile, cachedValue)

            computeSchemaObject(schemaVirtualFile, schemaPsiFile)
        }
    }

    private fun getUpToDateFuture(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile,
            newFuture: CompletableFuture<CirJsonSchemaObject?>): CachedValue<CompletableFuture<CirJsonSchemaObject?>> {
        return cache.compute(schemaVirtualFile) { _, prevValue ->
            val virtualFileModStamp: Long = schemaVirtualFile.modificationStamp
            val psiFileModStamp: Long = schemaPsiFile.modificationStamp

            if (prevValue != null && prevValue.virtualFileModStamp == virtualFileModStamp
                    && prevValue.psiFileModStamp == psiFileModStamp) {
                prevValue
            } else {
                CachedValue(newFuture, virtualFileModStamp, psiFileModStamp)
            }
        }!!
    }

    private fun completeSync(schemaVirtualFile: VirtualFile, schemaPsiFile: PsiFile,
            future: CompletableFuture<CirJsonSchemaObject?>) {
        try {
            future.complete(CirJsonSchemaReader(schemaVirtualFile).read(schemaPsiFile))
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
    }

    override fun dispose() {
        cache.clear()
    }

    private data class CachedValue<T>(val value: T, val virtualFileModStamp: Long, val psiFileModStamp: Long)

    companion object {

        fun getInstance(project: Project): CirJsonSchemaCacheManager {
            return project.service()
        }

    }

}