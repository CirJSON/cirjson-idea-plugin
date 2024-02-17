package org.cirjson.plugin.idea.editor

import com.intellij.openapi.options.ConfigurableBuilder
import com.intellij.openapi.options.SearchableConfigurable
import org.cirjson.plugin.idea.CirJsonBundle

class CirJsonSmartKeysConfigurable : ConfigurableBuilder(), SearchableConfigurable {

    init {
        val settings = CirJsonEditorOptions.instance

        checkBox(CirJsonBundle.message("settings.smart.keys.insert.missing.comma.on.enter"),
                settings::COMMA_ON_ENTER)
        checkBox(CirJsonBundle.message("settings.smart.keys.insert.missing.comma.after.matching.braces.and.quotes"),
                settings::COMMA_ON_MATCHING_BRACES)
        checkBox(CirJsonBundle.message(
                "settings.smart.keys.automatically.manage.commas.when.pasting.cirJson.fragments"),
                settings::COMMA_ON_PASTE)
        checkBox(CirJsonBundle.message("settings.smart.keys.escape.text.on.paste.in.string.literals"),
                settings::ESCAPE_PASTED_TEXT)
        checkBox(CirJsonBundle.message(
                "settings.smart.keys.automatically.add.quotes.to.property.names.when.typing.comma"),
                settings::AUTO_QUOTE_PROP_NAME)
        checkBox(CirJsonBundle.message(
                "settings.smart.keys.automatically.add.whitespace.when.typing.comma.after.property.names"),
                settings::AUTO_WHITESPACE_AFTER_COLON)
        checkBox(CirJsonBundle.message(
                "settings.smart.keys.automatically.move.colon.after.the.property.name.if.typed.inside.quotes"),
                settings::COLON_MOVE_OUTSIDE_QUOTES)
        checkBox(CirJsonBundle.message(
                "settings.smart.keys.automatically.move.comma.after.the.property.value.or.array.element.if.inside.quotes"),
                settings::COMMA_MOVE_OUTSIDE_QUOTES)
    }

    override fun getDisplayName(): String {
        return CirJsonBundle.message("configurable.CirJsonSmartKeysConfigurable.display.name")
    }

    override fun getId(): String {
        return "editor.preferences.cirJsonOptions"
    }

}