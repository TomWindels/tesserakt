package core.sparql.compiler

import core.rdf.Triple

sealed interface Token {

    val syntax: String

    enum class Syntax(override val syntax: String): Token {
        /* structural tokens */
        CurlyBracketStart("{"),
        CurlyBracketEnd("}"),
        RoundBracketStart("("),
        RoundBracketEnd(")"),
        PredicateOr("|"),
        PredicateChain("/"),
        Star("*"),
        /* in-pattern only structural tokens */
        ObjectEnd(","),
        PredicateEnd(";"),
        PatternEnd("."),
        BlankStart("["),
        BlankEnd("]"),
        /* keywords */
        Prefix("PREFIX"),
        Select("SELECT"),
        Construct("CONSTRUCT"),
        Where("WHERE"),
        As("AS"),
        Bind("BIND"),
        Filter("FILTER"),
        Limit("LIMIT"),
        Union("UNION");

        override fun toString() = "reserved character `$syntax`"

    }

    data class Term(
        /** The value of the term from the query, either `a`, `<term>` or `ex:term` **/
        val value: String
    ): Token {
        override fun toString() = "term `$value`"
        override val syntax = value
    }

    companion object {

        val syntax = Syntax.entries.associateBy { it.syntax }

    }

}
