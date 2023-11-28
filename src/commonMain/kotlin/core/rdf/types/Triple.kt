package core.rdf.types

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

data class Triple(
    val s: NamedTerm,
    val p: NamedTerm,
    val o: Term
) {

    sealed interface Term {
        val value: String
    }

    @JvmInline
    value class BlankTerm(override val value: String): Term
    @JvmInline
    value class NamedTerm(override val value: String): Term
    @JvmInline
    value class Literal(override val value: String): Term

    companion object {

        @JvmStatic
        fun String.asNamedNode() = NamedTerm(this)

        @JvmStatic
        fun String.asLiteral() = Literal(this)

    }

}
