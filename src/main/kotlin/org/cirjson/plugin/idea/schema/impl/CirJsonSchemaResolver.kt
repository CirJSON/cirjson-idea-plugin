package org.cirjson.plugin.idea.schema.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.cirjson.plugin.idea.pointer.CirJsonPointerPosition
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonArrayValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonObjectValueAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonPropertyAdapter
import org.cirjson.plugin.idea.schema.extension.adapters.CirJsonValueAdapter
import java.util.*

class CirJsonSchemaResolver(private val myProject: Project, private val mySchema: CirJsonSchemaObject,
        private val myPosition: CirJsonPointerPosition) {

    constructor(myProject: Project, mySchema: CirJsonSchemaObject) : this(myProject, mySchema, CirJsonPointerPosition())

    fun detailedResolve(): MatchResult {
        val node = CirJsonSchemaVariantsTreeBuilder.buildTree(myProject, mySchema, myPosition, false)
        return MatchResult.create(node)
    }

    fun resolve(): Collection<CirJsonSchemaObject> {
        val result = detailedResolve()
        val list = LinkedList(result.mySchemas)

        for (myExcludingSchema in result.myExcludingSchemas) {
            list.addAll(myExcludingSchema)
        }

        return list
    }

    fun findNavigationTarget(element: PsiElement?): PsiElement? {
        val node = CirJsonSchemaVariantsTreeBuilder.buildTree(myProject, mySchema, myPosition, true)
        val schema = selectSchema(node, element, myPosition.empty) ?: return null
        val file = CirJsonSchemaService.get(myProject).resolveSchemaFile(schema) ?: return null
        val psiFile = PsiManager.getInstance(myProject).findFile(file) ?: return null
        val walker = CirJsonLikePsiWalker.getWalker(psiFile, schema) ?: return null
        return resolvePosition(walker, psiFile, CirJsonPointerPosition.parsePointer(schema.pointer))
    }

    companion object {

        fun selectSchema(resolveRoot: CirJsonSchemaTreeNode, element: PsiElement?,
                topLevelSchema: Boolean): CirJsonSchemaObject? {
            val matchResult = MatchResult.create(resolveRoot)
            val schemas = ArrayList(matchResult.mySchemas)
            schemas.addAll(matchResult.myExcludingSchemas.flatten())

            val firstSchema = getFirstValidSchema(schemas)

            if (element == null || schemas.size == 1 || firstSchema == null) {
                return firstSchema
            }

            val walker = CirJsonLikePsiWalker.getWalker(element, firstSchema)
            val adapter = walker?.createValueAdapter(element) ?: return null

            val parentAdapter = if (topLevelSchema) {
                null
            } else {
                val parentValue = walker.getParentContainer(element) ?: return null
                walker.createValueAdapter(parentValue) ?: return null
            }

            val schemaRef = Ref<CirJsonSchemaObject>()
            MatchResult.iterateTree(resolveRoot) { node ->
                val parent = node.parent

                if (node.schema == null || parentAdapter != null && parent != null && parent.nothing) {
                    true
                } else if (!isCorrect(adapter, node.schema)) {
                    true
                } else if (parentAdapter == null || parent == null || parent.schema == null || parent.any || isCorrect(
                                parentAdapter, parent.schema)) {
                    schemaRef.set(node.schema)
                    false
                } else {
                    true
                }
            }

            return schemaRef.get()
        }

        private fun getFirstValidSchema(schemas: List<CirJsonSchemaObject>): CirJsonSchemaObject? {
            return schemas.firstOrNull()
        }

        private fun isCorrect(value: CirJsonValueAdapter, schema: CirJsonSchemaObject): Boolean {
            val type = CirJsonSchemaType.getType(value) ?: return true

            if (!CirJsonSchemaAnnotatorChecker.areSchemaTypesCompatible(schema, type)) {
                return false
            }

            val checker = CirJsonSchemaAnnotatorChecker(value.delegate.project,
                    CirJsonComplianceCheckerOptions.RELAX_ENUM_CHECK)
            checker.checkByScheme(value, schema)
            return checker.isCorrect
        }

        private fun resolvePosition(walker: CirJsonLikePsiWalker, element: PsiElement?,
                position: CirJsonPointerPosition): PsiElement? {
            var realPosition: CirJsonPointerPosition? = position

            val psiElement = if (element is PsiFile) {
                walker.getRoots(element)?.firstOrNull()
            } else {
                element
            } ?: return null

            var value = walker.createValueAdapter(psiElement)

            while (realPosition != null && !realPosition.empty) {
                if (value is CirJsonObjectValueAdapter) {
                    val name = realPosition.firstName ?: return null
                    val property = findProperty(value, name)

                    if (property != null) {
                        value = getValue(property) ?: return null
                    } else {
                        val props = findProperty(value, CirJsonSchemaObject.PROPERTIES)

                        if (props != null) {
                            value = getValue(props)
                            continue
                        }

                        val defs = findProperty(value, CirJsonSchemaObject.DEFINITIONS)

                        if (defs != null) {
                            value = getValue(defs)
                            continue
                        }

                        val defs9 = findProperty(value, CirJsonSchemaObject.DEFINITIONS_v9)

                        if (defs9 != null) {
                            value = getValue(defs9)
                            continue
                        }

                        return null
                    }
                } else if (value is CirJsonArrayValueAdapter) {
                    val index = realPosition.firstIndex

                    if (index >= 0) {
                        val values = value.elements

                        if (values.size > index) {
                            value = values[index]
                        } else {
                            return null
                        }
                    }
                }

                realPosition = realPosition.skip(1)
            }

            val delegate = value?.delegate ?: return null
            val propertyNameElement = walker.getPropertyNameElement(delegate.parent)
            return propertyNameElement ?: delegate
        }

        private fun findProperty(value: CirJsonObjectValueAdapter, name: String): CirJsonPropertyAdapter? {
            return value.propertyList.find { it.name == name }
        }

        private fun getValue(property: CirJsonPropertyAdapter): CirJsonValueAdapter? {
            val values = property.values
            return if (values.size == 1) values.first() else null
        }

    }

}