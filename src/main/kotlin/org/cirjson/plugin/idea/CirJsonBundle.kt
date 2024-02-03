package org.cirjson.plugin.idea

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object CirJsonBundle {

    @NonNls
    const val BUNDLE = "messages.CirJsonBundle"

    private val INSTANCE = DynamicBundle(CirJsonBundle::class.java, BUNDLE)

    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
        return INSTANCE.getMessage(key, *params)
    }

    fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<@Nls String> {
        return INSTANCE.getLazyMessage(key, *params)
    }

}