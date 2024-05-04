@file:Suppress("UnstableApiUsage")

package org.cirjson.plugin.idea.schema.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class CirJsonSchemaUsageTriggerCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    @Suppress("CompanionObjectInExtension")
    companion object {

        private val GROUP = EventLogGroup("cirjson.schema", 1)

        private val COMPLETION_BY_SCHEMA_INVOKED = GROUP.registerEvent("completion.by.schema.invoked",
                EventFields.String("schemaKind", listOf("builtin", "schema", "user", "remote")))

        fun trigger(feature: String) {
            COMPLETION_BY_SCHEMA_INVOKED.log(feature)
        }

    }

}