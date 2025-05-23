@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.CompilerException
import dev.tesserakt.sparql.compiler.lexer.Lexer
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.QueryAtom
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class Analyser<RT: QueryAtom?> {

    /** available set of predicates, set according to the currently processed query **/
    protected lateinit var prefixes: MutableMap<String, String>
    /** lexer used for processing, responsible for receiving the next token **/
    private lateinit var lexer: Lexer
    /** current token being observed from the lexer **/
    protected val token: Token get() = lexer.current

    /**
     * Processes starting from the input's current position and consumes every related item to its specific
     *  type T
     */
    fun configureAndUse(input: Lexer): RT {
        lexer = input
        consume()
        return _process()
    }

    /**
     * Processes starting from the calling analyzer's last token, reusing that position (so starting at "lexer - 1")
     */
    protected fun <O: QueryAtom?> use(other: Analyser<O>): O {
        other.lexer = lexer
        other.prefixes = prefixes
        return other._process()
    }

    protected abstract fun _process(): RT

    /** Consumes the next token. The next token can be `EOF` if the end has been reached **/
    protected fun consume() {
        lexer.advance()
    }

    @OptIn(ExperimentalContracts::class)
    protected inline fun expect(predicate: Boolean, msg: String = "Requirement failed") {
        contract {
            returns() implies (predicate)
        }
        if (!predicate) {
            bail(msg)
        }
    }

    protected fun expectToken(vararg tokens: Token) {
        if (token !in tokens) {
            expectedToken(*tokens)
        }
    }

    protected fun expectBinding() {
        if (token !is Token.Binding) {
            expectedBinding()
        }
    }

    protected fun expectStringLiteral() {
        if (token !is Token.StringLiteral) {
            expectedStringLiteral()
        }
    }

    protected fun expectPatternElementOrBinding() {
        if (token is Token.Symbol) {
            expectedPatternElementOrBinding()
        }
    }

    protected fun expectPatternElementOrBindingOrToken(vararg tokens: Token) {
        if (token is Token.Symbol && token !in tokens) {
            expectedPatternElementOrBindingOrToken(*tokens)
        }
    }

    protected fun expectedToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, error"
            1 -> "Unexpected $token, expected ${tokens.first().syntax}"
            else -> "Unexpected $token, expected any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedPatternElement(): Nothing {
        bail("Unexpected $token, expected pattern element")
    }

    protected fun expectedBinding(): Nothing {
        bail("Unexpected $token, expected pattern element")
    }

    protected fun expectedStringLiteral(): Nothing {
        bail("Unexpected $token, expected string literal")
    }

    protected fun expectedPatternElementOrBinding(): Nothing {
        bail("Unexpected $token, expected pattern element or binding")
    }

    protected fun expectedPatternElementOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected pattern element"
            1 -> "Unexpected $token, expected pattern element or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected pattern element or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedBindingOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected binding"
            1 -> "Unexpected $token, expected binding or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected binding or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedLiteralOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected literal"
            1 -> "Unexpected $token, expected literal or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected literal or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedPatternElementOrBindingOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected pattern element or binding"
            1 -> "Unexpected $token, expected pattern element, binding or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected pattern element, binding or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun expectedBindingOrLiteralOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected binding or literal"
            1 -> "Unexpected $token, expected binding, literal or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected binding, literal or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun bail(message: String = "Internal compiler error"): Nothing {
        throw CompilerException(
            message = "Failed during the execution of `${this::class.simpleName!!}`",
            type = CompilerException.Type.StructuralError,
            stacktrace = lexer.stacktrace(message = message, type = CompilerException.Type.StructuralError)
        )
    }

}
