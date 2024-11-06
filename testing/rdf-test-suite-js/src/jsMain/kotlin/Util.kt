import dev.tesserakt.sparql.runtime.common.types.Bindings

fun createSelectResult(bindings: List<Bindings>, variables: List<String> = bindings.extractVariables()): IQueryResultBindings {
    return object: IQueryResultBindings {
        override val type: String = "bindings"
        override val variables: Array<String> = variables.toTypedArray()
        override val checkOrder: Boolean = false
        val value = bindings.map { it.toJsObject() }.toTypedArray()
    }
}

private fun List<Bindings>.extractVariables() = this.flatMapDistinct { it.keys }

private inline fun <T, R> List<T>.flatMapDistinct(transform: (T) -> Iterable<R>): List<R> {
    val result = mutableSetOf<R>()
    forEach { element -> result.addAll(transform(element)) }
    return result.toList()
}

private fun <T> Map<String, T>.toJsObject(): dynamic {
    val result: dynamic = Any()
    forEach { result[it.key] = it.value }
    return result
}
