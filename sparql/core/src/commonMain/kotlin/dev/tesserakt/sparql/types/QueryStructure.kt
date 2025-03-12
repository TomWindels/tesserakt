package dev.tesserakt.sparql.types

sealed class QueryStructure: QueryAtom {

    abstract val body: GraphPattern

}
