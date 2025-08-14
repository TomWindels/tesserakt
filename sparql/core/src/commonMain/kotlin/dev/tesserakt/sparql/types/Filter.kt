package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

sealed interface Filter: QueryAtom {

    @JvmInline
    value class Predicate(val expression: Expression): Filter

    @JvmInline
    value class Exists(val pattern: GraphPattern): Filter

    @JvmInline
    value class NotExists(val pattern: GraphPattern): Filter

}
