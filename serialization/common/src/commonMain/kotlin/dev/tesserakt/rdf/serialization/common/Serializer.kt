package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.SuspendingIterator
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.SuspendingDataStream
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

abstract class Serializer {

    /**
     * Parses the data obtained from the [DataStream] as long as the resulting [Quad]s are being consumed
     *  using [Iterator.next]. The opened [DataStream] is [DataStream.close]d when no more [Quad]s are available or
     *  a [DeserializationException] is thrown.
     *
     * If deserialization is no longer required before reaching the end or an error was encountered, [close] has to be
     *  called manually so the underlying [DataStream] can be [DataStream.close]d.
     *
     * A common usage pattern is deserializing into a [Store]. This can be done using
     *  the [dev.tesserakt.rdf.types.toStore] extension method:
     * ```kt
     * val source = /* create file source, text source, ... */
     * val store = serializer(/* format */).deserialize(source).toStore()
     * ```
     *
     * Alternatively, the overload taking in a [dev.tesserakt.rdf.types.MutableStore] implementor can be used if the
     *  resulting [Quad]s have to be added to an existing store:
     * ```kt
     * val store = /* existing (mutable!) store */
     * val source = /* create file source, text source, ... */
     * serializer(/* format */).deserialize(source).toStore(store)
     * ```
     */
    class DeserializationProcess internal constructor(
        @OptIn(InternalSerializationApi::class)
        private val source: DataStream,
        private val inner: Iterator<Quad>
    ): Iterator<Quad>, AutoCloseable {

        private var curr: Quad? = null

        override fun hasNext(): Boolean {
            if (curr != null) {
                return true
            }
            curr = getNext()
            return curr != null
        }

        override fun next(): Quad {
            val next = curr
                ?: getNext()
                ?: throw NoSuchElementException()
            curr = null
            return next
        }

        override fun close() {
            @OptIn(InternalSerializationApi::class)
            source.close()
        }

        private fun getNext(): Quad? = try {
            if (inner.hasNext()) {
                inner.next()
            } else {
                close()
                null
            }
        } catch (t: Throwable) {
            close()
            throw DeserializationException("Deserialization failed!", t)
        }

    }

    /**
     * Parses the data obtained from the [SuspendingDataStream] as long as the resulting [Quad]s are being consumed
     *  using [Iterator.next]. The opened [SuspendingDataStream] is [SuspendingDataStream.close]d when no more [Quad]s
     *  are available or a [DeserializationException] is thrown.
     *
     * If deserialization is no longer required before reaching the end or an error was encountered, [close] has to be
     *  called manually so the underlying [DataStream] can be [DataStream.close]d.
     *
     * A common usage pattern is deserializing into a [Store]. This can be done using
     *  the [dev.tesserakt.rdf.types.toStore] extension method:
     * ```kt
     * val source = /* suspending source */
     * val store = serializer(/* format */).deserialize(source).toStore()
     * ```
     *
     * Alternatively, the overload taking in a [dev.tesserakt.rdf.types.MutableStore] implementor can be used if the
     *  resulting [Quad]s have to be added to an existing store:
     * ```kt
     * val store = /* existing (mutable!) store */
     * val source = /* suspending store */
     * serializer(/* format */).deserialize(source).toStore(store)
     * ```
     */
    class SuspendingDeserializationProcess internal constructor(
        @OptIn(InternalSerializationApi::class)
        private val source: SuspendingDataStream,
        private val inner: Iterator<Quad>
    ): SuspendingIterator<Quad>, AutoCloseable {

        private var curr: Quad? = null

        override suspend fun hasNext(): Boolean {
            if (curr != null) {
                return true
            }
            curr = getNext()
            return curr != null
        }

        override suspend fun next(): Quad {
            val next = curr
                ?: getNext()
                ?: throw NoSuchElementException()
            curr = null
            return next
        }

        override fun close() {
            @OptIn(InternalSerializationApi::class)
            source.close()
        }

        private suspend fun getNext(): Quad? = try {
            @OptIn(InternalSerializationApi::class)
            source.prepare()
            if (inner.hasNext()) {
                inner.next()
            } else {
                close()
                null
            }
        } catch (t: Throwable) {
            close()
            throw DeserializationException("Deserialization failed!", t)
        }

    }

    /**
     * Standard serializer. Can use the [store]s [Store.iter] to optimise the resulting output.
     *
     * The result is an iterator of [String] snippets making up the entire serialized result.
     */
    open fun serialize(store: Store): Iterator<String> {
        return serialize(data = store.iterator())
    }

    /**
     * The most basic form of serialization. The [data] is being iterated over once: the order of elements is not
     *  altered, and will be reflected in the output.
     */
    abstract fun serialize(data: Iterator<Quad>): Iterator<String>

    /**
     * Standard deserialization. Opens a new [DataStream] from the provided [input]. See [DeserializationProcess] for
     *  more information.
     *
     * NOTE: not consuming this iterator will leave the [DataStream] opened, and is therefore **not** recommended, as
     *  it is possible for a stream to originate from a file or other resource that should be closed! In these cases,
     *  manually invoking [DeserializationProcess.close] is required.
     *
     * Can throw [DeserializationException] if an error occurs, upon which the [DataStream] will be closed.
     */
    fun deserialize(input: DataSource): DeserializationProcess = try {
        @OptIn(InternalSerializationApi::class)
        val source = input.open()
        @OptIn(InternalSerializationApi::class)
        DeserializationProcess(
            source = source,
            inner = deserialize(source),
        )
    } catch (t: Throwable) {
        throw DeserializationException("Failed to initiate deserialization", t)
    }

    /**
     * Standard deserialization. Opens a new [SuspendingDataStream] from the provided [input].
     *  See [SuspendingDeserializationProcess] for more information.
     *
     * NOTE: not consuming this iterator will leave the [SuspendingDataStream] opened, and is therefore **not**
     *  recommended, as it is possible for a stream to originate from a file or other resource that should be closed!
     *  In these cases, manually invoking [SuspendingDeserializationProcess.close] is required.
     *
     * Can throw [DeserializationException] if an error occurs, upon which the [SuspendingDataStream] will be closed.
     */
    suspend fun deserialize(input: SuspendingDataSource): SuspendingDeserializationProcess = try {
        @OptIn(InternalSerializationApi::class)
        val source = input.open()
        @OptIn(InternalSerializationApi::class)
        SuspendingDeserializationProcess(
            source = source,
            inner = deserialize(source),
        )
    } catch (t: Throwable) {
        throw DeserializationException("Failed to initiate deserialization", t)
    }

    /**
     * Standard deserialization. The [input] is iterated over once, as long as the returned [Iterator] is being consumed.
     */
    @OptIn(InternalSerializationApi::class)
    protected abstract fun deserialize(input: DataStream): Iterator<Quad>

}
