package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.ontology.XSD
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@Suppress("unused")
class Quad(
    s: Term,
    p: NamedTerm,
    o: Term
) {

    private val data = arrayOf(s, p, o)

    /** named or blank **/
    val s: Term get() = data[0]

    /** named **/
    val p: Term get() = data[1]

    /** named, literal or blank **/
    val o: Term get() = data[2]

    operator fun get(index: Int) = data[index]

    override fun toString() = "Triple <$s $p $o>"

    sealed interface Term {
        val value: String
    }

    @JvmInline
    value class BlankTerm(val id: Int): Term {
        override val value: String
            get() = "blank_$id"
        override fun toString() = value
    }

    @JvmInline
    value class NamedTerm(override val value: String): Term {
        override fun toString() = value
    }

    data class Literal<T>(
        val literal: T,
        val type: NamedTerm,
        override val value: String = literal.toString()
    ): Term {
        override fun toString(): String {
            return "\"$value\"^^${type.value}"
        }
    }

    companion object {

        @JvmStatic
        fun String.asNamedTerm() = NamedTerm(this)

        @JvmStatic
        fun String.asLiteralTerm() = Literal(this, type = XSD.string)

        @JvmStatic
        fun Int.asLiteralTerm() = Literal(this, type = XSD.int)

        @JvmStatic
        fun Long.asLiteralTerm() = Literal(this, type = XSD.long)

        @JvmStatic
        fun Float.asLiteralTerm() = Literal(this, type = XSD.float)

        @JvmStatic
        fun Double.asLiteralTerm() = Literal(this, type = XSD.double)

        @JvmStatic
        fun Boolean.asLiteralTerm() = Literal(this, type = XSD.boolean)

        @JvmStatic
        fun Number.asLiteralTerm() = when (this) {
            is Int -> asLiteralTerm()
            is Long -> asLiteralTerm()
            is Float -> asLiteralTerm()
            /* TODO: byte char & short  */
            else -> toDouble().asLiteralTerm()
        }

        @JvmStatic
        fun <T> T.asLiteralTerm() = when (this) {
            is Number -> asLiteralTerm()
            is String -> asLiteralTerm()
            is Boolean -> asLiteralTerm()
            else -> throw IllegalArgumentException("Unknown literal type `$this`")
        }

    }

}
