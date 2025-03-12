package dev.tesserakt.sparql.types

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.types.Expression.Compiled
import kotlin.jvm.JvmInline

sealed interface Expression : QueryAtom {

    /**
     * Generic context-like object, representing either a single binding solution propagating through, or an aggregate
     *  of bindings in queries utilizing `GROUP BY`
     */
    sealed interface Context {

        /**
         * Context object representing a single binding solution propagating through
         */
        data class Singular(
            /** The currently relevant bindings when iterating through individual solutions **/
            val current: Bindings,
            /** All bindings (including `current`) generated, guaranteed to be complete w.r.t. available data **/
            val all: List<Bindings>
        ): Context

        /**
         * Context object representing a collection of binding solutions for a single group currently propagating through
         */
        data class Aggregate(
            /** The currently relevant bindings when iterating through individual solutions **/
            val current: List<Bindings>,
            /** All binding groups (including `current`) generated, guaranteed to be complete w.r.t. available data **/
            val all: List<List<Bindings>>
        ): Context

    }

    fun interface Compiled {

        fun execute(context: Context): Quad.Term?

        companion object {

            fun compile(ast: Filter.Regex): Compiled {
                return object: Compiled {
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
            fun compile(ast: Expression): Compiled = when (ast) {
                is BindingValues -> Compiled {
                    require(it is Context.Singular)
                    it.current[ast.name]
                }

                is Conditional -> object : Compiled {
                    val lhs = compile(ast.lhs)
                    val rhs = compile(ast.rhs)
                    override fun execute(context: Context): Quad.Term {
                        val a = lhs.execute(context) as Comparable<Any>
                        val b = rhs.execute(context) as Comparable<Any>
                        return when (ast.operand) {
                            Conditional.Operand.GREATER_THAN -> a > b
                            Conditional.Operand.GREATER_THAN_OR_EQ -> a >= b
                            Conditional.Operand.LESS_THAN -> a < b
                            Conditional.Operand.LESS_THAN_OR_EQ -> a <= b
                            Conditional.Operand.EQUAL -> a == b
                        }.asLiteralTerm()
                    }
                }

                is BindingAggregate -> object : Compiled {
                    val input = ast.input.name
                    val distinct = ast.distinct
                    val expr = when (ast.type) {
                        BindingAggregate.Type.COUNT -> Collection<Any>::count
                        BindingAggregate.Type.SUM -> Collection<Double>::sum
                        BindingAggregate.Type.MIN -> Collection<Comparable<Any>>::min
                        BindingAggregate.Type.MAX -> Collection<Comparable<Any>>::max
                        BindingAggregate.Type.AVG -> Collection<Double>::average
                        BindingAggregate.Type.GROUP_CONCAT -> TODO()
                        BindingAggregate.Type.SAMPLE -> TODO()
                    } as (Any) -> Any

                    override fun execute(context: Context): Quad.Term {
                        // getting all values matching the provided binding values name
                        require(context is Context.Singular)
                        // FIXME: distinct
                        return context.all.map { it[input]!! }.let(expr).asLiteralTerm()
                    }
                }

                is NumericLiteralValue -> Compiled { ast.value.asLiteralTerm() }

                is StringLiteralValue -> Compiled { ast.value.asLiteralTerm() }

                is MathOp.Mul -> object: Compiled {
                    val lhs = compile(ast.lhs)
                    val rhs = compile(ast.rhs)
                    override fun execute(context: Context) =
                        (lhs.execute(context)!!.numericalValue * rhs.execute(context)!!.numericalValue).asLiteralTerm()
                }

                is MathOp.Sum -> object: Compiled {
                    val lhs = compile(ast.lhs)
                    val rhs = compile(ast.rhs)
                    override fun execute(context: Context) =
                        (lhs.execute(context)!!.numericalValue + rhs.execute(context)!!.numericalValue).asLiteralTerm()
                }

                is MathOp.Diff -> object: Compiled {
                    val lhs = compile(ast.lhs)
                    val rhs = compile(ast.rhs)
                    override fun execute(context: Context) =
                        (lhs.execute(context)!!.numericalValue - rhs.execute(context)!!.numericalValue).asLiteralTerm()
                }

                is MathOp.Div -> object: Compiled {
                    val lhs = compile(ast.lhs)
                    val rhs = compile(ast.rhs)
                    override fun execute(context: Context) =
                        (lhs.execute(context)!!.numericalValue / rhs.execute(context)!!.numericalValue).asLiteralTerm()
                }

                is MathOp.Negative -> object: Compiled {
                    val expr = compile(ast.value)
                    override fun execute(context: Context) =
                        (- expr.execute(context)!!.numericalValue).asLiteralTerm()
                }

                is FuncCall -> object: Compiled {
                    val func = funcs[ast.name]!!
                    val args = ast.args.map { arg -> compile(arg) }
                    override fun execute(context: Context) = func(args.map { it.execute(context)!! })
                }

            }

        }

    }

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class BindingAggregate(
        val type: Type,
        val input: BindingValues,
        val distinct: Boolean
    ) : Expression {

        enum class Type {
            COUNT,
            SUM,
            MIN,
            MAX,
            AVG,
            GROUP_CONCAT,
            SAMPLE
        }

        override fun toString() = "${type.name}($input)"

    }

    data class FuncCall(
        val name: String,
        val args: List<Expression>
    ) : Expression {

        override fun toString() = "$name(${args.joinToString()})"

    }

    sealed class MathOp : Expression {

        enum class Operator {
            SUM, MUL, DIFF, DIV
        }

        data class Sum(val lhs: Expression, val rhs: Expression) : MathOp() {
            override fun toString() = "($lhs + $rhs)"
        }

        data class Diff(val lhs: Expression, val rhs: Expression) : MathOp() {
            override fun toString() = "($lhs - $rhs)"
        }

        data class Mul(val lhs: Expression, val rhs: Expression) : MathOp() {
            override fun toString() = "($lhs * $rhs)"
        }

        data class Div(val lhs: Expression, val rhs: Expression) : MathOp() {
            override fun toString() = "($lhs / $rhs)"
        }

        /* = - value */
        @JvmInline
        value class Negative(val value: Expression) : Expression {
            override fun toString() = "(- $value)"

            companion object {
                fun of(value: Expression) = if (value is Negative) value.value else Negative(value)
            }

        }

        /**
         * Builder for grouping numerical operators read in LTR and fixes the ordering to respect order of operations
         */
        class Builder(
            // the first term of the statement
            start: Expression
        ) {

            // only + & * are stored here, see `add()`
            private val operators = mutableListOf<Operator>()
            private val operands = mutableListOf(start)

            fun add(operator: Operator, operand: Expression) {
                operators.add(operator)
                operands.add(operand)
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): Expression {
                // first grouping all multiplication statements
                if (operands.size == 1) {
                    return operands.single()
                }
                var order = operators.maxOf { it.order }
                while (order >= 1) {
                    var i = 0
                    while (i < operators.size) {
                        if (operators[i].order == order) {
                            fuse(i)
                        } else {
                            ++i
                        }
                    }
                    --order
                }
                return operands.single()
            }

            private inline fun fuse(index: Int) {
                val operand1 = operands[index]
                val operand2 = operands[index + 1]
                // the operator is being consumed, so only using it to create the expression
                val new = operators.removeAt(index).create(operand1, operand2)
                // removing one and setting the swapping the other one
                operands.removeAt(index)
                operands[index] = new
            }

        }

    }

    data class Conditional(
        val lhs: Expression,
        val rhs: Expression,
        val operand: Operand
    ) : Expression {
        enum class Operand {
            GREATER_THAN, GREATER_THAN_OR_EQ, LESS_THAN, LESS_THAN_OR_EQ, EQUAL
        }
    }

    /**
     * BIND(`expression` AS ?`target`)
     */
    // not an ExpressionAST subtype as this cannot be directly used in other expressions
    data class BindingStatement(
        val expression: Expression,
        val target: String
    )

    @JvmInline
    value class BindingValues(val name: String) : Expression {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class NumericLiteralValue(val value: Number) : Expression {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class StringLiteralValue(val value: String) : Expression {
        override fun toString() = value
    }

}

/* helpers */

private val Expression.MathOp.Operator.order: Int get() = when (this) {
    Expression.MathOp.Operator.SUM -> 1
    Expression.MathOp.Operator.DIFF -> 1
    Expression.MathOp.Operator.MUL -> 2
    Expression.MathOp.Operator.DIV -> 2
}

private fun Expression.MathOp.Operator.create(lhs: Expression, rhs: Expression): Expression.MathOp = when (this) {
    Expression.MathOp.Operator.SUM -> Expression.MathOp.Sum(lhs, rhs)
    Expression.MathOp.Operator.DIFF -> Expression.MathOp.Diff(lhs, rhs)
    Expression.MathOp.Operator.MUL -> Expression.MathOp.Mul(lhs, rhs)
    Expression.MathOp.Operator.DIV -> Expression.MathOp.Div(lhs, rhs)
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
