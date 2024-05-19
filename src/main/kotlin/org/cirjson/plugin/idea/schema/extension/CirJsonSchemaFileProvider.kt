package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.vfs.VirtualFile
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion

interface CirJsonSchemaFileProvider {

    val name: String

    fun isAvailable(file: VirtualFile): Boolean

    val schemaFile: VirtualFile?

    val schemaType: SchemaType

    val schemaVersion: CirJsonSchemaVersion
        get() = CirJsonSchemaVersion.SCHEMA_1

    /**
     * Information about the provided API (e.g., an API version or the target platform). This is useful for
     * auto-generated schemas targeting multiple versions of the same config.
     */
    val thirdPartyApiInformation: String?
        get() = null

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