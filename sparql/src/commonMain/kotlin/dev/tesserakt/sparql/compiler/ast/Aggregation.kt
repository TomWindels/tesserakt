package dev.tesserakt.sparql.compiler.ast

import dev.tesserakt.sparql.compiler.lexer.Token
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

data class Aggregation(
    val expression: Expression,
    val target: Token.Binding
): ASTNode {

    sealed interface Expression: ASTNode

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class FuncCall(
        val type: Type,
        val input: Expression
    ): Expression {

        enum class Type {
           MIN,
           MAX,
           COUNT,
           AVG,
        }

        override fun toString() = "${type.name}($input)"

    }

    sealed class MathOp(val operands: List<Expression>): Expression {

        enum class Operator {
            SUM, PRODUCT, DIFFERENCE, DIVISION
        }

        class Sum(operands: Iterable<Expression>): MathOp(
            operands = operands
                .flatMap { if (it is Sum) it.operands else listOf(it.stabilised()) }
                .stabilised(0) { first, second -> first.toDouble() + second.toDouble() }
                .sortedBy { it.sortValue }
        ) {
            override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " + ")
        }

        class Multiplication(operands: Iterable<Expression>): MathOp(
            operands = operands
                .flatMap { if (it is Multiplication) it.operands else listOf(it.stabilised()) }
                .stabilised(1) { first, second -> first.toDouble() * second.toDouble() }
                .sortedBy { it.sortValue }
        ) {
            override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " * ")
        }

        override fun equals(other: Any?) =
            other != null &&
            other is MathOp &&
            this::class.simpleName!! == other::class.simpleName!! &&
            operands.size == other.operands.size &&
            operands.containsAll(other.operands)

        /* = - value */
        @JvmInline
        value class Negative(val value: Expression): Expression {
            override fun toString() = "- $value"
            companion object {
                fun of(value: Expression) = if (value is Negative) value.value else Negative(value)
            }

        }

        /* = 1 / value */
        @JvmInline
        value class Inverse(val value: Expression): Expression {
            override fun toString() = "1 / $value"
            companion object {
                fun of(value: Expression) = if (value is Inverse) value.value else Inverse(value)
            }

        }

        override fun hashCode(): Int = this::class.simpleName!!.hashCode() + operands.hashCode()

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
                when (operator) {
                    Operator.DIFFERENCE -> {
                        operators.add(Operator.SUM)
                        operands.add(Negative.of(operand))
                    }
                    Operator.DIVISION -> {
                        operators.add(Operator.PRODUCT)
                        operands.add(Inverse.of(operand))
                    }
                    Operator.SUM, Operator.PRODUCT -> {
                        operators.add(operator)
                        operands.add(operand)
                    }
                }
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): Expression {
                // first grouping all multiplication statements
                var i = 0
                while (i < operators.size) {
                    if (operators[i] == Operator.PRODUCT) {
                        fuse(i) { first, second -> Multiplication(listOf(first, second)) }
                    } else {
                        ++i
                    }
                }
                // followed by a single sum statement if necessary
                return if (operands.size > 1) {
                    Sum(operands)
                } else {
                    operands.first().stabilised()
                }
            }

            private inline fun fuse(
                index: Int,
                action: (first: Expression, second: Expression) -> Expression
            ) {
                val operand1: Expression
                val operand2: Expression
                val op1 = operands[index]
                val op2 = operands[index + 1]
                if (op1.sortValue > op2.sortValue) {
                    operand1 = op1
                    operand2 = op2
                } else {
                    operand2 = op1
                    operand1 = op2
                }
                val new = action(operand1, operand2)
                // the operator has been consumed, so nuking that one
                operators.removeAt(index)
                // removing one and setting the swapping the other one
                operands.removeAt(index)
                operands[index] = new
            }

        }

    }

    @JvmInline
    value class BindingValues(val name: String): Expression {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class DistinctBindingValues(val name: String): Expression {
        override fun toString() = "DISTINCT ?$name"
    }

    @JvmInline
    value class LiteralValue(val value: Number): Expression {
        override fun toString() = value.toString()
    }

    companion object {

        /**
         * A helper value used to stably sort operations
         */
        @Suppress("RecursivePropertyAccessor")
        private val Expression.sortValue: Int
            get() = when (this) {
                is BindingValues -> name.hashCode().absoluteValue % 100 + 1
                is LiteralValue -> value.hashCode().absoluteValue % 100 + 3
                is FuncCall -> input.sortValue * 100 + (type.ordinal + 1) * 10
                is DistinctBindingValues -> name.hashCode() % 100 + 2
                is MathOp.Inverse -> - value.sortValue - 1
                is MathOp.Negative -> - value.sortValue - 2
                is MathOp.Sum -> operands
                    .sumOf { it.sortValue } * 1000 + 100
                is MathOp.Multiplication -> operands
                    .sumOf { it.sortValue } * 1000 + 200
            }

        /**
         * Produces the constant value associated with this expression if possible, `null` otherwise
         */
        @Suppress("RecursivePropertyAccessor")
        private val Expression.stableValue: Number?
            get() = when (this) {
                is BindingValues -> null
                is LiteralValue -> value
                is FuncCall -> null // can only be applied to bindings, so no stable value
                is DistinctBindingValues -> null
                is MathOp.Inverse -> value.stableValue?.let { 1 / it.toDouble() }
                is MathOp.Negative -> value.stableValue?.let { - it.toDouble() }
                is MathOp.Sum -> operands
                    .sumOf { it.stableValue?.toDouble() ?: return null }
                is MathOp.Multiplication -> operands
                    .fold(initial = 1.0) { value, element -> (element.stableValue?.toDouble() ?: return null) * value }
            }

        /**
         * Attempts to replace `this` by a literal value constant, if possible
         */
        private fun Expression.stabilised() = stableValue?.let { LiteralValue(it) } ?: this

        /**
         * Stabilises the incoming aggregations, replacing all where possible with a `LiteralValue` as last element
         *  of the returned list. Aggregation of two elements is done using the provided `aggregator`
         */
        private fun Iterable<Expression>.stabilised(
            neutral: Number,
            aggregator: (first: Number, second: Number) -> Number
        ): List<Expression> {
            var aggregated = neutral
            val remaining = mutableListOf<Expression>()
            forEach { element ->
                element.stableValue
                    ?.let { stabilised -> aggregated = aggregator(aggregated, stabilised) }
                    ?: run { remaining.add(element) }
            }
            if (neutral != aggregated) {
                remaining.add(LiteralValue(aggregated))
            }
            return remaining
        }

        val Expression.builtin get() = this as FuncCall

        val Expression.sum get() = this as MathOp.Sum

        val Expression.multiplication get() = this as MathOp.Multiplication

        @Suppress("RecursivePropertyAccessor")
        val Expression.literal get(): LiteralValue = when (this) {
            is MathOp.Inverse -> LiteralValue(1 / value.literal.value.toDouble())
            is MathOp.Negative -> LiteralValue(- value.literal.value.toDouble())
            else -> this as LiteralValue
        }

        val Expression.bindings get() = this as BindingValues

        val Expression.distinctBindings get() = this as DistinctBindingValues

    }

}
