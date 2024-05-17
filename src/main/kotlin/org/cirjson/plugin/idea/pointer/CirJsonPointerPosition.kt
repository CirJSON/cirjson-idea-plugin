package org.cirjson.plugin.idea.pointer

import com.intellij.util.containers.ContainerUtil
import org.cirjson.plugin.idea.schema.CirJsonPointerUtil

class CirJsonPointerPosition(val steps: MutableList<Step>) {

    constructor() : this(ArrayList())

    fun addPrecedingStep(value: Int) {
        steps.add(0, Step.createArrayElementStep(value))
    }

    fun addPrecedingStep(value: String) {
        steps.add(0, Step.createPropertyStep(value))
    }

    fun addFollowingStep(value: String) {
        steps.add(Step.createPropertyStep(value))
    }

    fun replaceStep(pos: Int, value: String) {
        steps[pos] = Step.createPropertyStep(value)
    }

    val empty: Boolean
        get() {
            return steps.isEmpty()
        }

    fun isArray(pos: Int): Boolean {
        return checkPosInRange(pos) && steps[pos].isFromArray
    }

    fun isObject(pos: Int): Boolean {
        return checkPosInRange(pos) && steps[pos].isFromObject
    }

    fun skip(count: Int): CirJsonPointerPosition? {
        return if (checkPosInRangeIncl(count)) {
            CirJsonPointerPosition(steps.subList(count, steps.size))
        } else {
            null
        }
    }

    fun trimTail(count: Int): CirJsonPointerPosition? {
        return if (checkPosInRangeIncl(count)) {
            CirJsonPointerPosition(steps.subList(0, steps.size - count))
        } else {
            null
        }
    }

    val lastName: String?
        get() {
            return steps.lastOrNull()?.name
        }

    val firstName: String?
        get() {
            return steps.firstOrNull()?.name
        }

    val firstIndex: Int
        get() {
            return ContainerUtil.getFirstItem(steps)?.idx ?: -1
        }

    val size: Int
        get() {
            return steps.size
        }

    fun updateFrom(from: CirJsonPointerPosition) {
        steps.clear()
        steps.addAll(from.steps)
    }

    fun toCirJsonPointer(): String {
        return "/" + steps.joinToString("/") {
            CirJsonPointerUtil.escapeForCirJsonPointer(it.name ?: it.idx.toString())
        }
    }

    private fun checkPosInRange(pos: Int): Boolean {
        return steps.size > pos
    }

    private fun checkPosInRangeIncl(pos: Int): Boolean {
        return steps.size >= pos
    }

    class Step private constructor(val name: String?, val idx: Int) {

        val isFromArray = name == null

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

    companion object {

        fun parsePointer(pointer: String): CirJsonPointerPosition {
            val chain = CirJsonPointerUtil.split(CirJsonPointerUtil.normalizeSlashes(pointer))
            val steps = ArrayList<Step>(chain.size)

            for (s in chain) {
                try {
                    steps.add(Step.createArrayElementStep(s.toInt()))
                } catch (_: NumberFormatException) {
                    steps.add(Step.createPropertyStep(CirJsonPointerUtil.unescapeCirJsonPointerPart(s)))
                }
            }

            return CirJsonPointerPosition(steps)
        }

        fun createSingleProperty(property: String): CirJsonPointerPosition {
            return CirJsonPointerPosition(ContainerUtil.createMaybeSingletonList(Step.createPropertyStep(property)))
        }

    }

}