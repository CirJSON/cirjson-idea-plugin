package org.cirjson.plugin.idea.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.psi.*

class CirJsonStructureViewElement(element: CirJsonElement) : StructureViewTreeElement {

    private val myElement: CirJsonElement

    init {
        assert(PsiTreeUtil.instanceOf(element, CirJsonFile::class.java, CirJsonProperty::class.java,
                CirJsonObject::class.java, CirJsonArray::class.java))
        myElement = element
    }

    override fun getValue(): CirJsonElement {
        return myElement
    }

    override fun getPresentation(): ItemPresentation {
        return myElement.presentation!!
    }

    override fun getChildren(): Array<TreeElement> {
        val value = currentValue
        return if (value is CirJsonObject) {
            ContainerUtil.map2Array(value.propertyList, TreeElement::class.java) { property ->
                CirJsonStructureViewElement(property)
            }
        } else if (value is CirJsonArray) {
            val childObjects = ContainerUtil.mapNotNull(value.valueList) { v ->
                if (v is CirJsonObject && v.propertyList.isNotEmpty()) {
                    CirJsonStructureViewElement(v)
                } else if (v is CirJsonArray && PsiTreeUtil.findChildOfType(v, CirJsonProperty::class.java) != null) {
                    CirJsonStructureViewElement(v)
                } else {
                    null
                }
            }
            return childObjects.toTypedArray()
        } else {
            TreeElement.EMPTY_ARRAY
        }
    }

    private val currentValue: CirJsonElement?
        get() {
            return if (myElement is CirJsonFile) {
                myElement.topLevelValue
            } else if (myElement is CirJsonProperty) {
                myElement.value
            } else if (PsiTreeUtil.instanceOf(myElement, CirJsonObject::class.java, CirJsonArray::class.java)) {
                myElement
            } else {
                null
            }
        }

}