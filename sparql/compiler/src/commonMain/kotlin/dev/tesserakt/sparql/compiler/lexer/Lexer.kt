package dev.tesserakt.sparql.compiler.lexer

import dev.tesserakt.sparql.compiler.CompilerException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

abstract class Lexer {

    /** The currently processed token **/
    abstract val current: Token

    /** Advances the current token **/
    abstract fun advance()

    companion object {

        // convenience map for extracting tokens out of the string segments
        @JvmField
        val lut = Token.syntax
            .toList()
            .groupBy { (syntax) -> syntax.first().lowercaseChar() }
            .mapValues { (_, list) -> list.sortedByDescending { it.first.length } }

        @JvmStatic
        fun String.indexOf(vararg char: Char, startIndex: Int, endIndex: Int): Int {
            var i = startIndex
            while (i < endIndex) {
                if (char.any { it == this[i]}) {
                    return i
                }
                ++i
            }
            return -1
        }

        @JvmStatic
        fun String.indexOfLast(vararg char: Char, startIndex: Int, endIndex: Int = 0): Int {
            var i = startIndex
            while (i > endIndex - 1) {
                if (char.any { it == this[i]}) {
                    return i
                }
                --i
            }
            return -1
        }

        @JvmStatic
        fun String.substringFromUntil(index: Int, until: Char): String {
            val end = indexOf(until, startIndex = index)
            return if (end != -1) substring(index, end) else substring(index)
        }

    }

    /**
     * Returns a visual overview for the current line being processed and a line indicating the currently observed
     *  range
     */
    abstract fun stacktrace(type: CompilerException.Type, message: String): String

    abstract fun position(): Int

    protected fun bail(message: String = "Internal compiler error"): Nothing {
        throw CompilerException(
            message = "Syntax error at index ${position()}",
            type = CompilerException.Type.SyntaxError,
            stacktrace = stacktrace(CompilerException.Type.SyntaxError, message)
        )
    }

}
