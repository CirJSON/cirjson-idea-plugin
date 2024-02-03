package org.cirjson.plugin.idea.icons

import com.intellij.ui.IconManager
import javax.swing.Icon

object CirJsonIcons {

    val ICON = load("icons/cirJson.svg")

    val ICON_DARK = load("icons/cirJsonDark.svg")

//    val ARRAY = load("icons/array.svg")
//
//    val ARRAY_DARK = load("icons/arrayDark.svg")
//
//    val OBJECT = load("icons/object.svg")
//
//    val OBJECT_DARK = load("icons/objectDark.svg")

    private fun load(path: String): Icon {
        return IconManager.getInstance().getIcon(path, CirJsonIcons::class.java.classLoader)
    }

}