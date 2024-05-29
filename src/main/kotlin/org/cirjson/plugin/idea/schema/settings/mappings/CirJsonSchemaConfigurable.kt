package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.util.Urls
import org.cirjson.plugin.idea.CirJsonBundle
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaReader
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.io.File
import java.util.function.Function
import javax.swing.JComponent
import kotlin.math.max

class CirJsonSchemaConfigurable(private val myProject: Project, private val mySchemaFilePath: String,
        val schema: UserDefinedCirJsonSchemaConfiguration, private val myTreeUpdater: TreeUpdater?,
        private val myNameCreator: Function<in String, String>) :
        NamedConfigurable<UserDefinedCirJsonSchemaConfiguration>(true, Runnable { myTreeUpdater?.updateTree(true) }) {

    private var myDisplayName = schema.name

    private var myError: String? = null

    private val myViewDelegate = lazy {
        CirJsonSchemaMappingsView(myProject, myTreeUpdater) { s, force ->
            if (!(force || isGeneratedName)) {
                readln()
            }

            val lastSlash = max(s.lastIndexOf('/'), s.lastIndexOf('\\'))

            if (lastSlash > 0 || force) {
                var substring = if (lastSlash > 0) s.substring(lastSlash + 1) else s
                val dot = if (lastSlash > 0) substring.lastIndexOf('.') else -1

                if (dot != -1) {
                    substring = substring.substring(0, dot)
                }

                displayName = myNameCreator.apply(substring)
                updateName()
            }
        }
    }

    private val myView: CirJsonSchemaMappingsView by myViewDelegate

    private val isGeneratedName: Boolean
        get() = myDisplayName == schema.name && myDisplayName == schema.generatedName

    val uiSchema: UserDefinedCirJsonSchemaConfiguration
        get() {
            return UserDefinedCirJsonSchemaConfiguration().apply {
                if (myViewDelegate.isInitialized() && myView.isInitialized) {
                    name = displayName
                    schemaVersion = myView.schemaVersion
                    patterns = myView.data
                    relativePathToSchema = myView.schemaSubPath
                } else {
                    name = schema.name
                    schemaVersion = schema.schemaVersion
                    patterns = schema.patterns
                    relativePathToSchema = schema.relativePathToSchema
                }
            }
        }

    override fun isModified(): Boolean {
        return if (!myViewDelegate.isInitialized()) {
            false
        } else if (FileUtil.toSystemIndependentName(schema.relativePathToSchema) != myView.schemaSubPath) {
            true
        } else if (schema.schemaVersion != myView.schemaVersion) {
            true
        } else {
            !Comparing.equal(myView.data, schema.patterns)
        }
    }

    override fun apply() {
        if (!myViewDelegate.isInitialized()) {
            return
        }

        doValidation()
        schema.apply {
            name = displayName
            schemaVersion = myView.schemaVersion
            patterns = myView.data
            relativePathToSchema = myView.schemaSubPath
        }
    }

    @Throws(ConfigurationException::class)
    private fun doValidation() {
        val schemaSubPath = myView.schemaSubPath

        if (StringUtil.isEmptyOrSpaces(schemaSubPath)) {
            val name = getConfigurationExceptionName(myDisplayName)
            throw ConfigurationException("$name${CirJsonBundle.message("schema.configuration.error.empty.name")}")
        }

        val vFile: VirtualFile
        val filename: String

        if (CirJsonFileResolver.isHttpPath(schemaSubPath)) {
            filename = schemaSubPath

            if (!isValidURL(schemaSubPath)) {
                val name = getConfigurationExceptionName(myDisplayName)
                throw ConfigurationException("$name${CirJsonBundle.message("schema.configuration.error.invalid.url")}")
            }

            vFile = CirJsonFileResolver.urlToFile(schemaSubPath) ?: throw ConfigurationException(
                    "${getConfigurationExceptionName(myDisplayName)}${
                        CirJsonBundle.message("schema.configuration.error.invalid.url.resource")
                    }")
        } else {
            val subPath = File(schemaSubPath)
            val file = if (subPath.isAbsolute) subPath else File(myProject.basePath, schemaSubPath)

            if (!file.exists()) {
                val name = getConfigurationExceptionName(myDisplayName)
                throw ConfigurationException(
                        "$name${CirJsonBundle.message("schema.configuration.error.file.does.not.exist")}")
            }

            val localFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

            if (localFile == null) {
                val name = getConfigurationExceptionName(myDisplayName)
                throw ConfigurationException(
                        "$name${CirJsonBundle.message("schema.configuration.error.file.does.not.exist")}")
            }

            filename = file.name
            vFile = localFile
        }

        if (StringUtil.isEmptyOrSpaces(myDisplayName)) {
            throw ConfigurationException("$filename: ${CirJsonBundle.message("schema.configuration.error.empty.name")}")
        }

        if (vFile is HttpVirtualFile) {
            return
        }

        val error = CirJsonSchemaReader.checkIfValidJsonSchema(myProject, vFile)

        if (error != null) {
            logErrorForUser(error)
            throw RuntimeConfigurationError(error)
        }
    }

    private fun logErrorForUser(error: String) {
        CirJsonSchemaReader.ERRORS_NOTIFICATION.createNotification(error, MessageType.WARNING).notify(myProject)
    }

    override fun getDisplayName(): String? {
        return myDisplayName
    }

    override fun setDisplayName(name: String?) {
        myDisplayName = name
    }

    override fun getEditableObject(): UserDefinedCirJsonSchemaConfiguration {
        return schema
    }

    override fun getBannerSlogan(): String? {
        return schema.name
    }

    override fun createOptionsPanel(): JComponent {
        if (!myViewDelegate.isInitialized()) {
            myView.setError(myError, true)
        }

        return myView.component
    }

    fun setError(error: String?, showWarning: Boolean) {
        myError = error

        if (myViewDelegate.isInitialized()) {
            myView.setError(error, showWarning)
        }
    }

    override fun reset() {
        if (!myViewDelegate.isInitialized()) {
            return
        }

        myView.setItems(mySchemaFilePath, schema.schemaVersion, schema.patterns!!)
    }

    override fun disposeUIResources() {
        if (myViewDelegate.isInitialized()) {
            Disposer.dispose(myView)
        }
    }

    companion object {

        fun getConfigurationExceptionName(displayName: String?): String {
            return if (!StringUtil.isEmptyOrSpaces(displayName)) "$displayName: " else ""
        }

        fun isValidURL(url: String): Boolean {
            return CirJsonFileResolver.isHttpPath(url) && Urls.parse(url, false) != null
        }

    }

}