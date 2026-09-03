package dev.tesserakt.concurrent

import dev.tesserakt.util.SimpleList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.math.min

private val CPU_COUNT = Runtime.getRuntime().availableProcessors()

/**
 * A very simple append-only structure that returns the index of the newly-inserted item.
 *
 * The simple contract allows for concurrent access, using a short spin lock during specific insertions.
 */
@OptIn(ExperimentalAtomicApi::class)
class SimpleConcurrentList<T>(
    initialCapacity: Int = 10
): SimpleList<T> {

    // internal for testing purposes
    internal object Unused

    private val _size = AtomicInt(0)

    override val size get() = _size.load()

    // internal for testing purposes
    @Volatile
    internal var _buf = Array<Any?>(initialCapacity) { Unused }

    /**
     * Adds the [element] to the back of this list. Returns the index that can be used to [get] the inserted element.
     */
    override fun add(element: T): Int {
        val i = _size.fetchAndIncrement()
        val buf = prepareBufferForElementAt(i)
        buf[i] = element
        return i
    }

    override operator fun get(index: Int): T {
        // size only ever increases
        val buf = _buf
        // want to avoid the case where an `add` is still ongoing, meaning that `buf[i]` has not been set yet,
        //  even though our size has incremented
        if (index < 0 || index >= _size.load() || index >= buf.size) {
            throw ArrayIndexOutOfBoundsException()
        }
        val ele = buf[index]
        // shouldn't happen, but we don't want to leak the object
        if (ele === Unused) {
            throw ArrayIndexOutOfBoundsException()
        }
        @Suppress("UNCHECKED_CAST")
        return ele as T
    }

    override fun clear() {
        // we make sure the array is fully replaced, whilst the size is also forced back to 0
        //  if an add is in progress, its result will be discarded, as the size will also be set back to 0
        // with the size set to 0 first, we only risk replacing ('corrupting') the old `_buf`
        _size.store(0)
        // we now fully replace the buf, so the original memory can be reclaimed
        _buf = Array(10) { Unused }
        // and can now ensure the size is set to 0; if an add was already in progress, we consider that value also
        //  discarded
        _size.store(0)
    }

    /**
     * Returns an [Array] that can be indexed at [targetIndex] (which might be set to [Unused])
     *
     * Spin-locks in case the capacity needs to grow (or is being expanded)
     */
    private fun prepareBufferForElementAt(targetIndex: Int): Array<Any?> {
        // three possible cases:
        // * the target capacity is smaller than what we currently have room for, so we can return our buf
        // * the target capacity matches what we currently have room for, so we wait for all read operations to finish
        //  and replace it with a larger buffer
        // * the target capacity is larger than what we currently have, meaning we have another thread already
        //  actively resizing, and we need to wait for it to finish, so we can re-evaluate
        val buf = _buf
        return when {
            targetIndex < buf.size -> {
                buf
            }
            targetIndex == buf.size -> {
                onBufferResizeRequired(targetIndex)
            }
            else /* targetIndex > buf.size */ -> {
                onOngoingBufferResize(targetIndex)
            }
        }
    }

    private fun onOngoingBufferResize(size: Int): Array<Any?> {
        var b = _buf
        // as long as our buffer is smaller than the index we try to reach, we have another thread getting us a new
        //  buffer
        while (b.size < size) {
            spinLoopHint()
            b = _buf
        }
        // edge case: b.size == size, meaning that we've actually been trying to add elements far enough ahead
        //  that we now have to allocate a new buffer again
        if (b.size == size) {
            return onBufferResizeRequired(size)
        }
        return b
    }

    private fun onBufferResizeRequired(size: Int): Array<Any?> {
        // we need to allocate a new array, and migrate old data to this new array
        // however, it's possible the existing array is still getting its elements added (e.g. if another thread
        //  suspended during `add()`), so we need to check the contents of the existing array before we clone to the
        //  new one
        // there's no point in 'waiting' for the existing array to be filled if our capacity isn't marked as being
        //  occupied
        check(this._size.load() >= _buf.size)
        // we check up to CPU_COUNT elements back in the array, as that's the max number of threads that could
        //  be stuck adding an element
        val n = min(CPU_COUNT, _buf.size)
        val range = _buf.size - n ..< _buf.size
        while (range.any { _buf[it] === Unused }) {
            spinLoopHint()
        }
        @OptIn(ExperimentalStdlibApi::class)
        val new = _buf.copyOf(
            // we want to limit our growth rate, as we otherwise grow exponentially at rates that reach OOM quite quickly
            newSize = size + (size shr 1).coerceAtMost(20_000) + 1,
            // marking the new spots as unused, so a subsequent resize event makes sure these elements are in fact
            //  overwritten
            init = { Unused },
        )
        _buf = new
        return new
    }

}
