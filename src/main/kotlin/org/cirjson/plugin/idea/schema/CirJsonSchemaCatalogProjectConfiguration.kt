package org.cirjson.plugin.idea.schema

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xmlb.annotations.Tag

@State(name = "CirJsonSchemaCatalogProjectConfiguration", storages = [Storage("cirJsonCatalog.xml")])
open class CirJsonSchemaCatalogProjectConfiguration :
        PersistentStateComponent<CirJsonSchemaCatalogProjectConfiguration.MyState> {

    @Volatile
    var myState = MyState()

    private val myChangeHandlers = ContainerUtil.createConcurrentList<Runnable>()

    val isCatalogEnabled: Boolean
        get() {
            val state = this.state
            return state != null && state.myIsCatalogEnabled
        }

    val isPreferRemoteSchemas: Boolean
        get() {
            val state = this.state
            return state != null && state.myIsPreferRemoteSchemas
        }

    fun addChangeHandler(runnable: Runnable) {
        myChangeHandlers.add(runnable)
    }

    override fun getState(): MyState? {
        return myState
    }

    val isRemoteActivityEnabled: Boolean
        get() {
            val state = this.state
            return state != null && state.myIsRemoteActivityEnabled
        }

    override fun loadState(state: MyState) {
        myState = state

        for (handler in myChangeHandlers) {
            handler.run()
        }
    }

    class MyState internal constructor(@Tag("enabled") val myIsCatalogEnabled: Boolean,
            @Tag("remoteActivityEnabled") val myIsRemoteActivityEnabled: Boolean,
            @Tag("preferRemoteSchemas") val myIsPreferRemoteSchemas: Boolean) {

        internal constructor() : this(true, true, false)

    }

    companion object {

        fun getInstance(project: Project): CirJsonSchemaCatalogProjectConfiguration {
            return project.getService(CirJsonSchemaCatalogProjectConfiguration::class.java)
        }

    }

}