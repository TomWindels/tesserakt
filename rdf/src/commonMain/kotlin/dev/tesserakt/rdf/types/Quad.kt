package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.ontology.XSD
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@Suppress("unused")
data class Quad(
    val s: Subject,
    val p: Predicate,
    val o: Object,
    val g: Graph = DefaultGraph
) {

    override fun toString() = "$s $p $o"

    sealed interface Element {
        val value: String
    }

    sealed interface Subject : Element

    sealed interface Predicate : Element

    sealed interface Object : Element

    sealed interface Graph : Element

    @JvmInline
    value class BlankTerm(val id: Int): Subject, Object, Graph {
        override val value: String
            get() = "blank_$id"
        override fun toString() = value
    }

    @JvmInline
    value class NamedTerm(override val value: String): Subject, Predicate, Object, Graph {
        override fun toString() = value
    }

    data class Literal(
        override val value: String,
        val type: NamedTerm
    ): Object {
        override fun toString(): String {
            return "\"$value\"^^${type.value}"
        }
    }

    data object DefaultGraph: Graph {
        override val value: String = ""
    }

    companion object {

        @JvmStatic
        fun String.asNamedTerm() = NamedTerm(this)

        @JvmStatic
        fun String.asLiteralTerm() = Literal(this, type = XSD.string)

        @JvmStatic
        fun Int.asLiteralTerm() = Literal(toString(), type = XSD.int)

        @JvmStatic
        fun Long.asLiteralTerm() = Literal(toString(), type = XSD.long)

        @JvmStatic
        fun Float.asLiteralTerm() = Literal(toString(), type = XSD.float)

        @JvmStatic
        fun Double.asLiteralTerm() = Literal(toString(), type = XSD.double)

        @JvmStatic
        fun Boolean.asLiteralTerm() = Literal(toString(), type = XSD.boolean)

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
