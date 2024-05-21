package dev.tesserakt.sparql.compiler.types

sealed interface Token {

    val syntax: String

    enum class Symbol(override val syntax: String): Token {
        /* structural tokens */
        CurlyBracketStart("{"),
        CurlyBracketEnd("}"),
        RoundBracketStart("("),
        RoundBracketEnd(")"),
        PredicateOr("|"),
        ForwardSlash("/"),
        Asterisk("*"),
        ExclamationMark("!"),
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

        override fun toString() = "symbol $syntax"

    }

    enum class Keyword(override val syntax: String): Token {
        RdfTypePredicate("a"),
        Prefix("PREFIX"),
        Select("SELECT"),
        Construct("CONSTRUCT"),
        Where("WHERE"),
        As("AS"),
        Bind("BIND"),
        Filter("FILTER"),
        Limit("LIMIT"),
        Union("UNION"),
        Distinct("DISTINCT"),
        Optional("OPTIONAL"),
        FunCount("COUNT"),
        FunMin("MIN"),
        FunMax("MAX"),
        FunAvg("AVG"),
        /* end of (supported) keywords */;

        override fun toString() = "keyword `$syntax`"

    }

    data class PrefixedTerm(
        /** The value of the term before the colon **/
        val namespace: String,
        /** The value of the term after the colon **/
        val value: String
    ): Token {
        override fun toString() = "term `$value`"
        override val syntax = "$namespace:$value"
    }

    data class Term(
        /** The value of the term from the query, without the `<`, `>` **/
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

    data class NumericLiteral(
        val value: Number
    ): Token {
        override fun toString() = "numeric literal `$value`"
        override val syntax = value.toString()
    }

    data class StringLiteral(
        val value: String
    ): Token {
        override fun toString() = "string literal $syntax"
        override val syntax = "\"$value\""
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
