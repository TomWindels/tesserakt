package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

@OptIn(InternalSerializationApi::class)
internal class StringQueueStream(start: String): DataStream {

    private class Element(
        var value: String,
        var next: Element? = null,
    )

    // number of elements
    var count = 1
        private set
    private var head: Element? = Element(value = start, next = null)
    private var tail: Element? = head

    fun enqueue(element: String) {
        val currentTail = tail
            ?: throw IllegalStateException("Trying to enqueue a new element whilst the stream has already finished!")
        val new = Element(value = element, next = null)
        currentTail.next = new
        tail = new
        ++count
    }

    override fun read(target: CharArray, offset: Int, count: Int): Int {
        val currentHead = head ?: return -1
        return if (currentHead.value.length <= count) {
            // consuming the entire node
            val length = currentHead.value.length
            currentHead.value.toCharArray(
                destination = target,
                destinationOffset = offset,
                startIndex = 0,
                endIndex = length,
            )
            // shifting one over
            head = currentHead.next
            --this.count
            length
        } else {
            // partially consuming the node
            currentHead.value.toCharArray(
                destination = target,
                destinationOffset = offset,
                startIndex = 0,
                endIndex = count,
            )
            // replacing the value with a subset only
            currentHead.value = currentHead.value.drop(count)
            count
        }
    }

    override fun close() {
        head = null
        tail = null
    }

    override fun toString() = "StringQueueStream { count: $count }"

}
