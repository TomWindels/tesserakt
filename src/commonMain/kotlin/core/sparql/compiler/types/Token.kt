package core.sparql.compiler.types

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
        ExclamationMark("!"),
        /* in-pattern only structural tokens */
        ObjectEnd(","),
        PredicateEnd(";"),
        PatternEnd("."),
        BlankStart("["),
        BlankEnd("]"),
        TypePredicate("a"),
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
        /** The value of the term from the query, either `<term>` or `ex:term` **/
        val value: String
    ): Token {
        override fun toString() = "term `$value`"
        override val syntax = value
    }

    data class Binding(
        /** The value of a binding from the query, minus the `?` **/
        val name: String
    ): Token {
        override fun toString() = "binding `$name`"
        override val syntax = name
    }

    companion object {

        val syntax = Syntax.entries.associateBy { it.syntax }

    }

}
