package dev.tesserakt.rdf.trig.serialization

import kotlin.jvm.JvmInline

internal sealed interface TriGToken {

    val syntax: String

    enum class Structural(override val syntax: String): TriGToken {
        StatementTermination("."),
        PredicateTermination(";"),
        ObjectTermination(","),
        GraphStatementStart("{"),
        GraphStatementEnd("}"),
        BlankStart("["),
        BlankEnd("]"),
        BaseAnnotationA("@base"),
        BaseAnnotationB("BASE"),
        PrefixAnnotationA("@prefix"),
        PrefixAnnotationB("PREFIX"),
        GraphAnnotation("GRAPH"),
        TypePredicate("a"),
        TrueLiteral("true"),
        FalseLiteral("false"),
    }

    sealed interface TermToken: TriGToken

    sealed interface NonLiteralTerm: TriGToken

    /** = `<my_term>`, value is without < > **/
    data class Term(val value: String): TriGToken, NonLiteralTerm, TermToken {
        override val syntax = "<$value>"
        override fun toString() = "term `$syntax`"
    }

    /** = `#term` **/
    @JvmInline
    value class RelativeTerm(val value: String): TriGToken, NonLiteralTerm, TermToken {
        override val syntax get() = "<$value>"
        override fun toString(): String = "term `$syntax`"
    }

    /** = `my:term` **/
    data class PrefixedTerm(val prefix: String, val value: String): TriGToken, NonLiteralTerm, TermToken {
        override val syntax = "$prefix:$value"
        override fun toString(): String = "term `$syntax`"
    }

    /** any literal **/
    class LiteralTerm(
        val value: String,
        val type: NonLiteralTerm,
    ): TriGToken, TermToken {
        override val syntax get() = "\"$value\"^^${type.syntax}"
        override fun toString(): String = "literal `$syntax`"
    }

}
