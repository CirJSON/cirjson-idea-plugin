package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.vfs.VirtualFile

interface CirJsonSchemaFileProvider {

    fun isAvailable(file: VirtualFile): Boolean

    val schemaFile: VirtualFile?

    val schemaType: SchemaType

    val remoteSource: String?
        get() = null

}