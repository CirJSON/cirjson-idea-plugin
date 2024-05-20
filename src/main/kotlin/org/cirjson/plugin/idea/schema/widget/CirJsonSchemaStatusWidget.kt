package org.cirjson.plugin.idea.schema.widget

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonWidgetSuppressor
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
        TODO()
    }

    override fun update(finishUpdate: Runnable?) {
        TODO()
    }

    override fun registerCustomListeners(connection: MessageBusConnection) {
        TODO()
    }

    override fun handleFileChange(file: VirtualFile?) {
        TODO()
    }

    override fun dispose() {
        TODO()
    }

    override fun afterVisibleUpdate(state: WidgetState) {
        TODO()
    }

    private class MyWidgetState(toolTip: String, text: String, isActionEnabled: Boolean) :
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
            TODO()
        }

    }

}