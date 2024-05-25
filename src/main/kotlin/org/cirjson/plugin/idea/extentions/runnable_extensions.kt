package org.cirjson.plugin.idea.extentions

import kotlinx.coroutines.Runnable
import java.util.concurrent.Callable

fun Runnable.toCallable(): Callable<Void> {
    return Callable {
        run()
        null
    }
}
