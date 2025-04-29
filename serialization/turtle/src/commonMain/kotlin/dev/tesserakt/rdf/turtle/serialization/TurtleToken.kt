package dev.tesserakt.rdf.turtle.serialization

import kotlin.jvm.JvmInline

internal sealed interface TurtleToken {

    val syntax: String

    enum class Structural(override val syntax: String): TurtleToken {
        StatementTermination("."),
        PredicateTermination(";"),
        ObjectTermination(","),
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

    sealed interface TermToken: TurtleToken

    sealed interface NonLiteralTerm: TurtleToken, TermToken

    /** = `<my_term>`, value is without < > **/
    data class Term(val value: String): TurtleToken, NonLiteralTerm, TermToken {
        override val syntax = "<$value>"
        override fun toString() = "term `$syntax`"
    }

    /** = `#term` **/
    @JvmInline
    value class RelativeTerm(val value: String): TurtleToken, NonLiteralTerm, TermToken {
        override val syntax get() = "<$value>"
        override fun toString(): String = "relative term `$syntax`"
    }

    /** = `my:term` **/
    data class PrefixedTerm(val prefix: String, val value: String): TurtleToken, NonLiteralTerm, TermToken {
        override val syntax = "$prefix:$value"
        override fun toString(): String = "prefixed term `$syntax`"
    }

    /** any literal **/
    data class LiteralTerm(
        val value: String,
        val type: NonLiteralTerm,
    ): TurtleToken, TermToken {
        override val syntax get() = "\"$value\"^^${type.syntax}"
        override fun toString(): String = "literal `$syntax`"
    }

}
