package org.cirjson.plugin.idea.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

@Service(Service.Level.PROJECT)
class CirJsonDependencyModificationTracker : SimpleModificationTracker() {

    companion object {

        fun forProject(project: Project): CirJsonDependencyModificationTracker {
            return project.getService(CirJsonDependencyModificationTracker::class.java)
        }

    }

}