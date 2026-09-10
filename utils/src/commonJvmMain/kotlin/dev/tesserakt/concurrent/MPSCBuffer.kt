package dev.tesserakt.concurrent

import java.util.concurrent.atomic.AtomicInteger

/**
 * A simple Multi-Producer Single-Consumer buffer, offering elements (possibly out of order!) through a [push] [poll]
 *  API. For the opposite concurrent guarantees, see [SPMCBuffer]
 */
class MPSCBuffer<T : Any>: AutoCloseable {

    companion object {

        private const val LOWER_RANGE = 0xFFFF

        private const val UPPER_RANGE = 0xFFFF.inv()

    }

    /**
     * Whether the producer is still active. This is guaranteed to be false if an error was raised.
     */
    @Volatile
    var alive = true
        private set

    // in case a failure occurred, we re-throw it for every reader
    @Volatile
    private var error: Throwable? = null

    // we buffer up to 16 elements - we claim these per reader using our state below
    private val buffer = Array<Any?>(16) { null }
    // state tracking which slots are claimed by the writer / a reader thread:
    // the 16 MSBs indicate which slot have been written to, whilst the 16 LSBs indicate which are claimed for
    //  reading
    // this is stored in a single atomic variable, so write and read state changes are all processed atomically
    private val state = AtomicInteger(0)

    fun push(element: T) {
        val i = claimWriteSlot()
        if (i == -1) {
            return
        }
        buffer[i] = element
        // we mark this slot now as occupied in the high range, and remove it from the low range
        // can be achieved by simply xor-ing the offending bits, flipping their value
        var state = this.state.get()
        while (!this.state.compareAndSet(state, state xor ((1 shl (i + 16)) or (1 shl i)))) {
            spinLoopHint()
            state = this.state.get()
        }
    }

    fun close(error: Throwable) {
        this.error = error
        // we fully clear the state so the error is immediately reported and no reader ends up stuck
        this.state.set(0)
        alive = false
    }

    /**
     * Gets an element that was [push]ed by the producer, or `null` if the end was reached (and the input was
     *  closed). Throws any exception that might've been used to [close] this buffer.
     */
    fun poll(): T? {
        val i = claimReadSlot()
        if (i == -1) {
            val error = error
            if (error != null) {
                throw error
            }
            return null
        }
        // we claimed an index; we get its result and mark it available for writing again
        // followed by marking its slot available for both writing and reading again as well, as we got our result
        val next = buffer[i]
        this.state.unsetMask(((1 shl i) or (1 shl (i + 16))).inv())
        // and we can return the result
        @Suppress("UNCHECKED_CAST")
        return next as T
    }

    override fun close() {
        alive = false
        // we don't close the iterator here, this is the responsibility of the producer thread, as we don't have
        //  concurrency guarantees of source iterator
    }

    /**
     * Claims an index [0, 31], or returns `-1` if no slot could ever be obtained ([alive] is false)
     */
    private fun claimWriteSlot(): Int {
        // we have multiple writers, so we eagerly set the bit we claim high
        while (alive) {
            var state = this.state.get()
            // we await until we reach a state where the upper range and lower range have at least one zero in common
            // which indicate a slot that is not housing a value (upper range), nor was claimed for writing (lower range)
            var available = (state or (state shl 16)) and UPPER_RANGE
            while (alive && available == UPPER_RANGE) {
                spinLoopHint()
                state = this.state.get()
                available = (state or (state shl 16)) and UPPER_RANGE
            }
            if (available == UPPER_RANGE) {
                // ! alive
                return -1
            }
            // we know the first 16 bits have at least 1 zero - that's the one we're interested in
            // we can't use the lowest bit, as that's from the lowest range (`and UPPER_RANGE`)
            val i = available.inv().takeHighestOneBit().countTrailingZeroBits() - 16
            // we claim it in the lower range
            if (this.state.compareAndSet(state, state or (1 shl i))) {
                // claimed!
                return i
            }
            // another thread updated the state, so we have to check again for a valid index to use
            spinLoopHint()
        }
        return -1
    }

    /**
     * Claims an index [0, 31], or returns `-1` if no slot could ever be
     *  obtained ([error] is set or [alive] is false)
     */
    private fun claimReadSlot(): Int {
        // a valid read index is one that is marked high in the upper range (and should be unset in the lower range)
        var state = this.state.get()
        while (alive && state and UPPER_RANGE == 0) {
            // none available
            spinLoopHint()
            state = this.state.get()
        }
        if (!alive) {
            return -1
        }
        val i = (state and UPPER_RANGE).takeLowestOneBit().countTrailingZeroBits() - 16
        return i
    }

}

/**
 * Uses CAS to ensure all **high** bits in [mask] are also set in this integer's value
 */
private fun AtomicInteger.setMask(mask: Int) {
    var value = this.get()
    while (!this.compareAndSet(value, value or mask)) {
        spinLoopHint()
        value = this.get()
    }
}

/**
 * Uses CAS to ensure all **low** bits in [mask] are also **unset** in this integer's value
 */
private fun AtomicInteger.unsetMask(mask: Int) {
    var value = this.get()
    while (!this.compareAndSet(value, value and mask)) {
        spinLoopHint()
        value = this.get()
    }
}
