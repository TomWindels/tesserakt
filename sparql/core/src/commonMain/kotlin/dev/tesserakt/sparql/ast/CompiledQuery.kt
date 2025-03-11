package dev.tesserakt.sparql.ast

sealed class CompiledQuery: QueryAtom {

    abstract val body: GraphPattern

}
