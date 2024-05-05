package org.cirjson.plugin.idea

import com.intellij.openapi.fileTypes.LanguageFileType
import org.cirjson.plugin.idea.icons.CirJsonIcons
import javax.swing.Icon

class CirJsonFileType private constructor() : LanguageFileType(CirJsonLanguage.INSTANCE) {

    override fun getName(): String {
        return CirJsonConstants.CirJson
    }

    override fun getDescription(): String {
        return CirJsonConstants.CirJson
    }

    override fun getDefaultExtension(): String {
        return DEFAULT_EXTENSION
    }

    override fun getIcon(): Icon {
        return CirJsonIcons.ICON
    }

    @Suppress("CompanionObjectInExtension")
    companion object {

        val INSTANCE = CirJsonFileType()

        const val DEFAULT_EXTENSION = "cirjson"

    }

}