package org.cirjson.plugin.idea

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.psi.CirJsonObject
import org.cirjson.plugin.idea.psi.CirJsonProperty
import org.cirjson.plugin.idea.schema.CirJsonSchemaService
import org.cirjson.plugin.idea.schema.extension.CirJsonLikePsiWalker
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaObject
import org.cirjson.plugin.idea.schema.impl.CirJsonSchemaResolver

abstract class CirJsonSchemaSpellcheckerClient {

    protected abstract val element: PsiElement

    protected abstract val value: String?

    fun matchesNameFromSchema(): Boolean {
        val file = PsiUtilCore.getVirtualFile(element) ?: return false

        val project = element.project
        val service = CirJsonSchemaService.get(project)

        if (!service.isApplicableToFile(file)) {
            return false
        }

        val rootSchema = service.getSchemaObject(element.containingFile) ?: return false

        if (isXIntellijInjection(service, rootSchema)) {
            return true
        }

        val value = this.value ?: return false

        if (StringUtil.isEmpty(value)) {
            return false
        }

        val walker = CirJsonLikePsiWalker.getWalker(element, rootSchema) ?: return false
        val ckeckable = walker.findElementToCheck(element) ?: return false
        val isName = walker.isName(ckeckable)
        val position = walker.findPosition(ckeckable, isName == ThreeState.NO)

        if (position == null || position.empty && isName == ThreeState.NO) {
            return false
        }

        val schemas = CirJsonSchemaResolver(project, rootSchema, position).resolve()

        if (schemas.isEmpty()) {
            return false
        }

        return ContainerUtil.exists(schemas) {
            if (it.properties.containsKey(value) || it.getMatchingPatternPropertySchema(value) != null) {
                return@exists true
            }

            return@exists ContainerUtil.exists(ContainerUtil.notNullize(it.enum)) { e ->
                e is String && StringUtil.unquoteString(e) == value
            }
        }
    }

    protected open fun isXIntellijInjection(service: CirJsonSchemaService, rootSchema: CirJsonSchemaObject): Boolean {
        if (!service.isSchemaFile(rootSchema)) {
            return false
        }

        val property = ObjectUtils.tryCast(element.parent, CirJsonProperty::class.java) ?: return false

        if (property.name == CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION) {
            return true
        }

        if (property.name == "language") {
            val parent = property.parent

            if (parent is CirJsonObject) {
                val grandParent = parent.parent

                if (grandParent is CirJsonProperty &&
                        grandParent.name == CirJsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION) {
                    return true
                }
            }
        }

        return false
    }

}