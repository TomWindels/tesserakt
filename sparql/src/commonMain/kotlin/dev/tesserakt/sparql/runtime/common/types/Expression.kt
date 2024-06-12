package dev.tesserakt.sparql.runtime.common.types

import dev.tesserakt.sparql.compiler.ast.ExpressionAST
import dev.tesserakt.sparql.runtime.node.CommonNode

fun interface Expression<T> : CommonNode {

    fun execute(context: Context): T

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun convert(ast: ExpressionAST): Expression<*> = when (ast) {
            is ExpressionAST.BindingValues -> Expression {
                require(it is Context.Singular)
                it.current[ast.name]
            }

            is ExpressionAST.Conditional -> object : Expression<Boolean> {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context): Boolean {
                    val a = lhs.execute(context) as Comparable<Any>
                    val b = rhs.execute(context) as Comparable<Any>
                    return when (ast.operand) {
                        ExpressionAST.Conditional.Operand.GREATER_THAN -> a > b
                        ExpressionAST.Conditional.Operand.GREATER_THAN_OR_EQ -> a >= b
                        ExpressionAST.Conditional.Operand.LESS_THAN -> a < b
                        ExpressionAST.Conditional.Operand.LESS_THAN_OR_EQ -> a <= b
                        ExpressionAST.Conditional.Operand.EQUAL -> a == b
                    }
                }
            }

            is ExpressionAST.BindingAggregate -> object : Expression<Any> {
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

                override fun execute(context: Context): Any {
                    // getting all values matching the provided binding values name
                    require(context is Context.Singular)
                    // FIXME: distinct
                    return context.all.map { it[input]!! }.let(expr)
                }
            }

            is ExpressionAST.NumericLiteralValue -> Expression { ast.value }

            is ExpressionAST.StringLiteralValue -> Expression { ast.value }

            is ExpressionAST.MathOp.Mul -> object: Expression<Double> {
                val lhs = convert(ast.lhs) as Expression<Number>
                val rhs = convert(ast.rhs) as Expression<Number>
                override fun execute(context: Context) = lhs.execute(context).toDouble() * rhs.execute(context).toDouble()
            }

            is ExpressionAST.MathOp.Sum -> object: Expression<Double> {
                val lhs = convert(ast.lhs) as Expression<Number>
                val rhs = convert(ast.rhs) as Expression<Number>
                override fun execute(context: Context) = lhs.execute(context).toDouble() + rhs.execute(context).toDouble()
            }

            is ExpressionAST.MathOp.Diff -> object: Expression<Double> {
                val lhs = convert(ast.lhs) as Expression<Number>
                val rhs = convert(ast.rhs) as Expression<Number>
                override fun execute(context: Context) = lhs.execute(context).toDouble() - rhs.execute(context).toDouble()
            }

            is ExpressionAST.MathOp.Div -> object: Expression<Double> {
                val lhs = convert(ast.lhs) as Expression<Number>
                val rhs = convert(ast.rhs) as Expression<Number>
                override fun execute(context: Context) = lhs.execute(context).toDouble() / rhs.execute(context).toDouble()
            }

            is ExpressionAST.MathOp.Negative -> object: Expression<Double> {
                val expr = convert(ast.value) as Expression<Number>
                override fun execute(context: Context) = - expr.execute(context).toDouble()
            }

            is ExpressionAST.FuncCall -> object: Expression<Any> {
                val func = funcs[ast.name]!!
                val args = ast.args.map { arg -> convert(arg) }
                override fun execute(context: Context): Any = func(args.map { it.execute(context)!! })
            }

        }

    }

}

private val funcs = mapOf<String, (List<Any>) -> Any>(
    "strlen" to { (it.single() as String).length },
    "concat" to { it.joinToString() },
)
