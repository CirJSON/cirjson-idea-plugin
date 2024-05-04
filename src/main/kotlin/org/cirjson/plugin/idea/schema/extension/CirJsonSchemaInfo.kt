package org.cirjson.plugin.idea.schema.extension

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaType
import java.io.File
import java.nio.file.Paths

class CirJsonSchemaInfo private constructor(val provider: CirJsonSchemaFileProvider?, private val myUrl: String?) {

    var documentation: String? = null

    var name: String? = null

    constructor(provider: CirJsonSchemaFileProvider) : this(provider, null)

    constructor(url: String) : this(null, url)

    fun getUrl(project: Project): String {
        if (provider != null) {
            val remoteSource = provider.remoteSource

            if (remoteSource != null) {
                return remoteSource
            }

            val schemaFile = provider.schemaFile ?: return ""

            if (schemaFile is HttpVirtualFile) {
                return schemaFile.url
            }

            if (schemaFile.fileSystem is JarFileSystem) {
                return schemaFile.url
            }

            return getRelativePath(project, schemaFile.path)
        } else {
            return myUrl!!
        }
    }

    val description: String
        get() {
            if (provider != null) {
                val providerName = provider.presentableName
                return sanitizeName(providerName)
            }

            if (name != null) {
                return name!!
            }

            if (myUrl!! in ourWeirdNames) {
                return ourWeirdNames[myUrl]!!
            }

            val url = myUrl.replace('\\', '/')

            return StringUtil.split(url, "/").reversed().firstOrNull { !isVeryDumbName(it) } ?: myUrl
        }

    companion object {

        // "angular" is the only angular-related schema is the 'angular-cli', so we skip the repo name
        private val ourDumbNames = setOf("schema", "lib", "cli", "packages", "master", "format", "angular", "config")

        private val ourWeirdNames = mapOf("http://json.schemastore.org/config" to "asp.net config",
                "https://schemastore.azurewebsites.net/schemas/json/config.json" to "asp.net config",
                "http://json.schemastore.org/2.0.0-csd.2.beta.2018-10-10.json" to "sarif-2.0.0-csd.2.beta.2018-10-10",
                "https://schemastore.azurewebsites.net/schemas/json/2.0.0-csd.2.beta.2018-10-10.json" to "sarif-2.0.0-csd.2.beta.2018-10-10")

        fun getRelativePath(project: Project, text: String): String {
            val realText = text.trim()

            if (project.isDefault || project.basePath == null || StringUtil.isEmptyOrSpaces(realText)) {
                return realText
            }

            val file = Paths.get(realText)

            if (!file.isAbsolute) {
                return realText
            }

            val relativePath =
                    FileUtil.getRelativePath(project.basePath!!, FileUtil.toSystemIndependentName(file.toString()), '/')

            if (relativePath != null) {
                return relativePath
            }

            val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file) ?: return realText
            val projectBaseDir = LocalFileSystem.getInstance().findFileByPath(project.basePath!!) ?: return realText

            if (isMeaningfulAncestor(VfsUtilCore.getCommonAncestor(virtualFile, projectBaseDir))) {
                val path = VfsUtilCore.findRelativePath(projectBaseDir, virtualFile, File.separatorChar)

                if (path != null) {
                    return path
                }
            }

            return realText
        }

        private fun isMeaningfulAncestor(ancestor: VirtualFile?): Boolean {
            ancestor ?: return false
            val homeDir = VfsUtil.getUserHomeDir()
            return homeDir != null && VfsUtilCore.isAncestor(homeDir, ancestor, true)
        }

        private fun sanitizeName(providerName: String): String {
            return StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".cirjson"), "-schema"),
                    ".schema")
        }

        fun isVeryDumbName(possibleName: String?): Boolean {
            if (StringUtil.isEmptyOrSpaces(possibleName) || possibleName in ourDumbNames) {
                return true
            }

            return possibleName!!.split(".").all { CirJsonSchemaType.isInteger(it) }
        }

    }

}