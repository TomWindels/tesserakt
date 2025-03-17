package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.types.Expression.*
import kotlin.jvm.JvmInline

class FilterExpression(expr: Expression) {

    sealed interface OperationValue {
        object Unbound: OperationValue {
            override fun equals(other: Any?) = false
        }
        @JvmInline
        value class SingleValue(val term: Quad.Term) : OperationValue
        @JvmInline
        value class SingleMapping(val mapping: Mapping) : OperationValue
    }

    fun interface Operation {

        fun eval(input: OperationValue): OperationValue

        companion object {
            fun from(expr: Expression): Operation {
                return when (expr) {
                    is BindingAggregate -> TODO()
                    is BindingValues ->
                        ValueLookUpOperation(name = expr.name)

                    is Expression.Comparison -> when (expr.operand) {
                        Expression.Comparison.Operand.GREATER_THAN ->
                            Comparison.GT(left = from(expr.lhs), right = from(expr.rhs))

                        Expression.Comparison.Operand.GREATER_THAN_OR_EQ ->
                            Comparison.GTEQ(left = from(expr.lhs), right = from(expr.rhs))

                        Expression.Comparison.Operand.LESS_THAN ->
                            Comparison.LT(left = from(expr.lhs), right = from(expr.rhs))

                        Expression.Comparison.Operand.LESS_THAN_OR_EQ ->
                            Comparison.LTEQ(left = from(expr.lhs), right = from(expr.rhs))

                        Expression.Comparison.Operand.EQUAL ->
                            Comparison.EQ(left = from(expr.lhs), right = from(expr.rhs))

                        Expression.Comparison.Operand.NOT_EQUAL ->
                            Comparison.NEQ(left = from(expr.lhs), right = from(expr.rhs))
                    }

                    is FuncCall -> TODO()
                    is MathOp.Diff -> TODO()
                    is MathOp.Div -> TODO()
                    is MathOp.Mul -> TODO()
                    is MathOp.Sum -> TODO()
                    is MathOp.Negative -> TODO()
                    is NumericLiteralValue -> ConstantValueOperation(expr.value.asLiteralTerm().into())
                    is StringLiteralValue -> ConstantValueOperation(expr.value.asLiteralTerm().into())
                }
            }
        }

    }

    sealed interface Comparison : Operation {

        override fun eval(input: OperationValue): OperationValue

        class EQ(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                return (left.eval(input).term == right.eval(input).term).asLiteralTerm().into()
            }

        }

        class NEQ(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                return (left.eval(input).term != right.eval(input).term).asLiteralTerm().into()
            }

        }

        class LT(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                val a = left.eval(input).term ?: return false.asLiteralTerm().into()
                val b = right.eval(input).term ?: return false.asLiteralTerm().into()
                return (compare(a.literal, b.literal) < 0).asLiteralTerm().into()
            }
        }


        class GT(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                val a = left.eval(input).term ?: return false.asLiteralTerm().into()
                val b = right.eval(input).term ?: return false.asLiteralTerm().into()
                return (compare(a.literal, b.literal) > 0).asLiteralTerm().into()
            }
        }

        class LTEQ(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                val a = left.eval(input).term ?: return false.asLiteralTerm().into()
                val b = right.eval(input).term ?: return false.asLiteralTerm().into()
                return (compare(a.literal, b.literal) <= 0).asLiteralTerm().into()
            }

        }

        class GTEQ(private val left: Operation, private val right: Operation) : Comparison {

            override fun eval(input: OperationValue): OperationValue {
                val a = left.eval(input).term ?: return false.asLiteralTerm().into()
                val b = right.eval(input).term ?: return false.asLiteralTerm().into()
                return (compare(a.literal, b.literal) >= 0).asLiteralTerm().into()
            }

        }

        companion object {

            /**
             * Compares [left] value with the specified value for order. Returns zero if [left] value is equal to the
             *  specified [right] value, a negative number if it's less than [right], or a positive number if it's
             *  greater than [right].
             */
            private fun compare(left: Quad.Literal, right: Quad.Literal): Int {
                return when {
                    left.isNumericalValue() && right.isNumericalValue() ->
                        left.numericalValue.compareTo(right.numericalValue)

                    else ->
                        throw UnsupportedOperationException("Cannot compare literals with types ${left.type} and ${right.type}")
                }
            }

        }

    }

    @JvmInline
    private value class ValueLookUpOperation(private val name: String) : Operation {
        override fun eval(input: OperationValue): OperationValue {
            return input.mapping[name].into()
        }
    }

    @JvmInline
    private value class ConstantValueOperation(private val constant: OperationValue.SingleValue) : Operation {

        override fun eval(input: OperationValue): OperationValue {
            return constant
        }
    }

    @JvmInline
    private value class BooleanCoercionOperation(private val parent: Operation) : Operation {

        override fun eval(input: OperationValue): OperationValue {
            val result = parent.eval(input).term
            return when {
                result !is Quad.Literal -> {
                    throw IllegalStateException("Unexpected non-literal `$result` received!")
                }

                result.type == XSD.boolean -> {
                    result.into()
                }

                else -> {
                    throw IllegalStateException("Unexpected literal type `${result.type}`!")
                }
            }
        }
    }

    private val root = BooleanCoercionOperation(parent = Operation.from(expr))

    fun test(mapping: Mapping): Boolean {
        return root.eval(mapping.into()).term == true.asLiteralTerm()
    }

    companion object {

        private fun Quad.Term.into() = OperationValue.SingleValue(this)

        private fun Quad.Term?.into() = this?.let { OperationValue.SingleValue(this) } ?: OperationValue.Unbound

        private fun Mapping.into() = OperationValue.SingleMapping(this)

        private val OperationValue.term: Quad.Term?
            get() = when (this) {
                is OperationValue.SingleValue -> term
                // `is`, as `equals` is always false!
                is OperationValue.Unbound -> null
                else -> throw IllegalStateException("Single term value expected, but received a `${this::class.simpleName}` instead!")
            }

        private val OperationValue.mapping: Mapping
            get() = (this as? OperationValue.SingleMapping)?.mapping
                ?: throw IllegalStateException("Single mapping value expected, but received a `${this::class.simpleName}` instead!")

        private val Quad.Term.literal
            get() = (this as? Quad.Literal)
                ?: throw IllegalStateException("Literal term value expected, but received $this instead!")

    }

}

private val numerals = setOf(XSD.long, XSD.int, XSD.double, XSD.float, XSD.integer)

private fun Quad.Literal.isNumericalValue(): Boolean {
    return type in numerals
}

private val Quad.Literal.numericalValue: Double
    get() = this.value.toDouble()
