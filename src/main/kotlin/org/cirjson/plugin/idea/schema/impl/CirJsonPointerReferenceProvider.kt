package org.cirjson.plugin.idea.schema.impl

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.CirJsonFileType
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral
import org.cirjson.plugin.idea.psi.CirJsonValue
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver

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

                        return object : FileReference(this, range, index, text) {

                            override fun createLookupItem(candidate: PsiElement?): Any? {
                                return FileInfoManager.getFileLookupItem(candidate)
                            }

                            private fun collectCatalogVariants(): Array<Any> {
                                val elements = ArrayList<LookupElement>()
                                val project = this.element.project
                                val schemas = CirJsonSchemaService.get(project).allUserVisibleSchemas
                                TODO()
                            }

                        }
                    }

                }.allReferences)
    }

    class CirJsonSchemaIdReference(element: CirJsonValue, private val myText: String) :
            CirJsonSchemaBaseReference<CirJsonValue>(element, getRange(element)) {

        override fun resolveInner(): PsiElement? {
            TODO()
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

        override fun resolveInner(): PsiElement? {
            TODO()
        }

    }

}