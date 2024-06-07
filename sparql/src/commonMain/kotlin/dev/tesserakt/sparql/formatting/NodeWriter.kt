package dev.tesserakt.sparql.formatting

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.runtime.common.types.Expression
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.*
import dev.tesserakt.sparql.runtime.node.IncrementalNode
import dev.tesserakt.sparql.runtime.node.Node

abstract class NodeWriter<RT> {

    object Default: NodeWriter<String>() {

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

        override fun write(node: Node): String {
            process(node)
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

    abstract fun write(node: Node): RT

    /* internal API for writing the Node */

    protected abstract fun newline()

    protected abstract fun add(token: Token)

    protected abstract fun indent()

    protected abstract fun unindent()

    /* shared implementation */

    protected fun process(symbol: Node) {
        when (symbol) {
            is Pattern.GeneratedBinding ->
                add(Token.PrefixedTerm(namespace = "_", value = "b${symbol.id}"))

            is Pattern.RegularBinding ->
                add(Token.Binding(symbol.name))

            is Pattern.Exact -> when (symbol.term) {
                is Quad.BlankTerm -> throw UnsupportedOperationException()
                is Quad.Literal<*> -> {
                    (symbol.term.literal as? String)?.let { add(Token.StringLiteral(it)) }
                    ?: (symbol.term.literal as? Number)?.let { add(Token.NumericLiteral(it)) }
                    ?: throw UnsupportedOperationException()
                }
                is Quad.NamedTerm -> add(Token.Term(symbol.term.value))
            }

            is Pattern.Alts -> {
                add(Token.Symbol.RoundBracketStart)
                for (i in 0 ..< symbol.allowed.size - 1) {
                    process(symbol.allowed[i])
                    add(Token.Symbol.PredicateOr)
                }
                process(symbol.allowed.last())
                add(Token.Symbol.RoundBracketEnd)
            }

            is Pattern.Inverse -> {
                add(Token.Symbol.ExclamationMark)
                process(symbol.predicate)
            }

            is Pattern.OneOrMore -> {
                process(symbol.element)
                add(Token.Symbol.OpPlus)
            }

            is Pattern.ZeroOrMore -> {
                process(symbol.element)
                add(Token.Symbol.Asterisk)
            }

            is Pattern -> {
                process(symbol.s)
                process(symbol.p)
                process(symbol.o)
                add(Token.Symbol.Period)
            }

            is Patterns -> {
                symbol.forEach { pattern ->
                    newline()
                    process(pattern)
                }
            }

            is SelectQuerySegment -> {
                newline()
                process(symbol.query)
            }

            is StatementsSegment -> {
                process(symbol.statements)
            }

            is Optional -> {
                when (symbol.segment) {
                    is SelectQuerySegment -> { process(symbol.segment) }
                    is StatementsSegment -> { process(symbol.segment) }
                }
            }

            is Union -> {
                newline()
                for (i in 0 ..< symbol.size - 1) {
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    when (val segment = symbol[i]) {
                        is SelectQuerySegment -> { process(segment) }
                        is StatementsSegment -> { process(segment) }
                    }
                    unindent()
                    newline()
                    add(Token.Symbol.CurlyBracketEnd)
                    add(Token.Keyword.Union)
                }
                add(Token.Symbol.CurlyBracketStart)
                indent()
                when (val segment = symbol.last()) {
                    is SelectQuerySegment -> { process(segment) }
                    is StatementsSegment -> { process(segment) }
                }
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
            }

            is Query.QueryBody -> {
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

            is SelectQuery -> {
                add(Token.Keyword.Select)
                symbol.output.forEach { output ->
                    when (output) {
                        is SelectQuery.BindingOutput ->
                            add(Token.Binding(output.name))
                        is SelectQuery.ExpressionOutput -> {
                            add(Token.Symbol.RoundBracketStart)
                            process(output.expression)
                            add(Token.Keyword.As)
                            add(Token.Binding(output.name))
                            add(Token.Symbol.RoundBracketEnd)
                        }
                    }
                }
                add(Token.Keyword.Where)
                add(Token.Symbol.CurlyBracketStart)
                indent()
                process(symbol.body)
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
                if (symbol.grouping != null) {
                    newline()
                    add(Token.Keyword.Group)
                    add(Token.Keyword.By)
                    process(symbol.grouping)
                }
                if (symbol.groupingFilter != null) {
                    newline()
                    add(Token.Keyword.Having)
                    process(symbol.groupingFilter)
                }
                if (symbol.ordering != null) {
                    newline()
                    add(Token.Keyword.Order)
                    add(Token.Keyword.By)
                    process(symbol.ordering)
                }
            }

            is Expression<*> -> {
                add(Token.StringLiteral("EXPR"))
            }

            is IncrementalNode -> {
                add(Token.StringLiteral("??NODE:${symbol::class.simpleName}"))
            }
        }

    }

}
