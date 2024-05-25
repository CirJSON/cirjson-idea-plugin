package org.cirjson.plugin.idea.schema.widget

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage
import org.cirjson.plugin.idea.extentions.toCallable
import org.cirjson.plugin.idea.schema.CirJsonSchemaCatalogProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaMappingsProjectConfiguration
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaEnabler
import org.cirjson.plugin.idea.schema.extension.CirJsonSchemaInfo
import org.cirjson.plugin.idea.schema.extension.CirJsonWidgetSuppressor
import org.cirjson.plugin.idea.schema.extension.SchemaType
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaServiceImpl
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CirJsonSchemaStatusWidget internal constructor(project: Project, scope: CoroutineScope) :
        EditorBasedStatusBarPopup(project, false, scope) {

    private val mySuppressInfoRef = AtomicReference<Pair<VirtualFile, Boolean>>()

    private val myUpdateCallback = Runnable {
        update()
        ourIsNotified.set(false)
    }

    private val myServiceLazy = SynchronizedClearableLazy {
        if (!project.isDisposed) {
            val service = CirJsonSchemaService.get(project).apply {
                registerRemoteUpdateCallback(myUpdateCallback)
                registerResetAction(myUpdateCallback)
            }
            return@SynchronizedClearableLazy service
        }

        return@SynchronizedClearableLazy null
    }

    @Volatile
    private var myLastWidgetStateAndFilePair: Pair<WidgetState, VirtualFile?>? = null

    private var myCurrentProgress: ProgressIndicator? = null

    init {
        CirJsonWidgetSuppressor.EXTENSION_POINT_NAME.addChangeListener(this::update, this)
    }

    private val service: CirJsonSchemaService?
        get() = myServiceLazy.value

    override fun ID(): String {
        return ID
    }

    override fun createInstance(project: Project): StatusBarWidget {
        return CirJsonSchemaStatusWidget(project, scope)
    }

    override fun createPopup(context: DataContext): ListPopup? {
        val file = CommonDataKeys.VIRTUAL_FILE.getData(context) ?: return null

        val lastWidgetStateAndFilePair = myLastWidgetStateAndFilePair
        val lastWidgetState = lastWidgetStateAndFilePair?.first

        if (lastWidgetState is MyWidgetState && file == lastWidgetStateAndFilePair.second) {
            val service = service

            if (service != null) {
                return CirJsonSchemaStatusPopup.createPopup(service, project, file, lastWidgetState.isWarning)
            }
        }

        return null
    }

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        val lastStateAndFilePair = myLastWidgetStateAndFilePair
        val widgetState = calcWidgetState(file, lastStateAndFilePair?.first, lastStateAndFilePair?.second)
        myLastWidgetStateAndFilePair = widgetState to file
        return widgetState
    }

    private fun calcWidgetState(file: VirtualFile?, lastWidgetState: WidgetState?,
            lastFile: VirtualFile?): WidgetState {
        val suppressInfo = mySuppressInfoRef.getAndSet(null)

        if (myCurrentProgress != null && !myCurrentProgress!!.isCanceled) {
            myCurrentProgress!!.cancel()
        }

        file ?: return WidgetState.HIDDEN

        val status = getWidgetStatus(project, file)

        if (status == WidgetStatus.DISABLED) {
            return WidgetState.HIDDEN
        }

        val fileType = file.fileType
        val language = (fileType as? LanguageFileType)?.language
        val isCirJsonFile = language is CirJsonLanguage

        if (DumbService.getInstance(project).isDumb) {
            return getDumbModeState(isCirJsonFile)
        }

        if (status == WidgetStatus.MAYBE_SUPPRESSED) {
            if (suppressInfo == null || suppressInfo.first == file) {
                myCurrentProgress = EmptyProgressIndicator()
                scheduleSuppressCheck(file, myCurrentProgress!!)

                // show 'loading' only when switching between files and previous state was not hidden, otherwise the
                // widget will "jump"
                return if (lastFile != file && lastWidgetState != null && lastWidgetState != WidgetState.HIDDEN) {
                    val analyzed = if (isCirJsonFile) {
                        CirJsonBundle.message("schema.widget.prefix.cirjson.files")
                    } else {
                        CirJsonBundle.message("schema.widget.prefix.other.files")
                    }

                    WidgetState(CirJsonBundle.message("schema.widget.checking.state.tooltip"),
                            CirJsonBundle.message("schema.widget.checking.state.text", "$analyzed "), false)
                } else {
                    WidgetState.NO_CHANGE
                }
            } else if (suppressInfo.second) {
                return WidgetState.HIDDEN
            }
        }

        return doGetWidgetState(file, isCirJsonFile)
    }

    private fun scheduleSuppressCheck(file: VirtualFile, globalProgress: ProgressIndicator) {
        val update = Runnable {
            if (DumbService.getInstance(project).isDumb) {
                // Suppress check should be rescheduled when dumb mode ends.
                mySuppressInfoRef.set(null)
            } else {
                val suppress = CirJsonWidgetSuppressor.EXTENSION_POINT_NAME.extensionList.any {
                    it.suppressSwitcherWidget(file, project)
                }
                mySuppressInfoRef.set(file to suppress)
            }

            super.update(null)
        }

        if (ApplicationManager.getApplication().isUnitTestMode) {
            // Give tests a chance to check the widget state before the task is run (see
            // EditorBasedStatusBarPopup#updateInTests())
            ApplicationManager.getApplication().invokeLater(update)
        } else {
            ReadAction.nonBlocking(update.toCallable()).expireWith(this).wrapProgress(globalProgress)
                    .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    private fun doGetWidgetState(file: VirtualFile, isCirJsonFile: Boolean): WidgetState {
        val service = service
        val userMappingsConfiguration = CirJsonSchemaMappingsProjectConfiguration.getInstance(project)

        if (service == null || userMappingsConfiguration.isIgnoredFile(file)) {
            return getNoSchemaState()
        }

        var schemaFiles = service.getSchemaFilesForFile(file)

        if (schemaFiles.isEmpty()) {
            return getNoSchemaState()
        }

        if (schemaFiles.size > 1) {
            val userSchemas = arrayListOf<VirtualFile>()

            if (hasConflicts(userSchemas, service, file)) {
                val state = MyWidgetState(createMessage(schemaFiles, service, "<br/>",
                        CirJsonBundle.message("schema.widget.conflict.message.prefix"), ""),
                        "${schemaFiles.size} ${CirJsonBundle.message("schema.widget.conflict.message.postfix")}", true)
                state.isWarning = true
                state.isHavingConflict = true
                return state
            }

            schemaFiles = userSchemas

            if (schemaFiles.isEmpty()) {
                return getNoSchemaState()
            }
        }

        var schemaFile = schemaFiles.first()
        schemaFile = (service as CirJsonSchemaServiceImpl).replaceHttpFileWithBuiltinIfNeeded(schemaFile)

        val toolTip = if (isCirJsonFile) {
            CirJsonBundle.message("schema.widget.tooltip.cirjson.files")
        } else {
            CirJsonBundle.message("schema.widget.tooltip.other.files")
        }
        val bar = if (isCirJsonFile) {
            CirJsonBundle.message("schema.widget.prefix.cirjson.files")
        } else {
            CirJsonBundle.message("schema.widget.prefix.other.files")
        }

        if (schemaFile is HttpVirtualFile) {
            val info = schemaFile.fileInfo ?: return getDownloadErrorState(null)

            when (info.state) {
                RemoteFileState.DOWNLOADING_NOT_STARTED -> {
                    addDownloadingUpdateListener(info)
                    return MyWidgetState("$toolTip ${getSchemaFileDesc(schemaFile)}",
                            "$bar ${getPresentableNameForFile(schemaFile)}", true)
                }

                RemoteFileState.DOWNLOADING_IN_PROGRESS -> {
                    addDownloadingUpdateListener(info)
                    return MyWidgetState(CirJsonBundle.message("schema.widget.download.in.progress.tooltip"),
                            CirJsonBundle.message("schema.widget.download.in.progress.label"), false)
                }

                RemoteFileState.ERROR_OCCURRED -> {
                    return getDownloadErrorState(info.errorMessage)
                }

                else -> {}
            }
        }

        if (!isValidSchemaFile(schemaFile)) {
            val state = MyWidgetState(CirJsonBundle.message("schema.widget.error.not.a.schema"),
                    CirJsonBundle.message("schema.widget.error.label"), true)
            state.isWarning = true
            return state
        }

        val provider = service.getSchemaProvider(schemaFile) ?: return MyWidgetState(
                "$toolTip ${getSchemaFileDesc(schemaFile)}", "$bar ${getPresentableNameForFile(schemaFile)}", true)
        val preferRemoteSchemas = CirJsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas
        val remoteSource = provider.remoteSource
        val useRemoteSource = preferRemoteSchemas && remoteSource != null && !CirJsonFileResolver.isSchemaUrl(
                remoteSource) && !remoteSource.endsWith("!")
        val providerName = if (useRemoteSource) remoteSource!! else provider.presentableName
        val shortName = StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".cirjson"), "-schema")
        val name = if (useRemoteSource) {
            provider.presentableName
        } else if (CirJsonBundle.message("schema.of.version", "") in shortName) {
            shortName
        } else {
            "$bar $shortName"
        }
        val kind =
                if (!useRemoteSource && (provider.schemaType == SchemaType.EMBEDDED_SCHEMA || provider.schemaType == SchemaType.SCHEMA)) {
                    CirJsonBundle.message("schema.widget.bundled.postfix")
                } else {
                    ""
                }

        return MyWidgetState("$toolTip $providerName$kind", name, true)
    }

    private fun addDownloadingUpdateListener(info: RemoteFileInfo) {
        info.addDownloadingListener(object : FileDownloadingAdapter() {

            override fun fileDownloaded(localFile: VirtualFile) {
                update()
            }

            override fun errorOccurred(errorMessage: String) {
                update()
            }

            override fun downloadingCancelled() {
                update()
            }

        })
    }

    private fun isValidSchemaFile(schemaFile: VirtualFile?): Boolean {
        if (schemaFile is HttpVirtualFile) {
            return true
        }

        schemaFile ?: return false

        val service = service

        return service != null && service.isSchemaFile(schemaFile) && service.isApplicableToFile(schemaFile)
    }

    override fun update(finishUpdate: Runnable?) {
        mySuppressInfoRef.set(null)
        super.update(finishUpdate)
    }

    override fun registerCustomListeners(connection: MessageBusConnection) {
        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {

            @Volatile
            private var myIsDumbMode = false

            override fun enteredDumbMode() {
                myIsDumbMode = true
                update()
            }

            override fun exitDumbMode() {
                myIsDumbMode = false
                update()
            }

        })
    }

    override fun handleFileChange(file: VirtualFile?) {
        ourIsNotified.set(false)
    }

    override fun dispose() {
        val service = myServiceLazy.takeIf { it.isInitialized() }?.value

        service?.unregisterRemoteUpdateCallback(myUpdateCallback)
        service?.unregisterResetAction(myUpdateCallback)

        super.dispose()
    }

    override fun afterVisibleUpdate(state: WidgetState) {
        if (state !is MyWidgetState || !state.isHavingConflict) {
            ourIsNotified.set(false)
            return
        }

        if (ourIsNotified.get()) {
            return
        }

        ourIsNotified.set(true)
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
        alarm.addRequest(Runnable {
            val message = HtmlBuilder().apply {
                append(HtmlChunk.tag("b").addText(CirJsonBundle.message("schema.widget.conflict.popup.title")))
                append(HtmlChunk.br())
                append(HtmlChunk.br())
                appendRaw(state.toolTip!!)
            }.toString()
            val label = HintUtil.createErrorLabel(message)
            val builder = JBPopupFactory.getInstance().createBalloonBuilder(label)
            val statusBarComponent = component
            val balloon = builder.apply {
                setCalloutShift(statusBarComponent.height / 2)
                setDisposable(this@CirJsonSchemaStatusWidget)
                setFillColor(HintUtil.getErrorColor())
                setHideOnClickOutside(true)
            }.createBalloon()
            balloon.showInCenterOf(statusBarComponent)
        }, 500, ModalityState.nonModal())
    }

    private class MyWidgetState(toolTip: String?, text: String, isActionEnabled: Boolean) :
            WidgetState(toolTip, text, isActionEnabled) {

        var isWarning: Boolean = false
            set(value) {
                field = value
                icon = if (value) AllIcons.General.Warning else null
            }

        var isHavingConflict: Boolean = false

    }

    private enum class WidgetStatus {

        ENABLED,

        DISABLED,

        MAYBE_SUPPRESSED

    }

    companion object {

        const val ID = "CirJSONSchemaSelector"

        private val ourIsNotified = AtomicBoolean(false)

        fun isAvailableOnFile(project: Project, file: VirtualFile?): Boolean {
            file ?: return false

            val enablers = CirJsonSchemaEnabler.EXTENSION_POINT_NAME.extensionList

            return enablers.any { it.isEnabledForFile(file, project) && it.shouldShowSwitcherWidget(file) }
        }

        private fun getWidgetStatus(project: Project, file: VirtualFile): WidgetStatus {
            val enablers = CirJsonSchemaEnabler.EXTENSION_POINT_NAME.extensionList

            return if (!enablers.any { it.isEnabledForFile(file, project) && it.shouldShowSwitcherWidget(file) }) {
                WidgetStatus.DISABLED
            } else if (DumbService.getInstance(project).isDumb) {
                WidgetStatus.ENABLED
            } else if (CirJsonWidgetSuppressor.EXTENSION_POINT_NAME.extensionList.any {
                        it.isCandidateForSuppress(file, project)
                    }) {
                WidgetStatus.MAYBE_SUPPRESSED
            } else {
                WidgetStatus.ENABLED
            }
        }

        private fun getDumbModeState(isCirJsonFile: Boolean): WidgetState {
            val prefix = if (isCirJsonFile) {
                CirJsonBundle.message("schema.widget.prefix.cirjson.files")
            } else {
                CirJsonBundle.message("schema.widget.prefix.other.files")
            }
            return WidgetState.getDumbModeState(CirJsonBundle.message("schema.widget.service"), "$prefix ")
        }

        private fun getNoSchemaState(): WidgetState {
            return MyWidgetState(CirJsonBundle.message("schema.widget.no.schema.tooltip"),
                    CirJsonBundle.message("schema.widget.no.schema.label"), true)
        }

        private fun hasConflicts(files: MutableCollection<VirtualFile>, service: CirJsonSchemaService,
                file: VirtualFile): Boolean {
            val providers = (service as CirJsonSchemaServiceImpl).getProvidersForFile(file)

            for (provider in providers) {
                if (provider.schemaType == SchemaType.USER_SCHEMA) {
                    continue
                }

                provider.schemaFile?.let { files.add(it) }
            }

            return files.size > 1
        }

        @Suppress("SameParameterValue")
        private fun createMessage(schemaFiles: Collection<VirtualFile>, cirJsonSchemaService: CirJsonSchemaService,
                separator: String, prefix: String, suffix: String): String? {
            val pairList = schemaFiles.mapNotNull { cirJsonSchemaService.getSchemaProvider(it) }
                    .map { (it.schemaType == SchemaType.USER_SCHEMA) to it.name }

            val numOfSystemSchemas = pairList.count { !it.first }

            if (pairList.size == 2 && numOfSystemSchemas == 1) {
                return null
            }

            val withTypes = numOfSystemSchemas > 0
            return pairList.map { formatName(withTypes, it) }.joinToString(separator, prefix, suffix)
        }

        private fun formatName(withTypes: Boolean, pair: Pair<Boolean, String>): String {
            return "&nbsp;&nbsp;- " + if (withTypes) {
                "${if (pair.first) "user" else "&"} system ${pair.second}"
            } else {
                pair.second
            }
        }

        private fun getDownloadErrorState(message: String?): WidgetState {
            val s = if (message == null) "" else ": ${HtmlChunk.br()}$message"
            val state = MyWidgetState("${CirJsonBundle.message("schema.widget.error.cant.download")}$s",
                    CirJsonBundle.message("schema.widget.error.label"), true)
            state.isWarning = true
            return state
        }

        private fun getSchemaFileDesc(schemaFile: VirtualFile): String {
            if (schemaFile is HttpVirtualFile) {
                return schemaFile.presentableUrl
            }

            val npmPackageName = extractNpmPackageName(schemaFile.path)
            val packageSuffix = if (npmPackageName != null) {
                " ${CirJsonBundle.message("schema.widget.package.postfix", npmPackageName)}"
            } else {
                ""
            }
            return "${schemaFile.name}$packageSuffix"
        }

        private fun extractNpmPackageName(path: String?): String? {
            var realPath = path

            realPath ?: return null
            var idx = realPath.indexOf("node_modules")

            if (idx != -1) {
                val trimIndex = idx + "node_modules".length + 1

                if (trimIndex < realPath.length) {
                    realPath = realPath.substring(trimIndex)
                    idx = StringUtil.indexOfAny(realPath, "\\/")

                    if (idx != -1) {
                        if (realPath.startsWith("@")) {
                            idx = StringUtil.indexOfAny(realPath, "\\/", idx + 1, realPath.length)
                        }
                    }
                }

                if (idx != -1) {
                    return realPath.substring(0, idx)
                }
            }

            return null
        }

        private fun getPresentableNameForFile(schemaFile: VirtualFile): String {
            if (schemaFile is HttpVirtualFile) {
                return CirJsonSchemaInfo(schemaFile.url).description
            }

            val nameWithoutExtension = schemaFile.nameWithoutExtension

            if (!CirJsonSchemaInfo.isVeryDumbName(nameWithoutExtension)) {
                return nameWithoutExtension
            }

            val path = schemaFile.path
            val npmPackageName = extractNpmPackageName(path)
            return npmPackageName ?: schemaFile.name
        }

    }

}