package org.cirjson.plugin.idea.psi.impl

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import java.util.stream.IntStream
import kotlin.math.min

abstract class JSStringLiteralEscaper<T : PsiLanguageInjectionHost>(host: T) : LiteralTextEscaper<T>(host) {

    /**
     * Offset in injected string -> offset in host string
     *
     * Last element contains imaginary offset for the character after the last one in injected string. It would be host
     * string length.
     *
     * E.g. for "aa\nbb" it is [0,1,2,4,5,6]
     */
    private var myOutSourceOffsets = intArrayOf()

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val subText = rangeInsideHost.substring(myHost.text)

        val sourceOffsetsRef = Ref<IntArray>()
        val result = parseStringCharacters(subText, outChars, sourceOffsetsRef, isRegExpLiteral, !isOneLine)
        myOutSourceOffsets = sourceOffsetsRef.get()
        return result
    }

    protected abstract val isRegExpLiteral: Boolean

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        val result = if (offsetInDecoded < myOutSourceOffsets.size) myOutSourceOffsets[offsetInDecoded] else -1

        if (result == -1) {
            return -1
        }

        return min(result, rangeInsideHost.length) + rangeInsideHost.startOffset
    }

    override fun isOneLine(): Boolean {
        return false
    }

    companion object {

        fun parseStringCharacters(chars: String, outChars: StringBuilder, sourceOffsetsRef: Ref<IntArray>,
                regExp: Boolean, escapeBacktick: Boolean): Boolean {
            if (chars.indexOf('\\') < 0) {
                outChars.append(chars)
                sourceOffsetsRef.set(IntStream.range(0, chars.length + 1).toArray())
                return true
            }

            val sourceOffsets = IntArray(chars.length + 1)
            var index = 0
            val outOffset = outChars.length
            var result = true

            while (index < chars.length) {
                var c = chars[index++]

                sourceOffsets[outChars.length - outOffset] = index - 1
                sourceOffsets[outChars.length + 1 - outOffset] = index

                if (c != '\\') {
                    outChars.append(c)
                    continue
                }

                if (index == chars.length) {
                    result = false
                    break
                }

                c = chars[index++]

                if (escapeBacktick && c == '`') {
                    outChars.append(c)
                } else if (regExp) {
                    if (c != '/') {
                        outChars.append('\\')
                    }

                    outChars.append(c)
                } else {
                    when (c) {
                        'b' -> outChars.append('\b')
                        't' -> outChars.append('\t')
                        'n', '\n' -> outChars.append('\n')
                        'f' -> outChars.append('\u000c')
                        'r' -> outChars.append('\r')
                        '"' -> outChars.append('"')
                        '/' -> outChars.append('/')
                        '\'' -> outChars.append('\'')
                        '\\' -> outChars.append('\\')

                        '0', '1', '2', '3', '4', '5', '6', '7' -> {
                            val startC = c
                            var v = c.code - '0'.code

                            if (index < chars.length) {
                                c = chars[index++]

                                if (c.code in '0'.code..'7'.code) {
                                    v = v shl 3
                                    v += c.code - '0'.code

                                    if (startC.code <= '3'.code && index < chars.length) {
                                        c = chars[index++]

                                        if (c.code in '0'.code..'7'.code) {
                                            v = v shl 3
                                            v += c.code - '0'.code
                                        } else {
                                            index--
                                        }
                                    }
                                } else {
                                    index--
                                }
                            }

                            outChars.append(v.toChar())
                        }

                        'x' -> {
                            if (index + 2 <= chars.length) {
                                try {
                                    val v = chars.substring(index, index + 2).toInt(16)
                                    outChars.append(v.toChar())
                                    index += 2
                                } catch (e: Exception) {
                                    result = false
                                    break
                                }
                            } else {
                                result = false
                                break
                            }
                        }

                        'u' -> {
                            if (index + 3 <= chars.length && chars[index] == '{') {
                                val end = chars.indexOf('}', index + 1)

                                if (end < 0) {
                                    result = false
                                    break
                                }

                                try {
                                    val v = chars.substring(index + 1, end).toInt(16)
                                    c = chars[index + 1]

                                    if (c == '+' || c == '-') {
                                        result = false
                                        break
                                    }

                                    outChars.appendCodePoint(v)
                                    index = end + 1
                                } catch (e: Exception) {
                                    result = false
                                    break
                                }
                            } else if (index + 4 <= chars.length) {
                                try {
                                    val v = chars.substring(index, index + 4).toInt(16)
                                    c = chars[index]

                                    if (c == '+' || c == '-') {
                                        result = false
                                        break
                                    }

                                    outChars.append(v.toChar())
                                    index += 4
                                } catch (e: Exception) {
                                    result = false
                                    break
                                }
                            } else {
                                result = false
                                break
                            }
                        }

                        else -> outChars.append(c)
                    }
                }

                sourceOffsets[outChars.length - outOffset] = index
            }

            sourceOffsets[outChars.length - outOffset] = chars.length

            sourceOffsetsRef.set(sourceOffsets.copyOf(outChars.length - outOffset + 1))
            return result
        }

    }

}