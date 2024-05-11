package org.cirjson.plugin.idea.extentions.kotlin

fun Boolean?.trueOrNull(): Boolean = this ?: true

fun Boolean?.orFalse(): Boolean = this ?: false