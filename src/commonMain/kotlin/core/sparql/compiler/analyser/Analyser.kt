package core.sparql.compiler.analyser

import core.sparql.compiler.Pattern
import core.sparql.compiler.StructuralError
import core.sparql.compiler.Token
import core.sparql.compiler.lexer.Lexer
import kotlin.jvm.JvmStatic

abstract class Analyser<T> {

    protected companion object {

        /* helper extensions */

        @JvmStatic
        protected fun Token.Term.asBinding(): Pattern.Binding? {
            return if (value[0] == '?') {
                Pattern.Binding(value.substring(1))
            } else {
                null
            }
        }

    }

    /** current token, actually kept here so `peek` does not actively `consume()` **/
    protected lateinit var token: Token
        private set
    /** lexer used for processing, responsible for receiving the next token **/
    private lateinit var lexer: Lexer

    /**
     * Processes starting from the input's current position and consumes every related item to its specific
     *  type T
     */
    fun process(input: Lexer): T {
        lexer = input
        consumeOrBail()
        return _process()
    }

    /**
     * Processes starting from the original analyzer's last token, reusing that position (so starting at "lexer - 1")
     */
    fun chain(from: Analyser<*>): T {
        lexer = from.lexer
        token = from.token
        return _process()
    }

    protected abstract fun _process(): T

    /** Consumes the next token. Bails if no other token is available **/
    protected fun consumeOrBail() {
        if (!lexer.hasNext()) {
            bail("Unexpected end of input (last token is $token)")
        } else {
            // simplified version of `singleOrNull()` that does not check if there are tokens remaining, removing
            //  the additional `take(1)` call
            token = lexer.next()
        }
    }

    protected fun expectToken(vararg tokens: Token) {
        if (token !in tokens) {
            expectedToken(*tokens)
        }
    }

    protected fun expectPatternElementOrToken(vararg tokens: Token) {
        if (token !is Token.Term && token !in tokens) {
            expectedPatternElementOrToken(*tokens)
        }
    }

    protected fun expectedToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, internal error"
            1 -> "Unexpected $token, expected ${tokens.first().syntax}"
            else -> "Unexpected $token, expected any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedPatternElementOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected pattern element"
            1 -> "Unexpected $token, expected pattern element or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected pattern element or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun bail(reason: String = "Internal compiler error"): Nothing {
        throw StructuralError(problem = "Failed at index ${lexer.position()}", description = reason)
    }

}
