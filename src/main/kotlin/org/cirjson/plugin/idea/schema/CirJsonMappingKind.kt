package org.cirjson.plugin.idea.schema

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import org.cirjson.plugin.idea.CirJsonBundle
import javax.swing.Icon

enum class CirJsonMappingKind {

    FILE,

    PATTERN,

    DIRECTORY;

    val description: String
        get() {
            return when (this) {
                FILE -> CirJsonBundle.message("schema.mapping.file")
                PATTERN -> CirJsonBundle.message("schema.mapping.pattern")
                DIRECTORY -> CirJsonBundle.message("schema.mapping.directory")
            }
        }

    val prefix: String
        get() = "${StringUtil.capitalize(description)}: "

    val icon: Icon
        get() {
            return when (this) {
                FILE -> AllIcons.FileTypes.Any_type
                PATTERN -> AllIcons.FileTypes.Unknown
                DIRECTORY -> AllIcons.Nodes.Folder
            }
        }

}