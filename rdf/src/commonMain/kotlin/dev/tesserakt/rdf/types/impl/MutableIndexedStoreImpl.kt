package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodedQuadElement
import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

internal class MutableIndexedStoreImpl: IndexedStore, MutableStore {

    private data class QuadEntry(
        // the id of the entry; used to identify the relative position of two entries when traversing the collection
        var id: Int,
        // for linked list traversal and modification, in case a no-constraint iteration is required
        // these two entries have no guaranteed terms in common
        var prev: QuadEntry?,
        var next: QuadEntry?,
        // embedded value for the `EncodedQuad` it represents
        val s: Int,
        val p: Int,
        val o: Int,
        val g: Int,
        // the neighbouring values part of this store, having either their subject, predicate, object or graph value in
        //  common
        var nextS: QuadEntry?,
        var nextP: QuadEntry?,
        var nextO: QuadEntry?,
        var nextG: QuadEntry?,
    ) {
        fun toEncodedQuad(): EncodedQuad {
            return EncodedQuad(s, p, o, g)
        }
    }

    private data class Tail(
        // required for encoding context purposes
        val decoded: Quad.Element,
        var firstS: QuadEntry?,
        var firstP: QuadEntry?,
        var firstO: QuadEntry?,
        var firstG: QuadEntry?,
    )

    private sealed class Cursor(
        val tail: Tail,
        var pos: QuadEntry?
    ) {

        /**
         * The [QuadEntry.id] of the entry this cursor is currently pointing at (increases with subsequent calls
         *  to [next])
         *
         * Returns [Int.MAX_VALUE] in case this cursor has reached the end
         */
        val id: Int get() = pos?.id ?: Int.MAX_VALUE

        /**
         * The next position this cursor will point at after having called [next]
         */
        abstract val next: QuadEntry?

        /**
         * Advances the cursor. Does nothing if [pos] is already `null`
         */
        fun next() {
            pos = next
        }

    }

    private class SubjectCursor(
        tail: Tail,
        pos: QuadEntry? = tail.firstS,
    ) : Cursor(tail, pos) {
        override val next: QuadEntry?
            get() = pos?.nextS
    }

    private class PredicateCursor(
        tail: Tail,
        pos: QuadEntry? = tail.firstP,
    ) : Cursor(tail, pos) {
        override val next: QuadEntry?
            get() = pos?.nextP
    }

    private class ObjectCursor(
        tail: Tail,
        pos: QuadEntry? = tail.firstO,
    ) : Cursor(tail, pos) {
        override val next: QuadEntry?
            get() = pos?.nextO
    }

    private class GraphCursor(
        tail: Tail,
        pos: QuadEntry? = tail.firstG,
    ) : Cursor(tail, pos) {
        override val next: QuadEntry?
            get() = pos?.nextG
    }

    private class SingleCursorIterator(
        private val cursor: Cursor,
    ): Iterator<EncodedQuad> {

        private var next: EncodedQuad? = cursor.pos?.toEncodedQuad()

        override fun hasNext(): Boolean {
            next = next ?: getNext()
            return next != null
        }

        override fun next(): EncodedQuad {
            val n = next ?: getNext()
            next = null
            return n ?: throw NoSuchElementException()
        }

        private fun getNext(): EncodedQuad? {
            cursor.next()
            return cursor
                .pos
                ?.toEncodedQuad()
        }

    }

    private inline fun Tail.toSubjectCursor() = SubjectCursor(this)
    private inline fun Tail.toPredicateCursor() = PredicateCursor(this)
    private inline fun Tail.toObjectCursor() = ObjectCursor(this)
    private inline fun Tail.toGraphCursor() = GraphCursor(this)

    // the most recently added element
    private var tail: QuadEntry? = null
    // the most recently added element, by encoded quad element type
    private val tails = mutableMapOf<Int, Tail>()

    override fun encodedIterator(): MutableIterator<EncodedQuad> {
        val sIter = tails.iterator()

        if (!sIter.hasNext()) {
            return emptyIterator()
        }

        return object : MutableIterator<EncodedQuad> {

            private var previous: EncodedQuad? = null
            private var next = tail

            override fun next(): EncodedQuad {
                val current = next ?: throw NoSuchElementException()
                val element = current.toEncodedQuad()
                previous = element
                next = current.next
                return current.toEncodedQuad()
            }

            override fun hasNext(): Boolean {
                return next != null
            }

            override fun remove() {
                remove(previous ?: throw NoSuchElementException())
                previous = null
            }

        }

    }

    override fun encodedIter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> {
        val cursors = listOfNotNull(
            if (s != Int.MIN_VALUE) {
                val tail = tails[s] ?: return emptyIterator()
                tail.toSubjectCursor()
            } else null,
            if (p != Int.MIN_VALUE) {
                val tail = tails[p] ?: return emptyIterator()
                tail.toPredicateCursor()
            } else null,
            if (o != Int.MIN_VALUE) {
                val tail = tails[o] ?: return emptyIterator()
                tail.toObjectCursor()
            } else null,
            if (g != Int.MIN_VALUE) {
                val tail = tails[g] ?: return emptyIterator()
                tail.toGraphCursor()
            } else null,
        )
        return when (cursors.size) {
            0 -> {
                encodedIterator()
            }
            1 -> {
                SingleCursorIterator(cursors[0])
            }
            2 -> {
                TODO()
            }
            3 -> {
                TODO()
            }
            else /* 4 */ -> {
                // yields at most one element
                TODO()
            }
        }
    }

    private fun remove(quad: EncodedQuad): Boolean {
        // has to be implemented like this as all chains have to be updated, as well as their tails if necessary
        val s = (tails[quad.s] ?: return false).toSubjectCursor()
        val p = (tails[quad.p] ?: return false).toPredicateCursor()
        val o = (tails[quad.o] ?: return false).toObjectCursor()
        val g = (tails[quad.g] ?: return false).toGraphCursor()
        // edge case: there is at least one cursor already pointing at our destination (meaning there's no prior
        //  element and that chain's tail has to be updated instead)
        // TODO ^

        // we have to advance all cursors until they all have their *next ID* match
        // when they do, they all have our target entry set as their next respective entry
        // advancing s, p, o and g until they all settle on the same entry ID
        // we know advancing along a cursor increases the ID of the entry it points at, so we navigate the
        //  cursor with the smallest ID in case of a mismatch

        // making sure `s` `p` match
        sp@ while (true) {
            val nextS = s.next.let {
                if (it == null) {
                    return false
                }
                it.id
            }
            val nextP = p.next.let {
                if (it == null) {
                    return false
                }
                it.id
            }
            if (nextS > nextP) {
                p.next()
                // we have to continue searching
                continue@sp
            } else if (nextP > nextS) {
                s.next()
                // we have to continue searching
                continue@sp
            }
            // we've reached here, meaning that `nextS` and `nextP` have settled;
            //  we now have to make sure `nextO` settles with the other two
            po@ while (true) {
                val nextO = o.next.let {
                    if (it == null) {
                        return false
                    }
                    it.id
                }
                if (nextP > nextO) {
                    o.next()
                    // we have to continue searching for the next object match
                    continue@po
                } else if (nextO > nextP) {
                    // the `s` `p` pair haven't advanced far enough, so we push them both, letting them settle
                    //  on the next match
                    s.next()
                    p.next()
                    continue@sp
                }
                // now we only have to settle on the graph
                og@ while (true) {
                    val nextG = g.next.let {
                        if (it == null) {
                            return false
                        }
                        it.id
                    }
                    if (nextO > nextG) {
                        g.next()
                        // we have to continue searching for the next graph match
                        continue@og
                    } else if (nextG > nextO) {
                        // the `s` `p` and `o` pairs haven't advanced far enough, so we push them all, letting them
                        //  settle on the next match
                        s.next()
                        p.next()
                        o.next()
                        // back up top
                        continue@sp
                    }
                    // they now all match, so we can break out of the outermost loop!
                    break@sp
                }
            }
        }
        // as we've reached here, we've settled all cursors to point to the one-before target that needs to be removed
        TODO()
    }

}
