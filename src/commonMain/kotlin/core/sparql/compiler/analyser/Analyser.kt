package core.sparql.compiler.analyser

import core.sparql.compiler.StructuralError
import core.sparql.compiler.lexer.Lexer
import core.sparql.compiler.types.Token

abstract class Analyser<AST> {

    /** available set of predicates, set according to the currently processed query **/
    protected lateinit var prefixes: MutableMap<String, String>
    /** current token, actually kept here so `peek` does not actively `consume()` **/
    protected lateinit var token: Token
        private set
    /** lexer used for processing, responsible for receiving the next token **/
    private lateinit var lexer: Lexer

    /**
     * Processes starting from the input's current position and consumes every related item to its specific
     *  type T
     */
    protected fun configureAndUse(input: Lexer): AST {
        lexer = input
        consumeOrBail()
        return _process()
    }

    /**
     * Processes starting from the calling analyzer's last token, reusing that position (so starting at "lexer - 1")
     */
    protected fun <O> use(other: Analyser<O>): O {
        other.lexer = lexer
        other.token = token
        other.prefixes = prefixes
        val result = other._process()
        // resetting our current token back
        token = other.token
        return result
    }

    protected abstract fun _process(): AST

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

    protected fun expectPatternElement() {
        if (token !is Token.Term) {
            expectedPatternElement()
        }
    }

    protected fun expectBinding() {
        if (token !is Token.Binding) {
            expectedBinding()
        }
    }

    protected fun expectPatternElementOrBinding() {
        if (token is Token.Syntax) {
            expectedPatternElementOrBinding()
        }
    }

    protected fun expectPatternElementOrToken(vararg tokens: Token) {
        if (token !is Token.Term && token !in tokens) {
            expectedPatternElementOrToken(*tokens)
        }
    }

    protected fun expectBindingOrToken(vararg tokens: Token) {
        if (token !is Token.Term && token !in tokens) {
            expectedBindingOrToken(*tokens)
        }
    }

    protected fun expectPatternElementOrBindingOrToken(vararg tokens: Token) {
        if (token is Token.Syntax && token !in tokens) {
            expectedPatternElementOrBindingOrToken(*tokens)
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

    protected fun expectedPatternElement(): Nothing {
        bail("Unexpected $token, expected pattern element")
    }

    protected fun expectedBinding(): Nothing {
        bail("Unexpected $token, expected pattern element")
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

    protected fun expectedPatternElementOrBindingOrToken(vararg tokens: Token): Nothing {
        val msg = when (tokens.size) {
            0 -> "Unexpected $token, expected pattern element or binding"
            1 -> "Unexpected $token, expected pattern element, binding or ${tokens.first().syntax}"
            else -> "Unexpected $token, expected pattern element, binding or any of ${tokens.joinToString { it.syntax }}"
        }
        bail(msg)
    }

    protected fun bail(message: String = "Internal compiler error"): Nothing {
        throw StructuralError(message = "Failed at index ${lexer.position() - token.syntax.length}", stacktrace = lexer.stacktrace(message))
    }

}
