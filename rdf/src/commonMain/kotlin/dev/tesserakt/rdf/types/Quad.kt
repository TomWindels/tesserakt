package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.ontology.RDF
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

    override fun toString() = "$s $p $o $g"

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

    sealed interface Literal : Object {

        override val value: String

        val type: NamedTerm

    }

    @JvmInline
    value class SimpleLiteral internal constructor(
        override val value: String
    ) : Literal {

        override val type: NamedTerm
            get() = XSD.string

        override fun toString(): String {
            return "\"${value}\""
        }

    }

    class TypedLiteral internal constructor(
        override val value: String,
        override val type: NamedTerm
    ): Literal {

        override fun toString(): String {
            return "\"$value\"^^${type.value}"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is TypedLiteral) {
                return false
            }
            return value == other.value && type == other.type
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + type.hashCode()
            return result
        }

    }

    class LangString internal constructor(
        override val value: String,
        val language: String,
    ): Literal {

        override val type: NamedTerm
            get() = RDF.langString

        override fun toString(): String {
            return "\"$value\"@$language"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is LangString) {
                return false
            }
            return value == other.value && language == other.language
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + language.hashCode()
            result = 31 * result + type.hashCode()
            return result
        }

    }

    data object DefaultGraph: Graph {
        override val value: String = ""
    }

    companion object {

        @JvmStatic
        fun String.asNamedTerm() = NamedTerm(this)

        @JvmStatic
        fun String.asLiteralTerm() = SimpleLiteral(this)

        @JvmStatic
        fun Int.asLiteralTerm() = TypedLiteral(toString(), type = XSD.int)

        @JvmStatic
        fun Long.asLiteralTerm() = TypedLiteral(toString(), type = XSD.long)

        @JvmStatic
        fun Float.asLiteralTerm() = TypedLiteral(toString(), type = XSD.float)

        @JvmStatic
        fun Double.asLiteralTerm() = TypedLiteral(toString(), type = XSD.double)

        @JvmStatic
        fun Boolean.asLiteralTerm() = TypedLiteral(toString(), type = XSD.boolean)

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

        /* factories */

        @JvmStatic
        fun Literal(value: String): Literal = SimpleLiteral(value)

        @JvmStatic
        fun Literal(value: String, language: String): Literal = LangString(value, language)

        @JvmStatic
        fun Literal(value: String, type: NamedTerm): Literal = if (type != XSD.string) TypedLiteral(value, type) else SimpleLiteral(value)

    }

}
