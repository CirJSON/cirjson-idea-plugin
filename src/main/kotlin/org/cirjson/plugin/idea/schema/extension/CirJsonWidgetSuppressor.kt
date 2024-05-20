package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Implement to suppress showing CirJSON widget for particular files where assistance is powered by a custom provider.
 */
interface CirJsonWidgetSuppressor {

    /**
     * Allows to check whether widget for the file should be suppressed or not.
     *
     * This method is called on EDT.
     */
    fun isCandidateForSuppress(file: VirtualFile, project: Project): Boolean {
        return false
    }

    /**
     * Allows to suppress CirJSON widget for particular files.
     *
     * This method is called only if [isCandidateForSuppress] returns `true` for the given file in the given project.
     *
     * This method is called on a background thread under read action with progress indicator.
     *
     * Implementors might want to call [com.intellij.openapi.progress.ProgressManager.checkCanceled] from time to time
     * to check whether widget suppression is still actual for the given file. For instance, progress indicator is
     * canceled if another editor tab is selected.
     */
    fun suppressSwitcherWidget(file: VirtualFile, project: Project): Boolean

    companion object {

        val EXTENSION_POINT_NAME =
                ExtensionPointName.create<CirJsonWidgetSuppressor>("org.cirjson.plugin.idea.cirJsonWidgetSuppressor")

    }

}