@file:Suppress("unused")

package dev.tesserakt.rdf.types

open class MutableStore: Store {

    interface Listener {
        fun onQuadAdded(quad: Quad)
        fun onQuadRemoved(quad: Quad)
    }

    // TODO: actually performant implementation
    // stored quads utilize set semantics, duplicates are not allowed
    final override val quads: MutableSet<Quad>

    protected val listeners = mutableListOf<Listener>()

    constructor(quads: Collection<Quad> = emptyList()) {
        this.quads = quads.toMutableSet()
    }

    constructor(store: Store) {
        this.quads = store.quads.toMutableSet()
    }

    open fun add(quad: Quad) {
        if (quads.add(quad)) {
            listeners.forEach {
                try {
                    it.onQuadAdded(quad)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to add `$quad`", e)
                }
            }
        }
    }

    open fun addAll(quads: Iterable<Quad>) {
        quads.forEach { this.quads.add(it) }
    }

    open fun remove(quad: Quad) {
        if (quads.remove(quad)) {
            listeners.forEach {
                try {
                    it.onQuadRemoved(quad)
                } catch (e: Throwable) {
                    // TODO: maybe rollback for local data and other listeners?
                    // TODO: better exception type, or return a result type?
                    throw RuntimeException("Failed to remove `$quad`", e)
                }
            }
        }
    }

    open fun removeAll(quads: Iterable<Quad>) {
        quads.forEach { this.quads.remove(it) }
    }

    open fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    open fun removeListener(listener: Listener) {
        check(listeners.remove(listener)) { "The provided listener (class name ${listener::class.simpleName}) was not registered!" }
    }

}
