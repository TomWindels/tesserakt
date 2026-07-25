import dev.tesserakt.rdf.types.Quad

fun StoreJs.unwrap() = this.store

fun QuadJs.unwrap() = this.value

fun Quad.Element.toTermJs() = when (this) {
    Quad.DefaultGraph -> DefaultGraphTerm
    is Quad.BlankTerm -> toTermJs()
    is Quad.NamedTerm -> toTermJs()
    is Quad.LangString -> toTermJs()
    is Quad.SimpleLiteral -> toTermJs()
    is Quad.TypedLiteral -> toTermJs()
}

fun Quad.NamedTerm.toTermJs() = NamedTerm(value)

fun Quad.BlankTerm.toTermJs() = BlankTerm(id)

fun Quad.Literal.toTermJs() = when (this) {
    is Quad.LangString -> LiteralTerm(value, null, language)
    is Quad.SimpleLiteral -> LiteralTerm(value)
    // Kotlin's `Quad.NamedTerm` is not supported by the JS API
    is Quad.TypedLiteral -> LiteralTerm(value, type.value)
}

fun Quad.Subject.toTermJs(): TermJs = when (this) {
    is Quad.BlankTerm -> toTermJs()
    is Quad.NamedTerm -> toTermJs()
}

fun Quad.Predicate.toTermJs(): TermJs = when (this) {
    is Quad.NamedTerm -> toTermJs()
}

fun Quad.Object.toTermJs(): TermJs = when (this) {
    is Quad.BlankTerm -> toTermJs()
    is Quad.NamedTerm -> toTermJs()
    is Quad.Literal -> toTermJs()
}

fun Quad.Graph.toTermJs(): GraphTerm = when (this) {
    Quad.DefaultGraph -> DefaultGraphTerm
    is Quad.BlankTerm -> GraphTerm(id)
    is Quad.NamedTerm -> GraphTerm(value)
}
