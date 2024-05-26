package org.cirjson.plugin.idea.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PatternUtil
import com.intellij.util.SmartList
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xmlb.annotations.Tag
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaVersion
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.io.File
import java.util.function.BiPredicate

@Tag("SchemaInfo")
class UserDefinedCirJsonSchemaConfiguration(var name: String?, var schemaVersion: CirJsonSchemaVersion,
        private var myRelativePathToSchema: String?, var isApplicationDefined: Boolean,
        private val myPatterns: MutableList<Item>?) {

    var generatedName: String? = null

    private var myCalculatedPatterns = SynchronizedClearableLazy(this::recalculatePatterns)

    var patterns: List<Item>?
        get() = myPatterns
        set(value) {
            myPatterns!!.clear()
            value?.let { myPatterns.addAll(it) }
            myPatterns.sortWith(ITEM_COMPARATOR)
            myCalculatedPatterns.drop()
        }

    var isIgnoredFile = false

    var relativePathToSchema: String
        get() = Item.normalizePath(myRelativePathToSchema!!)
        set(value) {
            myRelativePathToSchema = Item.neutralizePath(value)
        }

    val calculatedPatterns: List<BiPredicate<Project, VirtualFile>>
        get() = myCalculatedPatterns.value

    private fun recalculatePatterns(): List<BiPredicate<Project, VirtualFile>> {
        val result = SmartList<BiPredicate<Project, VirtualFile>>()

        for (patternText in myPatterns!!) {
            when (patternText.mappingKind) {
                CirJsonMappingKind.FILE -> {
                    result.add(BiPredicate { project, vFile ->
                        vFile == getRelativeFile(project, patternText) || vFile.url == Item.neutralizePath(
                                patternText.path)
                    })
                }

                CirJsonMappingKind.PATTERN -> {
                    val pathText = FileUtil.toSystemIndependentName(patternText.path)
                    val pattern = if (pathText.isEmpty()) {
                        PatternUtil.NOTHING
                    } else if (pathText.indexOf('/') >= 0) {
                        PatternUtil.compileSafe("*/${PatternUtil.convertToRegex(pathText)}", PatternUtil.NOTHING)
                    } else {
                        PatternUtil.fromMask(pathText)
                    }

                    result.add(BiPredicate { _, file ->
                        val s = if (pathText.indexOf('/') >= 0) file.path else file.name
                        CirJsonSchemaObject.matchPattern(pattern, s)
                    })
                }

                CirJsonMappingKind.DIRECTORY -> {
                    result.add(BiPredicate { project, vFile ->
                        val relativeFile = getRelativeFile(project, patternText) ?: return@BiPredicate false

                        if (!VfsUtil.isAncestor(relativeFile, vFile, true)) {
                            return@BiPredicate false
                        }

                        val service = CirJsonSchemaService.get(project)
                        service.isApplicableToFile(vFile)
                    })
                }
            }
        }

        return result
    }

    fun refreshPatterns() {
        myCalculatedPatterns.drop()
    }

    class Item(path: String, mappingKind: CirJsonMappingKind) {

        var mappingKind = mappingKind
            private set

        var path = neutralizePath(path)
            get() = normalizePath(field)
            set(value) {
                field = neutralizePath(value)
            }

        val pathParts: Array<String>
            get() {
                return pathToParts(path)
            }

        var isPattern: Boolean
            get() = mappingKind == CirJsonMappingKind.PATTERN
            set(value) {
                mappingKind = if (value) CirJsonMappingKind.PATTERN else CirJsonMappingKind.FILE
            }

        var isDirectory: Boolean
            get() = mappingKind == CirJsonMappingKind.DIRECTORY
            set(value) {
                mappingKind = if (value) CirJsonMappingKind.DIRECTORY else CirJsonMappingKind.FILE
            }

        constructor(path: String, isPattern: Boolean, isDirectory: Boolean) : this(path, if (isPattern) {
            CirJsonMappingKind.PATTERN
        } else if (isDirectory) {
            CirJsonMappingKind.DIRECTORY
        } else {
            CirJsonMappingKind.FILE
        })

        companion object {

            fun normalizePath(path: String): String {
                if (preserveSlashes(path)) {
                    return path
                }

                return StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(path), File.separatorChar)
            }

            fun neutralizePath(path: String): String {
                if (preserveSlashes(path)) {
                    return path
                }

                return StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(path), "/")
            }

            private fun preserveSlashes(path: String): Boolean {
                return StringUtil.startsWith(path, "http:") || StringUtil.startsWith(path, "https:")
                        || CirJsonFileResolver.isTempOrMockUrl(path)
            }

        }

    }

    companion object {

        private val ITEM_COMPARATOR = Comparator<Item> { o1, o2 ->
            return@Comparator if (o1.isPattern != o2.isPattern) {
                if (o1.isPattern) -1 else 1
            } else if (o1.isDirectory != o2.isDirectory) {
                if (o1.isDirectory) -1 else 1
            } else {
                o1.path.compareTo(o2.path, ignoreCase = true)
            }
        }

        private fun getRelativeFile(project: Project, pattern: Item): VirtualFile? {
            project.basePath ?: return null

            val path = FileUtil.toSystemIndependentName(pattern.path)
            val parts = pathToPartsList(path)

            return if (parts.isEmpty()) {
                project.baseDir
            } else {
                VfsUtil.findRelativeFile(project.baseDir, *parts.toTypedArray())
            }
        }

        private fun pathToPartsList(path: String): List<String> {
            return StringUtil.split(path, "/").filter { it != "." }
        }

        private fun pathToParts(path: String): Array<String> {
            return pathToPartsList(path).toTypedArray()
        }

    }

}