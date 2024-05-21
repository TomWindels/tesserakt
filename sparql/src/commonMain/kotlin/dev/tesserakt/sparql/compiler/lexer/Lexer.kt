package dev.tesserakt.sparql.compiler.lexer

import dev.tesserakt.sparql.compiler.CompilerError
import dev.tesserakt.sparql.compiler.types.Token
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

abstract class Lexer {

    /** The currently processed token **/
    abstract val current: Token

    /** Advances the current token **/
    abstract fun advance()

    internal companion object {

        // convenience map for extracting tokens out of the string segments
        @JvmField
        internal val lut = Token.syntax
            .toList()
            .groupBy { (syntax) -> syntax.first().lowercaseChar() }
            .mapValues { (_, list) -> list.sortedByDescending { it.first.length } }

        @JvmStatic
        internal fun String.indexOf(vararg char: Char, startIndex: Int, endIndex: Int): Int {
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
        internal fun String.indexOfLast(vararg char: Char, startIndex: Int, endIndex: Int = 0): Int {
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
        internal fun String.substringFromUntil(index: Int, until: Char): String {
            val end = indexOf(until, startIndex = index)
            return if (end != -1) substring(index, end) else substring(index)
        }

    }

    /**
     * Returns a visual overview for the current line being processed and a line indicating the currently observed
     *  range
     */
    internal abstract fun stacktrace(type: CompilerError.Type, message: String): String

    internal abstract fun position(): Int

    protected fun bail(message: String = "Internal compiler error"): Nothing {
        throw CompilerError(
            message = "Syntax error at index ${position()}",
            type = CompilerError.Type.SyntaxError,
            stacktrace = stacktrace(CompilerError.Type.SyntaxError, message)
        )
    }

}
