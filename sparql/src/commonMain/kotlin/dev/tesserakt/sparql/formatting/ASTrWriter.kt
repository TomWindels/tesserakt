package dev.tesserakt.sparql.formatting

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.runtime.types.*

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
            is Token.BlankTerm -> "_:$value"
            is Token.StringLiteral -> value
            is Token.Term -> "<$value>"
            is Token.Symbol -> syntax
            is Token.Keyword -> syntax
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
                is Quad.BlankTerm -> throw UnsupportedOperationException()
                is Quad.Literal<*> -> {
                    (symbol.term.literal as? String)?.let { add(Token.StringLiteral(it)) }
                    ?: (symbol.term.literal as? Number)?.let { add(Token.NumericLiteral(it)) }
                    ?: throw UnsupportedOperationException()
                }
                is Quad.NamedTerm -> add(Token.Term(symbol.term.value))
            }

            is PatternASTr.Alts -> {
                add(Token.Symbol.RoundBracketStart)
                for (i in 0 ..< symbol.allowed.size - 1) {
                    process(symbol.allowed[i])
                    add(Token.Symbol.PredicateOr)
                }
                process(symbol.allowed.last())
                add(Token.Symbol.RoundBracketEnd)
            }

            is PatternASTr.Inverse -> {
                add(Token.Symbol.ExclamationMark)
                process(symbol.predicate)
            }

            is PatternASTr.OneOrMoreBound -> {
                process(symbol.predicate)
                add(Token.Symbol.OpPlus)
            }

            is PatternASTr.OneOrMoreFixed -> {
                process(symbol.predicate)
                add(Token.Symbol.OpPlus)
            }

            is PatternASTr.ZeroOrMoreBound -> {
                process(symbol.predicate)
                add(Token.Symbol.Asterisk)
            }

            is PatternASTr.ZeroOrMoreFixed -> {
                process(symbol.predicate)
                add(Token.Symbol.Asterisk)
            }

            is PatternASTr -> {
                process(symbol.s)
                process(symbol.p)
                process(symbol.o)
                add(Token.Symbol.Period)
            }

            is PatternsASTr -> {
                symbol.forEach { pattern ->
                    newline()
                    process(pattern)
                }
            }

            is SegmentASTr.SelectQuery -> {
                newline()
                process(symbol.query)
            }

            is SegmentASTr.Statements -> {
                process(symbol.statements)
            }

            is OptionalASTr -> {
                when (symbol.segment) {
                    is SegmentASTr.SelectQuery -> { process(symbol.segment) }
                    is SegmentASTr.Statements -> { process(symbol.segment) }
                }
            }

            is UnionASTr -> {
                newline()
                for (i in 0 ..< symbol.size - 1) {
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    when (val segment = symbol[i]) {
                        is SegmentASTr.SelectQuery -> { process(segment) }
                        is SegmentASTr.Statements -> { process(segment) }
                    }
                    unindent()
                    newline()
                    add(Token.Symbol.CurlyBracketEnd)
                    add(Token.Keyword.Union)
                }
                add(Token.Symbol.CurlyBracketStart)
                indent()
                when (val segment = symbol.last()) {
                    is SegmentASTr.SelectQuery -> { process(segment) }
                    is SegmentASTr.Statements -> { process(segment) }
                }
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
            }

            is QueryASTr.QueryBodyASTr -> {
                process(symbol.patterns)
                symbol.optional.forEach { optional ->
                    newline()
                    add(Token.Keyword.Optional)
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    process(optional)
                    unindent()
                    newline()
                    add(Token.Symbol.CurlyBracketEnd)
                }
                symbol.unions.forEach { union ->
                    process(union)
                }
            }

            is SelectQueryASTr -> {
                add(Token.Keyword.Select)
                symbol.output.forEach { name ->
                    add(Token.Binding(name))
                }
                add(Token.Keyword.Where)
                add(Token.Symbol.CurlyBracketStart)
                indent()
                process(symbol.body)
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
            }
        }

    }

}
