package dev.tesserakt.sparql.compiler.lexer

import kotlin.jvm.JvmInline

sealed interface Token {

    val syntax: String

    enum class Symbol(override val syntax: String): Token {
        /* structural tokens */
        CurlyBracketStart("{"),
        CurlyBracketEnd("}"),
        RoundBracketStart("("),
        RoundBracketEnd(")"),
        AngularBracketStart("<"),
        AngularBracketEnd(">"),
        Equals("="),
        NotEquals("!="),
        LTEQ("<="),
        GTEQ(">="),
        PredicateOr("|"),
        ForwardSlash("/"),
        Asterisk("*"),
        ExclamationMark("!"),
        ExprAnd("&&"),
        ExprOr("||"),
        /* operators */
        OpMinus("-"),
        OpPlus("+"),
        /* in-pattern only structural tokens */
        Comma(","),
        SemiColon(";"),
        Period("."),
        BlankStart("["),
        BlankEnd("]")
        /* end of (supported) (structural) symbols */;

        override fun toString() = "symbol `$syntax`"

    }

    enum class Keyword(override val syntax: String): Token {
        /* term keywords */
        RdfTypePredicate("a"),
        True("TRUE"),
        False("FALSE"),
        /* structural keywords */
        Prefix("PREFIX"),
        Select("SELECT"),
        Construct("CONSTRUCT"),
        Insert("INSERT"),
        Where("WHERE"),
        As("AS"),
        Ask("ASK"),
        Bind("BIND"),
        Filter("FILTER"),
        Group("GROUP"),
        Having("HAVING"),
        Union("UNION"),
        Distinct("DISTINCT"),
        Optional("OPTIONAL"),
        Bindings("BINDINGS"),
        Values("VALUES"),
        Exists("EXISTS"),
        Not("NOT"),
        /* solution sequence modifiers */
        Order("ORDER"),
        By("BY"),
        Asc("ASC"),
        Desc("DESC"),
        Limit("LIMIT"),
        Offset("OFFSET"),
        /* aggregates */
        AggCount("COUNT"),
        AggSum("SUM"),
        AggMin("MIN"),
        AggMax("MAX"),
        AggAvg("AVG"),
        AggGroupConcat("GROUP_CONCAT"),
        AggSample("SAMPLE"),
        /* end of (supported) keywords */;

        override fun toString() = "keyword `$syntax`"

    }

    sealed interface Term : Token

    data class PrefixedTerm(
        /** The value of the term before the colon **/
        val namespace: String,
        /** The value of the term after the colon **/
        val value: String
    ): Term {
        override fun toString() = "term `$namespace:$value`"
        override val syntax = "$namespace:$value"
    }

    data class Uri(
        /** The value of the term from the query, without the `<`, `>` **/
        val value: String
    ): Term {
        override fun toString() = "term `$value`"
        override val syntax = "<$value>"
    }

    // special type of term, it was prefixed with `_:`
    data class BlankTerm(
        /** The value of the term from the query, without the `<`, `>` **/
        val value: String
    ): Term {
        override fun toString() = "blank term `$value`"
        override val syntax = value
    }

    // whilst it's not a term in the strictest sense, functionally it is positioned as one
    data class Binding(
        /** The value of a binding from the query, minus the `?` **/
        val name: String
    ): Term {
        override fun toString() = "binding `$name`"
        override val syntax = "?$name"
    }

    data class NumericLiteral(
        val value: Number
    ): Term {
        override fun toString() = "numeric literal `$value`"
        override val syntax = value.toString()
    }

    data class StringLiteral(
        val value: String
    ): Term {
        override fun toString() = "string literal $syntax"
        override val syntax = "\"$value\""
    }

    data class TypedLiteral(
        val value: String,
        val datatype: Term,
    ): Term {
        override fun toString() = "typed literal $syntax"
        override val syntax = "\"$value\"^^${datatype.syntax}"
    }

    /**
     * A standalone set of text, typically used as an identifier for RDF functions, e.g. `strlen` and `langMatches`.
     * The valid set of characters are [a-zA-Z0-9_]
     */
    @JvmInline
    value class Identifier(
        val value: String,
    ): Token {
        override fun toString() = "identifier `$value`"
        override val syntax: String
            get() = value
    }

    data object EOF: Token {
        override val syntax: String = "EOF"
    }

    // TODO: custom dtype literals
    // TODO: string literals w/ lang tags

    companion object {

        val syntax = ((Symbol.entries + Keyword.entries) as List<Token>).associateBy { it.syntax }

        /* series of helper accessors */

        val Token.literalNumericValue get() = (this as NumericLiteral).value
        val Token.literalTextValue get() = (this as StringLiteral).value
        val Token.bindingName get() = (this as Binding).name

    }

}
