package dev.tesserakt.sparql.formatting

import dev.tesserakt.sparql.compiler.ast.*
import dev.tesserakt.sparql.compiler.lexer.Token

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

    fun write(ast: ASTNode): String {
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

    private fun process(symbol: ASTNode): Unit = when (symbol) {
        is AggregationAST -> {
            writeLine("aggregation")
            indented {
                writeLine("target: ${symbol.target.stringified()}")
                writeLine("expression: ")
                process(symbol.expression)
            }
        }

        is ExpressionAST.BindingValues -> {
            writeLine("binding")
            indented { writeLine("target: ${symbol.name}") }
        }

        is ExpressionAST.Conditional -> {
            writeLine("conditional")
            indented {
                writeLine("operand: ${symbol.operand}")
                writeLine("lhs: ")
                process(symbol.lhs)
                writeLine("rhs: ")
                process(symbol.rhs)
            }
        }

        is ExpressionAST.FuncCall -> {
            writeLine("func call")
            indented {
                writeLine("type: ${symbol.type}")
                writeLine("distinct: ${symbol.distinct}")
                writeLine("input: ${symbol.input.name}")
            }
        }

        is ExpressionAST.MathOp.Inverse -> {
            writeLine("inverse")
            indented {
                writeLine("input: ")
                process(symbol.value)
            }
        }

        is ExpressionAST.LiteralValue -> {
            writeLine("literal")
            indented {
                writeLine("value: ${symbol.value}")
            }
        }

        is ExpressionAST.MathOp.Multiplication -> {
            writeLine("multiplication")
            indented {
                symbol.operands.forEachIndexed { index, expression ->
                    writeLine("operand $index")
                    process(expression)
                }
            }
        }

        is ExpressionAST.MathOp.Sum -> {
            writeLine("sum")
            indented {
                symbol.operands.forEachIndexed { index, expression ->
                    writeLine("operand $index")
                    process(expression)
                }
            }
        }

        is ExpressionAST.MathOp.Negative -> {
            writeLine("negative")
            indented {
                writeLine("input: ")
                process(symbol.value)
            }
        }

        is PatternAST.BlankObject -> {
            writeLine("blank object")
            indented {
                writeLine("properties")
                symbol.properties.forEachIndexed { index, property ->
                    writeLine("$index: ")
                    indented {
                        writeLine("predicate: ")
                        process(property.p)
                        writeLine("object: ")
                        process(property.o)
                    }
                }
            }
        }

        is PatternAST.Binding -> {
            append("binding ${symbol.name}")
        }

        is PatternAST.Exact -> {
            append("exact ${symbol.term}")
        }

        is OptionalAST -> {
            append("optional ")
            process(symbol.segment)
        }

        is PatternAST -> {
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

        is PatternsAST -> {
            indented {
                symbol.forEachIndexed { i, pattern ->
                    writeLine("$i: ")
                    process(pattern)
                }
            }
        }

        is PatternAST.Alts -> {
            append("alt paths")
            indented {
                symbol.allowed.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is PatternAST.Chain -> {
            append("path chain")
            indented {
                symbol.chain.forEachIndexed { i, predicate ->
                    writeLine("$i: ")
                    process(predicate)
                }
            }
        }

        is PatternAST.Not -> {
            append("negative path ")
            process(symbol.predicate)
        }

        is PatternAST.OneOrMore -> {
            append("one or more ")
            process(symbol.value)
        }

        is PatternAST.ZeroOrMore -> {
            append("zero or more ")
            process(symbol.value)
        }

        is SelectQueryAST -> {
            append("select query")
            indented {
                writeLine("outputs")
                indented {
                    symbol.output.forEach {
                        writeLine("name: ${it.key}")
                        writeLine("value: ")
                        process(it.value)
                    }
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

        is QueryAST.QueryBodyAST -> {
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
                    if (symbol.unions.isNotEmpty()) {
                        writeLine("unions")
                        symbol.unions.forEachIndexed { index, union ->
                            writeLine("$index: ")
                            process(union)
                        }
                    }
                    if (symbol.optionals.isNotEmpty()) {
                        writeLine("optionals")
                        symbol.optionals.forEachIndexed { index, optional ->
                            writeLine("$index: ")
                            process(optional)
                        }
                    }
                }
            }
        }

        is SegmentAST.SelectQuery -> {
            append("segment: ")
            process(symbol.query)
        }

        is SegmentAST.Statements -> {
            append("segment: ")
            process(symbol.statements)
        }

        is UnionAST -> {
            append("union")
            symbol.segments.forEachIndexed { index, segment ->
                append("segment $index: ")
                process(segment)
            }
        }

        is SelectQueryAST.BindingOutputEntry -> {
            append("bound output (from select) ")
            process(symbol.binding)
        }

        is SelectQueryAST.AggregationOutputEntry -> {
            append("aggregated output (from select) ")
            process(symbol.aggregation)
        }

        is FilterAST.Predicate -> {
            append("expression")
            process(symbol.expression)
        }

        is FilterAST.Regex -> {
            writeLine("regex")
            indented {
                writeLine("input: ${symbol.input}")
                writeLine("regex: ${symbol.regex}")
                writeLine("mode: ${symbol.mode}")
            }
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
