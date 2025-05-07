package dev.tesserakt.sparql.coroutines

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.query.QueryState
import kotlin.jvm.JvmInline


sealed interface Delta<out T> {
    val value: T

    @JvmInline
    value class Addition<T>(override val value: T): Delta<T> {
        override fun toString(): String = "[+] $value"
    }

    @JvmInline
    value class Deletion<T>(override val value: T): Delta<T> {
        override fun toString(): String = "[-] $value"
    }

}

internal fun Delta(change: QueryState.ResultChange<out Bindings>): Delta<Bindings> = when (change) {
    is QueryState.ResultChange.New<*> -> Delta.Addition(value = change.value)
    is QueryState.ResultChange.Removed<*> -> Delta.Deletion(value = change.value)
}

internal fun DataDelta(delta: Delta<Quad>) = when (delta) {
    is Delta.Addition<*> -> DataAddition(delta.value)
    is Delta.Deletion<*> -> DataDeletion(delta.value)
}
