import dev.tesserakt.rdf.types.Quad

/* A series of helpers to work with internal types inside of Kotlin codebases, so the JS API does not expose them directly */

fun MutableStoreJs.unwrap() = this.store

fun QuadJs.unwrap() = this.value

fun Quad.Term.toJsTerm() = QuadJs.TermJs(this)
