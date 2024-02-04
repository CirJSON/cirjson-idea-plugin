package org.cirjson.plugin.idea.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.CirJsonLanguage
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.PropertyKey

@Suppress("PropertyName")
class CirJsonCodeStyleSettings(settings: CodeStyleSettings) :
        CustomCodeStyleSettings(CirJsonLanguage.INSTANCE.id, settings) {

    var SPACE_AFTER_COLON = true

    var SPACE_BEFORE_COLON = false

    var KEEP_TRAILING_COMMA = false

    var PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN.id

    @MagicConstant(flags = [CommonCodeStyleSettings.DO_NOT_WRAP.toLong(), CommonCodeStyleSettings.WRAP_ALWAYS.toLong(),
        CommonCodeStyleSettings.WRAP_AS_NEEDED.toLong(), CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM.toLong()])
    @CommonCodeStyleSettings.WrapConstant
    var OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS

    @MagicConstant(flags = [CommonCodeStyleSettings.DO_NOT_WRAP.toLong(), CommonCodeStyleSettings.WRAP_ALWAYS.toLong(),
        CommonCodeStyleSettings.WRAP_AS_NEEDED.toLong(), CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM.toLong()])
    @CommonCodeStyleSettings.WrapConstant
    var ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS

    enum class PropertyAlignment(val id: Int,
            @PropertyKey(resourceBundle = CirJsonBundle.BUNDLE) private val myKey: String) {

        DO_NOT_ALIGN(0, "formatter.align.properties.none"),

        ALIGN_ON_VALUE(1, "formatter.align.properties.on.value"),

        ALIGN_ON_COLON(1, "formatter.align.properties.on.colon");

        val description: String
            get() = CirJsonBundle.message(this.myKey)

    }

    companion object {

        val DO_NOT_ALIGN_PROPERTY = PropertyAlignment.DO_NOT_ALIGN.id

        val ALIGN_PROPERTY_ON_VALUE = PropertyAlignment.ALIGN_ON_VALUE.id

        val ALIGN_PROPERTY_ON_COLON = PropertyAlignment.ALIGN_ON_COLON.id

    }

}