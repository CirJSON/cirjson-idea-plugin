package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.vfs.VirtualFile

interface CirJsonSchemaFileProvider {

    val name: String

    fun isAvailable(file: VirtualFile): Boolean

    val schemaFile: VirtualFile?

    val schemaType: SchemaType

    /**
     * If this schema is shown and selectable by the user in the schema dropdown menu.
     *
     * Some schemas are designed to be auto-assigned and bound to very particular contexts,
     * and thus hidden from the selector.
     */
    val isUserVisible: Boolean
        get() = true

    /**
     * Presentable name of the schema shown in the UI.
     */
    val presentableName: String
        get() = name

    val remoteSource: String?
        get() = null

}