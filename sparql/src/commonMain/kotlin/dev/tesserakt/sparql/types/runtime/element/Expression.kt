package dev.tesserakt.sparql.types.runtime.element

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.types.ast.ExpressionAST
import dev.tesserakt.sparql.types.ast.FilterAST

fun interface Expression: RuntimeElement {

    fun execute(context: Context): Quad.Term?

    companion object {

        fun convert(ast: FilterAST.Regex): Expression {
            return object: Expression {
                // TODO: consider `FilterAST::Regex::mode`
                private val regex = Regex(ast.regex)
                override fun execute(context: Context): Quad.Term {
                    require(context is Context.Singular)
                    val term = context.current[ast.input.name] as? Quad.Literal
                        ?: return false.asLiteralTerm()
                    if (term.type != XSD.string) {
                        return false.asLiteralTerm()
                    }
                    return regex.matches(term.value).asLiteralTerm()
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun convert(ast: ExpressionAST): Expression = when (ast) {
            is ExpressionAST.BindingValues -> Expression {
                require(it is Context.Singular)
                it.current[ast.name]
            }

            is ExpressionAST.Conditional -> object : Expression {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context): Quad.Term {
                    val a = lhs.execute(context) as Comparable<Any>
                    val b = rhs.execute(context) as Comparable<Any>
                    return when (ast.operand) {
                        ExpressionAST.Conditional.Operand.GREATER_THAN -> a > b
                        ExpressionAST.Conditional.Operand.GREATER_THAN_OR_EQ -> a >= b
                        ExpressionAST.Conditional.Operand.LESS_THAN -> a < b
                        ExpressionAST.Conditional.Operand.LESS_THAN_OR_EQ -> a <= b
                        ExpressionAST.Conditional.Operand.EQUAL -> a == b
                    }.asLiteralTerm()
                }
            }

            is ExpressionAST.BindingAggregate -> object : Expression {
                val input = ast.input.name
                val distinct = ast.distinct
                val expr = when (ast.type) {
                    ExpressionAST.BindingAggregate.Type.COUNT -> Collection<Any>::count
                    ExpressionAST.BindingAggregate.Type.SUM -> Collection<Double>::sum
                    ExpressionAST.BindingAggregate.Type.MIN -> Collection<Comparable<Any>>::min
                    ExpressionAST.BindingAggregate.Type.MAX -> Collection<Comparable<Any>>::max
                    ExpressionAST.BindingAggregate.Type.AVG -> Collection<Double>::average
                    ExpressionAST.BindingAggregate.Type.GROUP_CONCAT -> TODO()
                    ExpressionAST.BindingAggregate.Type.SAMPLE -> TODO()
                } as (Any) -> Any

                override fun execute(context: Context): Quad.Term {
                    // getting all values matching the provided binding values name
                    require(context is Context.Singular)
                    // FIXME: distinct
                    return context.all.map { it[input]!! }.let(expr).asLiteralTerm()
                }
            }

            is ExpressionAST.NumericLiteralValue -> Expression { ast.value.asLiteralTerm() }

            is ExpressionAST.StringLiteralValue -> Expression { ast.value.asLiteralTerm() }

            is ExpressionAST.MathOp.Mul -> object: Expression {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context) =
                    (lhs.execute(context)!!.numericalValue * rhs.execute(context)!!.numericalValue).asLiteralTerm()
            }

            is ExpressionAST.MathOp.Sum -> object: Expression {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context) =
                    (lhs.execute(context)!!.numericalValue + rhs.execute(context)!!.numericalValue).asLiteralTerm()
            }

            is ExpressionAST.MathOp.Diff -> object: Expression {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context) =
                    (lhs.execute(context)!!.numericalValue - rhs.execute(context)!!.numericalValue).asLiteralTerm()
            }

            is ExpressionAST.MathOp.Div -> object: Expression {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context) =
                    (lhs.execute(context)!!.numericalValue / rhs.execute(context)!!.numericalValue).asLiteralTerm()
            }

            is ExpressionAST.MathOp.Negative -> object: Expression {
                val expr = convert(ast.value)
                override fun execute(context: Context) =
                    (- expr.execute(context)!!.numericalValue).asLiteralTerm()
            }

            is ExpressionAST.FuncCall -> object: Expression {
                val func = funcs[ast.name]!!
                val args = ast.args.map { arg -> convert(arg) }
                override fun execute(context: Context) = func(args.map { it.execute(context)!! })
            }

        }

    }

}

private val funcs = mapOf<String, (List<Quad.Term>) -> Quad.Term>(
    "strlen" to {
        (it.single() as Quad.Literal).value.length.asLiteralTerm()
    },
    "concat" to { args ->
        @Suppress("UNCHECKED_CAST")
        (args as List<Quad.Literal>).joinToString { it.value }.asLiteralTerm()
    },
)

@Suppress("UNCHECKED_CAST")
private val Quad.Term.numericalValue: Double
    get() = (this as Quad.Literal).value.toDouble()
