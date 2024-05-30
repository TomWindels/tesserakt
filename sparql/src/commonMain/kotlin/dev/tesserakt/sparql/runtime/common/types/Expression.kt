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

            is ExpressionAST.Filter -> object : Expression<Boolean> {
                val lhs = convert(ast.lhs)
                val rhs = convert(ast.rhs)
                override fun execute(context: Context): Boolean {
                    val a = lhs.execute(context) as Comparable<Any>
                    val b = rhs.execute(context) as Comparable<Any>
                    return when (ast.operand) {
                        ExpressionAST.Filter.Operand.GREATER_THAN -> a > b
                        ExpressionAST.Filter.Operand.GREATER_THAN_OR_EQ -> a >= b
                        ExpressionAST.Filter.Operand.LESS_THAN -> a < b
                        ExpressionAST.Filter.Operand.LESS_THAN_OR_EQ -> a <= b
                        ExpressionAST.Filter.Operand.EQUAL -> a == b
                    }
                }
            }

            is ExpressionAST.FuncCall -> object : Expression<Any> {
                val input = ast.input.name
                val distinct = ast.distinct
                val expr = when (ast.type) {
                    ExpressionAST.FuncCall.Type.COUNT -> Collection<Any>::count
                    ExpressionAST.FuncCall.Type.SUM -> Collection<Double>::sum
                    ExpressionAST.FuncCall.Type.MIN -> Collection<Comparable<Any>>::min
                    ExpressionAST.FuncCall.Type.MAX -> Collection<Comparable<Any>>::max
                    ExpressionAST.FuncCall.Type.AVG -> Collection<Double>::average
                    ExpressionAST.FuncCall.Type.GROUP_CONCAT -> TODO()
                    ExpressionAST.FuncCall.Type.SAMPLE -> TODO()
                } as (Any) -> Any

                override fun execute(context: Context): Any {
                    // getting all values matching the provided binding values name
                    require(context is Context.Singular)
                    // FIXME: distinct
                    return context.all.map { it[input]!! }.let(expr)
                }
            }

            is ExpressionAST.LiteralValue -> Expression { ast.value }

            is ExpressionAST.MathOp.Inverse -> object : Expression<Number> {
                val expr = convert(ast.value)
                override fun execute(context: Context): Number {
                    return 1 / (expr.execute(context) as Number).toDouble()
                }
            }

            is ExpressionAST.MathOp.Multiplication -> object: Expression<Double> {
                val expr = ast.operands.map { convert(it) as Expression<Number> }
                override fun execute(context: Context) = expr.fold(1.0) { acc, expression -> acc * expression.execute(context).toDouble() }
            }

            is ExpressionAST.MathOp.Sum -> object: Expression<Double> {
                val expr = ast.operands.map { convert(it) as Expression<Number> }
                override fun execute(context: Context) = expr.sumOf { it.execute(context).toDouble() }
            }

            is ExpressionAST.MathOp.Negative -> object: Expression<Double> {
                val expr = convert(ast.value) as Expression<Number>
                override fun execute(context: Context) = - expr.execute(context).toDouble()
            }

        }

    }

}
