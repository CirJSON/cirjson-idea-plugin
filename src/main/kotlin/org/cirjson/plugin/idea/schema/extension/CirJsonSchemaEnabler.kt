package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface CirJsonSchemaEnabler {

    /**
     * This method should return true if CirJSON schema mechanism should become applicable to corresponding file.
     * This method SHOULD NOT ADDRESS INDEXES.
     *
     * @param file Virtual file to check for
     *
     * @param project Current project
     *
     * @return `true` if available, `false` otherwise
     */
    fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean

    fun canBeSchemaFile(file: VirtualFile): Boolean

    companion object {

        val EXTENSION_POINT_NAME =
                ExtensionPointName.create<CirJsonSchemaEnabler>("org.cirjson.plugin.idea.cirJsonSchemaEnabler")

    }

}