package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.pointer.CirJsonPointerResolver
import org.cirjson.plugin.idea.psi.*
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.util.*
import javax.swing.Icon

class CirJsonPointerReferenceProvider(private val myIsSchemaProperty: Boolean) : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is CirJsonStringLiteral) {
            return emptyArray()
        }

        val refs = mutableListOf<PsiReference>()
        val fragments = element.textFragments

        if (fragments.size != 1) {
            return emptyArray()
        }

        val fragment = fragments[0]
        val originalText = element.text
        val hash = originalText.indexOf("#")
        val splitter = CirJsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(fragment.second)
        val id = splitter.schemaId

        if (id != null) {
            if (id.startsWith("#")) {
                refs.add(CirJsonSchemaIdReference(element, id))
            } else {
                addFileOrWebReferences(element, refs, hash, id)
            }
        }

        if (!myIsSchemaProperty) {
            val relativePath =
                    CirJsonPointerUtil.normalizeSlashes(CirJsonPointerUtil.normalizeId(splitter.relativePath))

            if (!StringUtil.isEmpty(relativePath)) {
                val parts1 = CirJsonPointerUtil.split(relativePath)
                val strings = parts1.toTypedArray()
                val parts2 =
                        CirJsonPointerUtil.split(CirJsonPointerUtil.normalizeSlashes(originalText.substring(hash + 1)))

                if (strings.size == parts2.size) {
                    var start = hash + 2

                    for (i in parts2.indices) {
                        var length = parts2[i].length

                        if (i == parts1.size - 1) {
                            length--
                        }

                        if (length <= 0) {
                            break
                        }

                        refs.add(CirJsonPointerReference(element, TextRange(start, start + length),
                                "${id ?: ""}#/${StringUtil.join(strings, 0, i + 1, "/")}"))
                        start += length + 1
                    }
                }
            }
        }

        return refs.toTypedArray()
    }

    private fun addFileOrWebReferences(element: PsiElement, refs: MutableList<PsiReference>, hashIndex: Int,
            id: String) {
        if (CirJsonFileResolver.isHttpPath(id)) {
            refs.add(WebReference(element, TextRange(1, if (hashIndex >= 0) hashIndex else id.length + 1), id))
            return
        }

        val isCompletion = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED in id

        ContainerUtil.addAll(refs,
                *object : FileReferenceSet(id, element, 1, null, true, true, arrayOf(CirJsonFileType.INSTANCE)) {

                    override fun isEmptyPathAllowed(): Boolean {
                        return true
                    }

                    override fun isSoft(): Boolean {
                        return true
                    }

                    override fun createFileReference(range: TextRange, index: Int, text: String): FileReference? {
                        var realRange = range
                        var realText = text

                        if (hashIndex != -1 && realRange.startOffset >= hashIndex) {
                            return null
                        }

                        if (hashIndex != -1 && realRange.endOffset > hashIndex) {
                            realRange = TextRange(realRange.startOffset, hashIndex)
                            realText = realText.substring(0, realText.indexOf('#'))
                        }

                        return object : FileReference(this, realRange, index, realText) {

                            override fun createLookupItem(candidate: PsiElement?): Any? {
                                return FileInfoManager.getFileLookupItem(candidate)
                            }

                            override fun getVariants(): Array<Any> {
                                val fileVariants = super.getVariants()

                                if (!isCompletion && rangeInElement.startOffset != 1) {
                                    return fileVariants
                                }

                                return ArrayUtil.mergeArrays(fileVariants, collectCatalogVariants())
                            }

                            private fun collectCatalogVariants(): Array<Any> {
                                val elements = ArrayList<LookupElement>()
                                val project = this.element.project
                                val schemas = CirJsonSchemaService.get(project).allUserVisibleSchemas

                                for (schema in schemas) {
                                    var variantElement = LookupElementBuilder.create(schema.getUrl(project))
                                            .withPresentableText(schema.description)
                                            .withLookupString(schema.description).withIcon(AllIcons.General.Web)
                                            .withTypeText(schema.documentation, true)
                                    schema.name?.let { variantElement = variantElement.withLookupString(it) }
                                    schema.documentation?.let { variantElement = variantElement.withLookupString(it) }
                                    elements.add(PrioritizedLookupElement.withPriority(variantElement, -1.0))
                                }

                                return elements.toTypedArray()
                            }

                        }
                    }

                }.allReferences)
    }

    class CirJsonSchemaIdReference(element: CirJsonValue, private val myText: String) :
            CirJsonSchemaBaseReference<CirJsonValue>(element, getRange(element)) {

        override fun resolveInner(): PsiElement? {
            val id = CirJsonCachedValues.resolveId(myElement.containingFile, myText) ?: return null
            return resolveForPath(myElement, "#$id", false)
        }

        override fun getVariants(): Array<Any> {
            return CirJsonCachedValues.getAllIdsInFile(myElement.containingFile).toTypedArray()
        }

        companion object {

            private fun getRange(element: CirJsonValue): TextRange {
                val range = element.textRange.shiftLeft(element.textOffset)
                return TextRange(range.startOffset + 1, range.endOffset - 1)
            }

        }

    }

    internal class CirJsonPointerReference(element: CirJsonValue, textRange: TextRange,
            private val myFullPath: String) : CirJsonSchemaBaseReference<CirJsonValue>(element, textRange) {

        override fun getCanonicalText(): String {
            return myFullPath
        }

        override fun resolveInner(): PsiElement? {
            return resolveForPath(myElement, canonicalText, false)
        }

        override fun isIdenticalTo(that: CirJsonSchemaBaseReference<*>): Boolean {
            return super.isIdenticalTo(that) && rangeInElement == that.rangeInElement
        }

        override fun getVariants(): Array<Any> {
            var text = canonicalText
            val index = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)

            if (index < 0) {
                return emptyArray()
            }

            val part = text.substring(0, index)
            text = prepare(part)
            var prefix: String? = null
            var element = resolveForPath(myElement, text, true)
            val indexOfSlash = part.lastIndexOf('/')

            if (indexOfSlash != -1 && indexOfSlash < text.lastIndex) {
                prefix = text.substring(indexOfSlash + 1)
                element = resolveForPath(myElement, prepare(text.substring(0, indexOfSlash)), false)
            }

            val finalPrefix = prefix

            return when (element) {
                is CirJsonObject -> {
                    element.propertyList.filter {
                        it.value is CirJsonContainer && (finalPrefix == null || it.name.startsWith(finalPrefix))
                    }.map {
                        LookupElementBuilder.create(it, CirJsonPointerUtil.escapeForCirJsonPointer(it.name))
                                .withIcon(getIcon(it.value))
                    }.toTypedArray()
                }

                is CirJsonArray -> {
                    val list = element.valueList
                    val values = LinkedList<Any>()

                    for (i in list.indices) {
                        val stringValue = i.toString()

                        if (prefix != null && !stringValue.startsWith(prefix)) {
                            continue
                        }

                        values.add(LookupElementBuilder.create(stringValue).withIcon(getIcon(list[i])))
                    }

                    values.toTypedArray()
                }

                else -> {
                    emptyArray()
                }
            }
        }

        companion object {

            private fun getIcon(value: CirJsonValue?): Icon {
                return when (value) {
                    is CirJsonObject -> {
                        AllIcons.Json.Object
                    }

                    is CirJsonArray -> {
                        AllIcons.Json.Array
                    }

                    else -> {
                        IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
                    }
                }
            }

        }

    }

    companion object {

        internal fun resolveForPath(element: PsiElement, text: String, alwaysRoot: Boolean): PsiElement? {
            val service = CirJsonSchemaService.get(element.project)
            val splitter = CirJsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(text)
            var schemaFile = CompletionUtil.getOriginalOrSelf(element.containingFile).virtualFile

            if (splitter.isAbsolute) {
                schemaFile = service.findSchemaFileByReference(splitter.schemaId!!, schemaFile) ?: return null
            }

            val psiFile = element.manager.findFile(schemaFile)

            val normalized = CirJsonPointerUtil.normalizeId(splitter.relativePath)

            if (!alwaysRoot && (StringUtil.isEmptyOrSpaces(normalized) || CirJsonPointerUtil.split(
                            CirJsonPointerUtil.normalizeSlashes(normalized)).isEmpty()) || psiFile !is CirJsonFile) {
                return psiFile
            }

            val chain = CirJsonPointerUtil.split(CirJsonPointerUtil.normalizeSlashes(normalized))
            service.getSchemaObjectForSchemaFile(schemaFile) ?: return null

            val value = psiFile.topLevelValue ?: return psiFile
            return CirJsonPointerResolver(value, StringUtil.join(chain, "/")).resolve()
        }

        private fun prepare(part: String): String {
            return if (part.endsWith("#/")) part else StringUtil.trimEnd(part, '/')
        }

    }

}