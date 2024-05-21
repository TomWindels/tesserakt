package dev.tesserakt.sparql.compiler.types

import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

data class Aggregation(
    val root: Aggregator,
    val output: Token.Binding
): AST {

    sealed interface Aggregator: AST

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class Builtin(
        val type: Type,
        val input: Aggregator
    ): Aggregator {

        enum class Type {
           MIN,
           MAX,
           COUNT,
           AVG,
        }

        override fun toString() = "${type.name}($input)"

    }

    sealed class MathOp(val operands: List<Aggregator>): Aggregator {

        enum class Operator {
            SUM, PRODUCT, DIFFERENCE, DIVISION
        }

        class Sum(operands: Iterable<Aggregator>): MathOp(
            operands = operands
                .flatMap { if (it is Sum) it.operands else listOf(it.stabilised()) }
                .stabilised(0) { first, second -> first.toDouble() + second.toDouble() }
                .sortedBy { it.sortValue }
        ) {
            override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " + ")
        }

        class Multiplication(operands: Iterable<Aggregator>): MathOp(
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
        value class Negative(val value: Aggregator): Aggregator {
            override fun toString() = "- $value"
            companion object {
                fun of(value: Aggregator) = if (value is Negative) value.value else Negative(value)
            }

        }

        /* = 1 / value */
        @JvmInline
        value class Inverse(val value: Aggregator): Aggregator {
            override fun toString() = "1 / $value"
            companion object {
                fun of(value: Aggregator) = if (value is Inverse) value.value else Inverse(value)
            }

        }

        override fun hashCode(): Int = this::class.simpleName!!.hashCode() + operands.hashCode()

        /**
         * Builder for grouping numerical operators read in LTR and fixes the ordering to respect order of operations
         */
        class Builder(
            // the first term of the statement
            start: Aggregator
        ) {

            // only + & * are stored here, see `add()`
            private val operators = mutableListOf<Operator>()
            private val operands = mutableListOf(start)

            fun add(operator: Operator, operand: Aggregator) {
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
            fun build(): Aggregator {
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
                action: (first: Aggregator, second: Aggregator) -> Aggregator
            ) {
                val operand1: Aggregator
                val operand2: Aggregator
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
    value class BindingValues(val name: String): Aggregator {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class DistinctBindingValues(val name: String): Aggregator {
        override fun toString() = "DISTINCT ?$name"
    }

    @JvmInline
    value class LiteralValue(val value: Number): Aggregator {
        override fun toString() = value.toString()
    }

    companion object {

        /**
         * A helper value used to stably sort operations
         */
        @Suppress("RecursivePropertyAccessor")
        private val Aggregator.sortValue: Int
            get() = when (this) {
                is BindingValues -> name.hashCode().absoluteValue % 100 + 1
                is LiteralValue -> value.hashCode().absoluteValue % 100 + 3
                is Builtin -> input.sortValue * 100 + (type.ordinal + 1) * 10
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
        private val Aggregator.stableValue: Number?
            get() = when (this) {
                is BindingValues -> null
                is LiteralValue -> value
                is Builtin -> null // can only be applied to bindings, so no stable value
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
        private fun Aggregator.stabilised() = stableValue?.let { LiteralValue(it) } ?: this

        /**
         * Stabilises the incoming aggregations, replacing all where possible with a `LiteralValue` as last element
         *  of the returned list. Aggregation of two elements is done using the provided `aggregator`
         */
        private fun Iterable<Aggregator>.stabilised(
            neutral: Number,
            aggregator: (first: Number, second: Number) -> Number
        ): List<Aggregator> {
            var aggregated = neutral
            val remaining = mutableListOf<Aggregator>()
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

        val Aggregator.builtin get() = this as Builtin

        val Aggregator.sum get() = this as MathOp.Sum

        val Aggregator.multiplication get() = this as MathOp.Multiplication

        @Suppress("RecursivePropertyAccessor")
        val Aggregator.literal get(): LiteralValue = when (this) {
            is MathOp.Inverse -> LiteralValue(1 / value.literal.value.toDouble())
            is MathOp.Negative -> LiteralValue(- value.literal.value.toDouble())
            else -> this as LiteralValue
        }

        val Aggregator.bindings get() = this as BindingValues

        val Aggregator.distinctBindings get() = this as DistinctBindingValues

    }

}
