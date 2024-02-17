package org.cirjson.plugin.idea.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Suppress("PropertyName")
@State(name = "CirJsonEditorOptions", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class CirJsonEditorOptions : PersistentStateComponent<CirJsonEditorOptions> {

    var COMMA_ON_ENTER = true

    var COMMA_ON_MATCHING_BRACES = true

    var COMMA_ON_PASTE = true

    var AUTO_QUOTE_PROP_NAME = true

    var AUTO_WHITESPACE_AFTER_COLON = true

    var ESCAPE_PASTED_TEXT = true

    var COLON_MOVE_OUTSIDE_QUOTES = false

    var COMMA_MOVE_OUTSIDE_QUOTES = false

    override fun getState(): CirJsonEditorOptions {
        return this
    }

    override fun loadState(state: CirJsonEditorOptions) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {

        val instance: CirJsonEditorOptions
            get() {
                return ApplicationManager.getApplication().getService(CirJsonEditorOptions::class.java)
            }

    }

}