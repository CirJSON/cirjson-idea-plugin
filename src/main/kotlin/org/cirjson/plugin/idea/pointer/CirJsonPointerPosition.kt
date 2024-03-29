package org.cirjson.plugin.idea.pointer

import com.intellij.util.containers.ContainerUtil

class CirJsonPointerPosition(private val mySteps: MutableList<Step>) {

    constructor() : this(ArrayList())

    fun addPrecedingStep(value: Int) {
        mySteps.add(0, Step.createArrayElementStep(value))
    }

    fun addPrecedingStep(value: String) {
        mySteps.add(0, Step.createPropertyStep(value))
    }

    val empty: Boolean
        get() {
            return mySteps.isEmpty()
        }

    fun isObject(pos: Int): Boolean {
        return checkPosInRange(pos) && mySteps[pos].isFromObject
    }

    fun skip(count: Int): CirJsonPointerPosition? {
        return if (checkPosInRangeIncl(count)) {
            CirJsonPointerPosition(mySteps.subList(count, mySteps.size))
        } else {
            null
        }
    }

    val firstName: String?
        get() {
            return ContainerUtil.getFirstItem(mySteps)?.name
        }

    val firstIndex: Int
        get() {
            return ContainerUtil.getFirstItem(mySteps)?.idx ?: -1
        }

    val size: Int
        get() {
            return mySteps.size
        }

    fun updateFrom(from: CirJsonPointerPosition) {
        mySteps.clear()
        mySteps.addAll(from.mySteps)
    }

    private fun checkPosInRange(pos: Int): Boolean {
        return mySteps.size > pos
    }

    private fun checkPosInRangeIncl(pos: Int): Boolean {
        return mySteps.size >= pos
    }

    class Step private constructor(val name: String?, val idx: Int) {

        val isFromObject = name != null

        companion object {

            fun createPropertyStep(name: String): Step {
                return Step(name, -1)
            }

            fun createArrayElementStep(idx: Int): Step {
                assert(idx >= 0)
                return Step(null, idx)
            }

        }

    }

}