package core.rdf.types

import core.rdf.ontology.XSD
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@Suppress("unused")
data class Triple(
    /** named or blank **/
    val s: Term,
    /** named **/
    val p: NamedTerm,
    /** named, literal or blank **/
    val o: Term
) {

    sealed interface Term {
        val value: String
    }

    @JvmInline
    value class BlankTerm(override val value: String): Term
    @JvmInline
    value class NamedTerm(override val value: String): Term
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
        fun String.asLiteral() = Literal(this, type = XSD.string)

        @JvmStatic
        fun Int.asLiteral() = Literal(this, type = XSD.int)

        @JvmStatic
        fun Long.asLiteral() = Literal(this, type = XSD.long)

        @JvmStatic
        fun Float.asLiteral() = Literal(this, type = XSD.float)

        @JvmStatic
        fun Double.asLiteral() = Literal(this, type = XSD.double)

        @JvmStatic
        fun Boolean.asLiteral() = Literal(this, type = XSD.boolean)

    }

}
