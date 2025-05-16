package dev.tesserakt.rdf.turtle.serialization

import kotlin.jvm.JvmInline

internal sealed interface TurtleToken {

    val syntax: String

    /**
     * A structural token: the token does not have to be followed by whitespace
     */
    enum class Structural(override val syntax: String): TurtleToken {
        StatementTermination("."),
        PredicateTermination(";"),
        ObjectTermination(","),
        BlankStart("["),
        BlankEnd("]"),
        ListStart("("),
        ListEnd(")"),
        ;

        companion object {

            private val min: Int
            private val backing: Array<Structural?>

            init {
                val mapped = entries.associateBy { it.syntax.single().code }
                min = entries.minOf { it.syntax.single().code }
                val max = entries.maxOf { it.syntax.single().code }
                backing = Array(max - min + 1) { mapped[it + min] }
            }

            operator fun get(char: Char): Structural? {
                val i = char.code - min
                return if (i < backing.size) backing[i] else null
            }
        }
    }

    /**
     * A keyword token: the token has to be followed by a whitespace (or EOF)
     */
    enum class Keyword(override val syntax: String): TurtleToken {
        BaseAnnotationA("@base"),
        BaseAnnotationB("BASE"),
        PrefixAnnotationA("@prefix"),
        PrefixAnnotationB("PREFIX"),
        TypePredicate("a"),
        TrueLiteral("true"),
        FalseLiteral("false"),
        ;

        companion object {
            val CaseInsensitive = listOf(
                BaseAnnotationB,
                PrefixAnnotationB,
            )
            val CaseSensitive = entries - CaseInsensitive
        }
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

    data class LocalizedLiteralTerm(
        val value: String,
        val language: String,
    ): TurtleToken, TermToken {
        override val syntax get() = "\"$value\"@$language"
        override fun toString(): String = "literal `$syntax@$language`"
    }

    data object EOF : TurtleToken {
        override val syntax: String = ""
    }

}
