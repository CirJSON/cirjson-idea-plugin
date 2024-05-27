package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration

class CirJsonSchemaPatternComparator(private val myProject: Project) {

    fun isSimilar(itemLeft: UserDefinedCirJsonSchemaConfiguration.Item,
            itemRight: UserDefinedCirJsonSchemaConfiguration.Item): ThreeState {
        TODO()
    }

}