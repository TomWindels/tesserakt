package dev.tesserakt.sparql.debug

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.ast.*
import dev.tesserakt.sparql.compiler.lexer.Token

abstract class QueryWriter<RT> {

    object Default: QueryWriter<String>() {

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

        override fun write(element: QueryAtom): String {
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

    abstract fun write(element: QueryAtom): RT

    /* API for writing the Node */

    protected abstract fun newline()

    protected abstract fun add(token: Token)

    protected abstract fun indent()

    protected abstract fun unindent()

    /* shared implementation */

    protected fun process(element: QueryAtom) {
        when (element) {
            is TriplePattern.GeneratedBinding ->
                add(Token.PrefixedTerm(namespace = "_", value = "b${element.id}"))

            is TriplePattern.NamedBinding ->
                add(Token.Binding(element.name))

            is TriplePattern.Exact -> when (element.term) {
                is Quad.BlankTerm -> throw UnsupportedOperationException()
                is Quad.Literal -> add(Token.StringLiteral(element.term.value))
                is Quad.NamedTerm -> add(Token.Term(element.term.value))
            }

            is TriplePattern.Alts -> {
                if (element.allowed.size == 1) {
                    process(element.allowed.single())
                    return
                }
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

            is TriplePattern.SimpleAlts -> {
                if (element.allowed.size == 1) {
                    process(element.allowed.single())
                    return
                }
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

            is TriplePattern.Negated -> {
                add(Token.Symbol.ExclamationMark)
                process(element.terms)
            }

            is TriplePattern.Sequence -> {
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

            is TriplePattern.UnboundSequence -> {
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

            is TriplePattern.OneOrMore -> {
                process(element.element)
                add(Token.Symbol.OpPlus)
            }

            is TriplePattern.ZeroOrMore -> {
                process(element.element)
                add(Token.Symbol.Asterisk)
            }

            is TriplePattern -> {
                process(element.s)
                process(element.p)
                process(element.o)
                add(Token.Symbol.Period)
            }

            is TriplePatternSet -> {
                element.forEach { pattern ->
                    newline()
                    process(pattern)
                }
            }

            is SelectQuerySegment -> {
                newline()
                process(element.query)
            }

            is GraphPatternSegment -> {
                process(element.pattern)
            }

            is Optional -> {
                when (element.segment) {
                    is SelectQuerySegment -> { process(element.segment) }
                    is GraphPatternSegment -> { process(element.segment) }
                }
            }

            is Union -> {
                newline()
                for (i in 0 ..< element.size - 1) {
                    add(Token.Symbol.CurlyBracketStart)
                    indent()
                    when (val segment = element[i]) {
                        is SelectQuerySegment -> { process(segment) }
                        is GraphPatternSegment -> { process(segment) }
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
                    is GraphPatternSegment -> { process(segment) }
                }
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
            }

            is GraphPattern -> {
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

            is CompiledSelectQuery -> {
                add(Token.Keyword.Select)
                element.output?.forEach { output ->
                    when (output) {
                        is CompiledSelectQuery.BindingOutput ->
                            add(Token.Binding(output.name))
                        is CompiledSelectQuery.ExpressionOutput -> {
                            add(Token.Symbol.RoundBracketStart)
                            process(output.expression)
                            add(Token.Keyword.As)
                            add(Token.Binding(output.name))
                            add(Token.Symbol.RoundBracketEnd)
                        }
                    }
                } ?: run {
                    add(Token.Symbol.Asterisk)
                }
                add(Token.Keyword.Where)
                add(Token.Symbol.CurlyBracketStart)
                indent()
                process(element.body)
                unindent()
                newline()
                add(Token.Symbol.CurlyBracketEnd)
                element.grouping?.let { grouping ->
                    newline()
                    add(Token.Keyword.Group)
                    add(Token.Keyword.By)
                    process(grouping)
                }
                element.groupingFilter?.let { groupingFilter ->
                    newline()
                    add(Token.Keyword.Having)
                    process(groupingFilter)
                }
                element.ordering?.let { ordering ->
                    newline()
                    add(Token.Keyword.Order)
                    add(Token.Keyword.By)
                    process(ordering)
                }
            }

            is Expression -> {
                add(Token.StringLiteral("EXPR"))
            }

            is Aggregation -> {
                add(Token.Symbol.RoundBracketStart)
                process(element.expression)
                add(Token.Keyword.As)
                add(element.target.toToken())
                add(Token.Symbol.RoundBracketEnd)
            }

            is Filter.Predicate -> {
                add(Token.Keyword.Filter)
                add(Token.Symbol.RoundBracketStart)
                process(element.expression)
                add(Token.Symbol.RoundBracketEnd)
            }

            is Filter.Regex -> {
                add(Token.Keyword.Filter)
                add(Token.Keyword.Regex)
                add(Token.Symbol.RoundBracketStart)
                add(element.input.toToken())
                add(Token.Symbol.Comma)
                add(Token.StringLiteral(element.regex))
                add(Token.Symbol.Comma)
                add(Token.StringLiteral(element.mode))
                add(Token.Symbol.RoundBracketEnd)
            }
        }

    }

    private fun Binding.toToken() = Token.Binding(name = name)

}
