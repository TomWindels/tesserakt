package dev.tesserakt.sparql.formatting

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.*

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

        override fun write(element: RuntimeElement): String {
            process(element)
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

    abstract fun write(element: RuntimeElement): RT

    /* internal API for writing the Node */

    protected abstract fun newline()

    protected abstract fun add(token: Token)

    protected abstract fun indent()

    protected abstract fun unindent()

    /* shared implementation */

    protected fun process(element: RuntimeElement) {
        when (element) {
            is Pattern.GeneratedBinding ->
                add(Token.PrefixedTerm(namespace = "_", value = "b${element.id}"))

            is Pattern.RegularBinding ->
                add(Token.Binding(element.name))

            is Pattern.Exact -> when (element.term) {
                is Quad.BlankTerm -> throw UnsupportedOperationException()
                is Quad.Literal -> add(Token.StringLiteral(element.term.value))
                is Quad.NamedTerm -> add(Token.Term(element.term.value))
            }

            is Pattern.Alts -> {
                add(Token.Symbol.RoundBracketStart)
                repeat(element.allowed.size - 1) { i ->
                    add(Token.Symbol.RoundBracketStart)
                    process(element.allowed[i])
                    add(Token.Symbol.RoundBracketEnd)
                    add(Token.Symbol.PredicateOr)
                }
                add(Token.Symbol.RoundBracketStart)
                process(element.allowed.last())
                add(Token.Symbol.RoundBracketEnd)
                add(Token.Symbol.RoundBracketEnd)

            }

            is Pattern.Negated -> {
                add(Token.Symbol.ExclamationMark)
                when (element.term) {
                    is Quad.BlankTerm -> throw UnsupportedOperationException()
                    is Quad.Literal -> add(Token.StringLiteral(element.term.value))
                    is Quad.NamedTerm -> add(Token.Term(element.term.value))
                }
            }

            is Pattern.Sequence -> {
                add(Token.Symbol.RoundBracketStart)
                repeat(element.chain.size - 1) { i ->
                    add(Token.Symbol.RoundBracketStart)
                    process(element.chain[i])
                    add(Token.Symbol.RoundBracketEnd)
                    add(Token.Symbol.ForwardSlash)
                }
                add(Token.Symbol.RoundBracketStart)
                process(element.chain.last())
                add(Token.Symbol.RoundBracketEnd)
                add(Token.Symbol.RoundBracketEnd)
            }

            is Pattern.SimpleAlts -> {
                add(Token.Symbol.RoundBracketStart)
                repeat(element.allowed.size - 1) { i ->
                    add(Token.Symbol.RoundBracketStart)
                    process(element.allowed[i])
                    add(Token.Symbol.RoundBracketEnd)
                    add(Token.Symbol.PredicateOr)
                }
                add(Token.Symbol.RoundBracketStart)
                process(element.allowed.last())
                add(Token.Symbol.RoundBracketEnd)
                add(Token.Symbol.RoundBracketEnd)
            }

            is Pattern.UnboundSequence -> {
                add(Token.Symbol.RoundBracketStart)
                repeat(element.chain.size - 1) { i ->
                    add(Token.Symbol.RoundBracketStart)
                    process(element.chain[i])
                    add(Token.Symbol.RoundBracketEnd)
                    add(Token.Symbol.ForwardSlash)
                }
                add(Token.Symbol.RoundBracketStart)
                process(element.chain.last())
                add(Token.Symbol.RoundBracketEnd)
                add(Token.Symbol.RoundBracketEnd)
            }

            is Pattern.OneOrMore -> {
                process(element.element)
                add(Token.Symbol.OpPlus)
            }

            is Pattern.ZeroOrMore -> {
                process(element.element)
                add(Token.Symbol.Asterisk)
            }

            is Pattern -> {
                process(element.s)
                process(element.p)
                process(element.o)
                add(Token.Symbol.Period)
            }

            is Patterns -> {
                element.forEach { pattern ->
                    newline()
                    process(pattern)
                }
            }

            is SelectQuerySegment -> {
                newline()
                process(element.query)
            }

            is StatementsSegment -> {
                process(element.statements)
            }

            is Optional -> {
                when (element.segment) {
                    is SelectQuerySegment -> { process(element.segment) }
                    is StatementsSegment -> { process(element.segment) }
                }
            }

            is Union -> {
                newline()
                for (i in 0 ..< element.size - 1) {
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    when (val segment = element[i]) {
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
                when (val segment = element.last()) {
                    is SelectQuerySegment -> { process(segment) }
                    is StatementsSegment -> { process(segment) }
                }
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
            }

            is Query.QueryBody -> {
                process(element.patterns)
                element.optional.forEach { optional ->
                    newline()
                    add(Token.Keyword.Optional)
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    process(optional)
                    unindent()
                    newline()
                    add(Token.Symbol.CurlyBracketEnd)
                }
                element.unions.forEach { union ->
                    process(union)
                }
            }

            is SelectQuery -> {
                add(Token.Keyword.Select)
                element.output.forEach { output ->
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
                process(element.body)
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
                if (element.grouping != null) {
                    newline()
                    add(Token.Keyword.Group)
                    add(Token.Keyword.By)
                    process(element.grouping)
                }
                if (element.groupingFilter != null) {
                    newline()
                    add(Token.Keyword.Having)
                    process(element.groupingFilter)
                }
                if (element.ordering != null) {
                    newline()
                    add(Token.Keyword.Order)
                    add(Token.Keyword.By)
                    process(element.ordering)
                }
            }

            is Expression -> {
                add(Token.StringLiteral("EXPR"))
            }

        }

    }

}
