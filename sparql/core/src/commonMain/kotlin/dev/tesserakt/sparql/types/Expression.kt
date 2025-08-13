package dev.tesserakt.sparql.types

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

sealed interface Expression : QueryAtom {

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

    /* = - value */
    @JvmInline
    value class Negative(val value: Expression) : Expression {
        override fun toString() = "(- $value)"

        companion object {
            fun of(value: Expression) = if (value is Negative) value.value else Negative(value)
        }

    }

    data class Calculation(
        val lhs: Expression,
        val rhs: Expression,
        val operator: Operator,
    ) : Expression {

        enum class Operator(val sign: String) {
            SUM("+"),
            MUL("⨉"),
            SUB("-"),
            DIV("÷"),
            AND("&&"),
            OR("||"),
            CMP_LT("<"),
            CMP_LE("<="),
            CMP_EQ("="),
            CMP_NEQ("!="),
            CMP_GE(">="),
            CMP_GT(">"),
            ;
        }

        override fun toString(): String {
            return "($lhs ${operator.sign} $rhs)"
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
    value class UriValue(val uri: Quad.NamedTerm) : Expression {
        override fun toString() = uri.toString()
    }

    @JvmInline
    value class NumericLiteralValue(val value: Number) : Expression {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class BooleanLiteralValue(val value: Boolean) : Expression {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class StringLiteralValue(val value: String) : Expression {
        override fun toString() = value
    }

}
