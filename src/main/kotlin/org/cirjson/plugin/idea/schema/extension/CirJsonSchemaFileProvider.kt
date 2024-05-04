package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.vfs.VirtualFile

interface CirJsonSchemaFileProvider {

    val name: String

    fun isAvailable(file: VirtualFile): Boolean

    val schemaFile: VirtualFile?

    val schemaType: SchemaType

    /**
     * Presentable name of the schema shown in the UI.
     */
    val presentableName: String
        get() = name

    val remoteSource: String?
        get() = null

}