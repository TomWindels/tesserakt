package tesserakt.sparql.formatting

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Token
import tesserakt.sparql.runtime.types.*

abstract class ASTrWriter<RT> {

    object Default: ASTrWriter<String>() {

        private val state = State()

        data class State(
            val content: StringBuilder = StringBuilder(),
            var indent: Int = 0
        ) {
            fun clear() {
                content.clear()
                indent = 0
            }
        }

        override fun write(ast: ASTr): String {
            process(ast)
            return state.content.toString()
                .also { state.clear() }
        }

        override fun newline() {
            state.content.append('\n')
            state.content.append("\t".repeat(state.indent))
        }

        override fun add(token: Token) {
            state.content.append(token.stringified())
            state.content.append(' ')
        }

        override fun indent() {
            state.indent += 1
        }

        override fun unindent() {
            state.indent -= 1
        }

        /* helpers */

        private fun Token.stringified(): String = when (this) {
            is Token.Binding -> "?$name"
            is Token.NumericLiteral -> value.toString()
            is Token.PrefixedTerm -> "$namespace:$value"
            is Token.StringLiteral -> value
            is Token.Term -> "<$value>"
            is Token.Syntax -> syntax
            Token.EOF -> "" // not expected to happen
        }

    }

    abstract fun write(ast: ASTr): RT

    /* internal API for writing the ASTr */

    protected abstract fun newline()

    protected abstract fun add(token: Token)

    protected abstract fun indent()

    protected abstract fun unindent()

    /* shared implementation */

    protected fun process(symbol: ASTr) {
        when (symbol) {
            is PatternASTr.GeneratedBinding ->
                add(Token.PrefixedTerm(namespace = "_", value = "b${symbol.id}"))

            is PatternASTr.RegularBinding ->
                add(Token.Binding(symbol.name))

            is PatternASTr.Exact -> when (symbol.term) {
                is Triple.BlankTerm -> throw UnsupportedOperationException()
                is Triple.Literal<*> -> when (symbol.term.literal) {
                    is String -> add(Token.StringLiteral(symbol.term.literal))
                    is Number -> add(Token.NumericLiteral(symbol.term.literal))
                    else -> throw UnsupportedOperationException()
                }
                is Triple.NamedTerm -> add(Token.Term(symbol.term.value))
            }

            is PatternASTr.Alts -> {
                add(Token.Syntax.RoundBracketStart)
                for (i in 0 ..< symbol.allowed.size - 1) {
                    process(symbol.allowed[i])
                    add(Token.Syntax.PredicateOr)
                }
                process(symbol.allowed.last())
                add(Token.Syntax.RoundBracketEnd)
            }

            is PatternASTr.Inverse -> {
                add(Token.Syntax.ExclamationMark)
                process(symbol.predicate)
            }

            is PatternASTr.OneOrMoreBound -> {
                process(symbol.predicate)
                add(Token.Syntax.OpPlus)
            }

            is PatternASTr.OneOrMoreFixed -> {
                process(symbol.predicate)
                add(Token.Syntax.OpPlus)
            }

            is PatternASTr.ZeroOrMoreBound -> {
                process(symbol.predicate)
                add(Token.Syntax.Asterisk)
            }

            is PatternASTr.ZeroOrMoreFixed -> {
                process(symbol.predicate)
                add(Token.Syntax.Asterisk)
            }

            is PatternASTr -> {
                process(symbol.s)
                process(symbol.p)
                process(symbol.o)
                add(Token.Syntax.Period)
            }

            is PatternsASTr -> {
                symbol.forEach { pattern ->
                    newline()
                    process(pattern)
                }
            }

            is UnionASTr -> {
                newline()
                for (i in 0 ..< symbol.size - 1) {
                    add(Token.Syntax.CurlyBracketStart)
                    indent()
                    process(symbol[i])
                    unindent()
                    newline()
                    add(Token.Syntax.CurlyBracketEnd)
                    add(Token.Syntax.Union)
                }
                add(Token.Syntax.CurlyBracketStart)
                indent()
                process(symbol.last())
                unindent()
                newline()
                add(Token.Syntax.CurlyBracketEnd)
            }

            is QueryASTr.QueryBodyASTr -> {
                process(symbol.patterns)
                symbol.optional.forEach { optional ->
                    newline()
                    add(Token.Syntax.Optional)
                    add(Token.Syntax.CurlyBracketStart)
                    indent()
                    process(optional)
                    unindent()
                    newline()
                    add(Token.Syntax.CurlyBracketEnd)
                }
                symbol.unions.forEach { union ->
                    process(union)
                }
            }

            is SelectQueryASTr -> {
                add(Token.Syntax.Select)
                symbol.output.forEach { name ->
                    add(Token.Binding(name))
                }
                add(Token.Syntax.Where)
                add(Token.Syntax.CurlyBracketStart)
                indent()
                process(symbol.body)
                unindent()
                newline()
                add(Token.Syntax.CurlyBracketEnd)
            }
        }

    }

}
