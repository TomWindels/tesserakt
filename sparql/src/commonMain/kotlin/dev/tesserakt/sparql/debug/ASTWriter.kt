package dev.tesserakt.sparql.debug

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.*

class ASTWriter(private val indentStyle: String = "  ") {

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

    fun write(ast: RuntimeElement): String {
        process(ast)
        return state.content.toString().trim()
            .also { state.clear() }
    }

    private fun writeLine(line: String) {
        state.content.append('\n')
        state.content.append(indentStyle.repeat(state.indent))
        state.content.append(line)
    }

    private fun append(text: String) {
        state.content.append(text)
    }

    private fun addIndent() {
        state.indent += 1
    }

    private fun removeIndent() {
        state.indent -= 1
    }

    private inline fun indented(block: () -> Unit) {
        addIndent()
        block()
        removeIndent()
    }

    private fun process(symbol: RuntimeElement): Unit = when (symbol) {
        is Aggregation -> {
            writeLine("aggregation")
            indented {
                writeLine("target: ${symbol.target.stringified()}")
                writeLine("expression: ")
                process(symbol.expression)
            }
        }

        is Expression.BindingValues -> {
            writeLine("binding")
            indented { writeLine("target: ${symbol.name}") }
        }

        is Expression.Conditional -> {
            writeLine("conditional")
            indented {
                writeLine("operand: ${symbol.operand}")
                writeLine("lhs: ")
                process(symbol.lhs)
                writeLine("rhs: ")
                process(symbol.rhs)
            }
        }

        is Expression.BindingAggregate -> {
            writeLine("func call")
            indented {
                writeLine("type: ${symbol.type}")
                writeLine("distinct: ${symbol.distinct}")
                writeLine("input: ${symbol.input.name}")
            }
        }

        is Expression.NumericLiteralValue -> {
            writeLine("numeric literal")
            indented {
                writeLine("value: ${symbol.value} [${symbol.value::class.simpleName}]")
            }
        }

        is Expression.StringLiteralValue -> {
            writeLine("string literal")
            indented {
                writeLine("value: ${symbol.value} [${symbol.value::class.simpleName}]")
            }
        }

        is Expression.MathOp.Sum -> {
            writeLine("sum")
            indented {
                writeLine("lhs")
                process(symbol.lhs)
                writeLine("rhs")
                process(symbol.rhs)
            }
        }

        is Expression.MathOp.Diff -> {
            writeLine("difference")
            indented {
                writeLine("lhs")
                process(symbol.lhs)
                writeLine("rhs")
                process(symbol.rhs)
            }
        }

        is Expression.MathOp.Mul -> {
            writeLine("multiplication")
            indented {
                writeLine("lhs")
                process(symbol.lhs)
                writeLine("rhs")
                process(symbol.rhs)
            }
        }

        is Expression.MathOp.Div -> {
            writeLine("division")
            indented {
                writeLine("lhs")
                process(symbol.lhs)
                writeLine("rhs")
                process(symbol.rhs)
            }
        }

        is Expression.MathOp.Negative -> {
            writeLine("negative")
            indented {
                writeLine("input: ")
                process(symbol.value)
            }
        }

        is Pattern.Binding -> {
            append("binding ${symbol.name}")
        }

        is Pattern.Exact -> {
            append("exact ${symbol.term}")
        }

        is Optional -> {
            append("optional ")
            process(symbol.segment)
        }

        is Pattern -> {
            writeLine("pattern")
            indented {
                writeLine("subject: ")
                process(symbol.s)
                writeLine("predicate: ")
                process(symbol.p)
                writeLine("object: ")
                process(symbol.o)
            }
        }

        is Patterns -> {
            indented {
                symbol.forEachIndexed { i, pattern ->
                    writeLine("$i: ")
                    process(pattern)
                }
            }
        }

        is Pattern.Alts -> {
            append("alt paths")
            indented {
                symbol.allowed.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is Pattern.SimpleAlts -> {
            append("alt paths (simple)")
            indented {
                symbol.allowed.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is Pattern.Sequence -> {
            append("path sequence")
            indented {
                symbol.chain.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is Pattern.UnboundSequence -> {
            append("path sequence (unbound)")
            indented {
                symbol.chain.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is Pattern.Negated -> {
            append("negated path ")
            process(symbol.terms)
        }

        is Pattern.OneOrMore -> {
            append("one or more ")
            process(symbol.element)
        }

        is Pattern.ZeroOrMore -> {
            append("zero or more ")
            process(symbol.element)
        }

        is SelectQuery -> {
            append("select query")
            indented {
                writeLine("outputs")
                indented {
                    symbol.output?.forEach {
                        writeLine("name: ${it.name}")
                        when (it) {
                            is SelectQuery.BindingOutput -> {
                                writeLine("value: directly from query")
                            }
                            is SelectQuery.ExpressionOutput -> {
                                writeLine("value: ")
                                process(it.expression)
                            }
                        }
                    } ?: writeLine("all (*): ${symbol.bindings.joinToString()}")
                }
                writeLine("body ")
                process(symbol.body)
                symbol.grouping?.let {
                    writeLine("grouping ")
                    process(it)
                }
                symbol.groupingFilter?.let {
                    writeLine("grouping filter ")
                    process(it)
                }
                symbol.ordering?.let {
                    writeLine("ordering modifier")
                    process(it)
                }
            }
        }

        is Query.QueryBody -> {
            indented {
                process(symbol.patterns)
                indented {
                    if (symbol.filters.isNotEmpty()) {
                        writeLine("filters")
                        symbol.filters.forEachIndexed { index, filter ->
                            writeLine("$index: ")
                            process(filter)
                        }
                    }
                    if (symbol.bindingStatements.isNotEmpty()) {
                        writeLine("binding statements")
                        symbol.bindingStatements.forEachIndexed { index, statement ->
                            writeLine("target: ${statement.target}")
                            writeLine("expression: ")
                            indented {
                                process(statement.expression)
                            }
                        }
                    }
                    if (symbol.unions.isNotEmpty()) {
                        writeLine("unions")
                        symbol.unions.forEachIndexed { index, union ->
                            writeLine("$index: ")
                            process(union)
                        }
                    }
                    if (symbol.optional.isNotEmpty()) {
                        writeLine("optionals")
                        symbol.optional.forEachIndexed { index, optional ->
                            writeLine("$index: ")
                            process(optional)
                        }
                    }
                }
            }
        }

        is SelectQuerySegment -> {
            append("segment: ")
            process(symbol.query)
        }

        is StatementsSegment -> {
            append("segment: ")
            process(symbol.statements)
        }

        is Union -> {
            append("union")
            symbol.segments.forEachIndexed { index, segment ->
                append("segment $index: ")
                process(segment)
            }
        }

        is Filter.Predicate -> {
            append("expression")
            process(symbol.expression)
        }

        is Filter.Regex -> {
            writeLine("regex")
            indented {
                writeLine("input: ${symbol.input}")
                writeLine("regex: ${symbol.regex}")
                writeLine("mode: ${symbol.mode}")
            }
        }

        is Expression.FuncCall -> {
            TODO()
        }
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
