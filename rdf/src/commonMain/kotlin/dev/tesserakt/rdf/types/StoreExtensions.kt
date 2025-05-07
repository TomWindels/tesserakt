package dev.tesserakt.rdf.types

/* utility extensions */

inline fun Store.elementAt(index: Int): Quad {
    require(index >= 0)
    var remaining = index
    var element: Quad? = null
    forEachUntil {
        --remaining
        if (remaining == 0) {
            element = it
            true
        } else {
            false
        }
    }
    return element ?: throw NoSuchElementException()
}

inline fun Store.none(crossinline predicate: (Quad) -> Boolean): Boolean {
    var result = true
    forEachUntil { contender ->
        if (predicate(contender)) {
            result = false
            return@forEachUntil true
        }
        false
    }
    return result
}

inline fun Store.all(crossinline predicate: (Quad) -> Boolean): Boolean {
    var result = true
    forEachUntil { contender ->
        if (!predicate(contender)) {
            result = false
            return@forEachUntil true
        }
        false
    }
    return result
}

inline fun Store.count(crossinline predicate: (Quad) -> Boolean): Int {
    var result = 0
    forEach { contender ->
        if (predicate(contender)) {
            ++result
        }
    }
    return result
}

inline fun Store.singleOrNull(crossinline filter: (Quad) -> Boolean): Quad? {
    var result: Quad? = null
    forEachUntil { contender ->
        val selected = filter(contender)
        when {
            selected && result == null -> {
                result = contender
            }
            selected && result != null -> {
                result = null
                // exiting
                return@forEachUntil true
            }
            else -> {
                /* nothing to do */
            }
        }
        // still looping
        return@forEachUntil false
    }
    return result
}

inline fun Store.single(crossinline filter: (Quad) -> Boolean): Quad {
    var result: Quad? = null
    forEach { contender ->
        val selected = filter(contender)
        when {
            selected && result == null -> {
                result = contender
            }
            selected && result != null -> {
                throw IllegalArgumentException("Collection contains more than one matching element.")
            }
            else -> {
                /* nothing to do */
            }
        }
    }
    return result ?: throw IllegalArgumentException("Collection contains no matching elements.")
}

inline fun Store.filter(crossinline predicate: (Quad) -> Boolean): List<Quad> {
    return filterTo(mutableListOf(), predicate)
}

inline fun <C: MutableCollection<Quad>> Store.filterTo(collection: C, crossinline predicate: (Quad) -> Boolean): C {
    forEach { contender ->
        if (predicate(contender)) {
            collection.add(contender)
        }
    }
    return collection
}

inline fun <T> Store.map(crossinline mapper: (Quad) -> T): List<T> {
    return mapTo(mutableListOf(), mapper)
}

inline fun <C: MutableCollection<T>, T> Store.mapTo(collection: C, crossinline mapper: (Quad) -> T): C {
    forEach { collection.add(mapper(it)) }
    return collection
}

inline fun <T: Any> Store.mapNotNull(crossinline mapper: (Quad) -> T?): List<T> {
    return mapNotNullTo(mutableListOf(), mapper)
}

inline fun <C: MutableCollection<T>, T: Any> Store.mapNotNullTo(collection: C, crossinline mapper: (Quad) -> T?): C {
    forEach { collection.add(mapper(it) ?: return@forEach) }
    return collection
}
