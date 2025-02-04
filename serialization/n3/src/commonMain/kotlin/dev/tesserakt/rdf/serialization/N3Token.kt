package dev.tesserakt.rdf.serialization

import kotlin.jvm.JvmInline

internal sealed interface N3Token {

    val syntax: String

    enum class Structural(override val syntax: String): N3Token {
        StatementTermination("."),
        PredicateTermination(";"),
        ObjectTermination(","),
        StatementsListStart("{"),
        StatementsListEnd("}"),
        BlankStart("["),
        BlankEnd("]"),
        BaseAnnotationA("@base"),
        BaseAnnotationB("BASE"),
        PrefixAnnotationA("@prefix"),
        PrefixAnnotationB("PREFIX"),
        TypePredicate("a"),
        TrueLiteral("true"),
        FalseLiteral("false"),
    }

    sealed interface TermToken: N3Token

    sealed interface NonLiteralTerm: N3Token

    /** = `<my_term>`, value is without < > **/
    data class Term(val value: String): N3Token, NonLiteralTerm, TermToken {
        override val syntax = "<$value>"
        override fun toString() = "term `$syntax`"
    }

    /** = `#term` **/
    @JvmInline
    value class RelativeTerm(val value: String): N3Token, NonLiteralTerm, TermToken {
        override val syntax get() = "<$value>"
        override fun toString(): String = "term `$syntax`"
    }

    /** = `my:term` **/
    data class PrefixedTerm(val prefix: String, val value: String): N3Token, NonLiteralTerm, TermToken {
        override val syntax = "$prefix:$value"
        override fun toString(): String = "term `$syntax`"
    }

    /** any literal **/
    class LiteralTerm(
        val value: String,
        val type: NonLiteralTerm,
    ): N3Token, TermToken {
        override val syntax get() = "\"$value\"^^${type.syntax}"
        override fun toString(): String = "literal `$syntax`"
    }

}
