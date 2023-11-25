package core.rdf

import kotlin.jvm.JvmInline

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

}
