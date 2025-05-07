package dev.tesserakt.rdf.types

import dev.tesserakt.util.RWLock
import dev.tesserakt.util.withReadLock
import dev.tesserakt.util.withWriteLock

class ConcurrentMutableStore: MutableStore {

    private val lock = RWLock()

    constructor(quads: Collection<Quad> = emptyList()): super(quads)

    constructor(store: Store): super(store)

    override val size: Int
        get() = lock.withReadLock { super.size }

    override fun contains(element: Quad): Boolean = lock.withReadLock { super.contains(element) }

    override fun containsAll(elements: Collection<Quad>): Boolean = lock.withReadLock { super.containsAll(elements) }

    override fun forEach(block: (Quad) -> Unit) = lock.withReadLock { super.forEach(block) }

    override fun first() = lock.withReadLock { quads.first() }

    override fun last() = lock.withReadLock { quads.last() }

    /**
     * A special variant of the [forEach] method, capable of halting execution prematurely when [block] returns `true`.
     * The behaviour is identical to the regular [forEach] method when always returning `false`.
     */
    override fun forEachUntil(block: (Quad) -> Boolean) = lock.withReadLock {
        quads.forEach {
            if (block(it)) {
                return
            }
        }
    }

    override fun add(quad: Quad) {
        val added = lock.withWriteLock { quads.add(quad) }
        if (added) {
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

    override fun addAll(quads: Iterable<Quad>) {
        lock
            .withWriteLock { quads.filter { this.quads.add(it) } }
            .forEach { added ->
                listeners.forEach {
                    try {
                        it.onQuadAdded(added)
                    } catch (e: Throwable) {
                        // TODO: maybe rollback for local data and other listeners?
                        // TODO: better exception type, or return a result type?
                        throw RuntimeException("Failed to add `$added`", e)
                    }
                }
            }
    }

    override fun remove(quad: Quad) {
        val removed = lock.withWriteLock { quads.remove(quad) }
        if (removed) {
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

    override fun removeAll(quads: Iterable<Quad>) {
        lock
            .withWriteLock { quads.filter { this.quads.remove(it) } }
            .forEach { added ->
                listeners.forEach {
                    try {
                        it.onQuadRemoved(added)
                    } catch (e: Throwable) {
                        // TODO: maybe rollback for local data and other listeners?
                        // TODO: better exception type, or return a result type?
                        throw RuntimeException("Failed to remove `$added`", e)
                    }
                }
            }
    }

}
