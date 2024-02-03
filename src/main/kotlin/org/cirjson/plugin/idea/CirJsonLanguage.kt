package org.cirjson.plugin.idea

import com.intellij.lang.Language

class CirJsonLanguage private constructor() : Language(CirJsonConstants.CirJson) {

    companion object {

        val INSTANCE = CirJsonLanguage()

    }

}