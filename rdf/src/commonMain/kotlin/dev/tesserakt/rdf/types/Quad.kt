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
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.NamedTerm(this)")
        )
        fun String.asNamedTerm() = NamedTerm(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun String.asLiteralTerm() = SimpleLiteral(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Int.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Long.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Float.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Double.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Boolean.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged.",
            replaceWith = ReplaceWith("Quad.Literal(this)")
        )
        fun Number.asLiteralTerm() = Literal(this)

        @JvmStatic
        @Deprecated(
            message = "The use of extension methods to create quad terms is discouraged."
            // no simple replacement possible
        )
        fun <T> T.asLiteralTerm() = when (this) {
            is Number -> Literal(this)
            is String -> Literal(this)
            is Boolean -> Literal(this)
            else -> throw IllegalArgumentException("Unknown literal type `$this`")
        }

        /* factories */

        @JvmStatic
        fun Literal(value: String): Literal = SimpleLiteral(value)

        @JvmStatic
        fun Literal(value: Int): Literal = Literal(value = value.toString(), type = XSD.int)

        @JvmStatic
        fun Literal(value: Long): Literal = Literal(value = value.toString(), type = XSD.long)

        @JvmStatic
        fun Literal(value: Float): Literal = Literal(value = value.toString(), type = XSD.float)

        @JvmStatic
        fun Literal(value: Double): Literal = Literal(value = value.toString(), type = XSD.double)

        @JvmStatic
        fun Literal(value: Byte): Literal = Literal(value = value.toString(), type = XSD.byte)

        @JvmStatic
        fun Literal(value: Short): Literal = Literal(value = value.toString(), type = XSD.short)

        @JvmStatic
        fun Literal(value: Boolean): Literal = if (value) TrueLiteral else FalseLiteral

        @JvmStatic
        fun Literal(value: Number): Literal = when (value) {
            is Int -> Literal(value)
            is Long -> Literal(value)
            is Float -> Literal(value)
            is Byte -> Literal(value)
            is Short -> Literal(value)
            else -> Literal(value.toDouble())
        }

        @JvmStatic
        fun Literal(value: String, language: String): Literal = LangString(value, language)

        @JvmStatic
        fun Literal(value: String, type: NamedTerm): Literal = if (type != XSD.string) TypedLiteral(value, type) else SimpleLiteral(value)

        private val TrueLiteral = TypedLiteral("true", XSD.boolean)
        private val FalseLiteral = TypedLiteral("false", XSD.boolean)

    }

}
