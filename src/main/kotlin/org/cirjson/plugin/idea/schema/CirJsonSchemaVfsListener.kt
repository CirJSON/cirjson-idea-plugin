package org.cirjson.plugin.idea.schema

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ZipperUpdater
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.Alarm
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.Callable
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaServiceImpl

class CirJsonSchemaVfsListener private constructor(updater: CirJsonSchemaUpdater) :
        BulkVirtualFileListenerAdapter(object : VirtualFileContentsChangedAdapter() {

            private val myUpdater = updater

            override fun onFileChange(schemaFile: VirtualFile) {
                myUpdater.onFileChange(schemaFile)
            }

            override fun onBeforeFileChange(schemaFile: VirtualFile) {
                myUpdater.onFileChange(schemaFile)
            }

        }) {

    class CirJsonSchemaUpdater(private val myProject: Project, private val myService: CirJsonSchemaService) {

        private val myUpdater = ZipperUpdater(DELAY_MS, Alarm.ThreadToUse.POOLED_THREAD, myService as Disposable)

        private val myDirtySchemas = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

        private val myTaskExecutor =
                SequentialTaskExecutor.createSequentialApplicationPoolExecutor("CirJson Vfs Updater Executor")

        private val myRunnable = Runnable {
            if (myProject.isDisposed) {
                return@Runnable
            }

            val scope = HashSet(myDirtySchemas)

            if (scope.any { myService.possiblyHasReference(it.name) }) {
                myProject.messageBus.syncPublisher(CIRJSON_DEPS_CHANGED).run()
                CirJsonDependencyModificationTracker.forProject(myProject).incModificationCount()
            }

            myDirtySchemas.removeAll(scope)

            if (scope.isEmpty()) {
                return@Runnable
            }

            val finalScope = scope.filter {
                myService.isApplicableToFile(it) && (myService as CirJsonSchemaServiceImpl).isMappedSchema(it, false)
            }

            if (finalScope.isEmpty()) {
                return@Runnable
            }

            if (myProject.isDisposed) {
                return@Runnable
            }

            myProject.messageBus.syncPublisher(CIRJSON_SCHEMA_CHANGED).run()

            val analyzer = DaemonCodeAnalyzer.getInstance(myProject)
            val psiManager = PsiManager.getInstance(myProject)
            val editors = EditorFactory.getInstance().allEditors
            editors.filter { it is EditorEx && it.project === myProject }.mapNotNull { it.virtualFile }
                    .filter { it.isValid }.forEach { file ->
                        val schemaFiles =
                                (myService as CirJsonSchemaServiceImpl).getSchemasForFile(file, single = false,
                                        onlyUserSchemas = true)

                        if (!schemaFiles.any(finalScope::contains)) {
                            return@forEach
                        }

                        if (ApplicationManager.getApplication().isUnitTestMode) {
                            ReadAction.run<RuntimeException> { restartAnalyzer(analyzer, psiManager, file) }
                        } else {
                            ReadAction.nonBlocking(Callable { restartAnalyzer(analyzer, psiManager, file) })
                                    .expireWith(myService as Disposable).submit(myTaskExecutor)
                        }
                    }
        }

        internal fun onFileChange(schemaFile: VirtualFile) {
            if (CirJsonFileType.DEFAULT_EXTENSION == schemaFile.extension) {
                myDirtySchemas.add(schemaFile)
                val app = ApplicationManager.getApplication()

                if (app.isUnitTestMode) {
                    app.invokeLater(myRunnable, myProject.disposed)
                } else {
                    myUpdater.queue(myRunnable)
                }
            }
        }

        companion object {

            private const val DELAY_MS = 200

            private fun restartAnalyzer(analyzer: DaemonCodeAnalyzer, psiManager: PsiManager, file: VirtualFile) {
                val psiFile = if (!psiManager.isDisposed && file.isValid) {
                    psiManager.findFile(file)
                } else {
                    null
                } ?: return
                analyzer.restart(psiFile)
            }

        }

    }

    companion object {

        val CIRJSON_SCHEMA_CHANGED =
                Topic.create("CirJsonSchemaVfsListener.CirJson.Schema.Changed", Runnable::class.java)

        val CIRJSON_DEPS_CHANGED = Topic.create("CirJsonSchemaVfsListener.CirJson.Deps.Changed", Runnable::class.java)

        fun startListening(project: Project, service: CirJsonSchemaService,
                connection: MessageBusConnection): CirJsonSchemaUpdater {
            val updater = CirJsonSchemaUpdater(project, service)
            connection.subscribe(VirtualFileManager.VFS_CHANGES, CirJsonSchemaVfsListener(updater))
            PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeAnyChangeAbstractAdapter() {

                override fun onChange(file: PsiFile?) {
                    file ?: return
                    updater.onFileChange(file.viewProvider.virtualFile)
                }

            }, service as Disposable)
            return updater
        }

    }

}