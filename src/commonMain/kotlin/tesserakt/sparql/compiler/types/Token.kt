package tesserakt.sparql.compiler.types

sealed interface Token {

    val syntax: String

    enum class Syntax(override val syntax: String): Token {
        /* structural tokens */
        CurlyBracketStart("{"),
        CurlyBracketEnd("}"),
        RoundBracketStart("("),
        RoundBracketEnd(")"),
        PredicateOr("|"),
        ForwardSlash("/"),
        Asterisk("*"),
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
        Union("UNION"),
        Distinct("DISTINCT"),
        Optional("OPTIONAL"),
        /* functional keywords */
        FunCount("COUNT"),
        FunMin("MIN"),
        FunMax("MAX"),
        FunAvg("AVG"),
        /* operators */
        OpMinus("-"),
        OpPlus("+"),
        // times `*` & division `/` are known as `Asterisk` and `ForwardSlash`
        /* end of syntax tokens */;

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

    data class NumericLiteral(
        /** The value of a binding from the query, minus the `?` **/
        val value: Number
    ): Token {
        override fun toString() = "numeric literal `$value`"
        override val syntax = value.toString()
    }

    data class StringLiteral(
        /** The value of a binding from the query, minus the `?` **/
        val value: String
    ): Token {
        override fun toString() = "string literal `$value`"
        override val syntax = value
    }

    data object EOF: Token {
        override val syntax: String = "EOF"
    }

    // TODO: custom dtype literals
    // TODO: string literals w/ lang tags

    companion object {

        val syntax = Syntax.entries.associateBy { it.syntax }

        /* series of helper accessors */

        val Token.literalNumericValue get() = (this as NumericLiteral).value
        val Token.literalTextValue get() = (this as StringLiteral).value
        val Token.bindingName get() = (this as Binding).name

    }

}
