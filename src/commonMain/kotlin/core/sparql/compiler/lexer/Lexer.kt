package core.sparql.compiler.lexer

import core.sparql.compiler.SyntaxError
import core.sparql.compiler.Token
import kotlin.jvm.JvmStatic

abstract class Lexer: Iterator<Token> {

    protected companion object {

        // convenience map for extracting tokens out of the string segments
        @JvmStatic
        protected val lut = Token.syntax
            .toList()
            .groupBy { (syntax) -> syntax.first() }
            .mapValues { (_, list) -> list.sortedByDescending { it.first.length } }

        @JvmStatic
        protected fun String.indexOf(char: Char, startIndex: Int, endIndex: Int): Int {
            var i = startIndex
            while (i < endIndex) {
                if (this[i] == char) {
                    return i
                }
                ++i
            }
            return -1
        }

        @JvmStatic
        protected fun String.indexOfLast(char: Char, startIndex: Int, endIndex: Int = 0): Int {
            var i = startIndex
            while (i > endIndex - 1) {
                if (this[i] == char) {
                    return i
                }
                --i
            }
            return -1
        }

        @JvmStatic
        protected fun String.substringFromUntil(index: Int, until: Char): String {
            val end = indexOf(until, startIndex = index)
            return if (end != -1) substring(index, end) else substring(index)
        }

    }

    /**
     * Returns a visual overview for the current line being processed and a line indicating the currently observed
     *  range
     */
    abstract fun stacktrace(description: String): String

    abstract fun position(): String

    protected fun bail(reason: String = "Internal compiler error"): Nothing {
        throw SyntaxError(problem = "Syntax error at index ${position()}", description = reason)
    }

}
