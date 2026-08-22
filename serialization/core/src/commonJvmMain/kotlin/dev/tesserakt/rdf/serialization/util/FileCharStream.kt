package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import java.io.InputStreamReader


@InternalSerializationApi
class FileCharStream(
    private val source: InputStreamReader,
): BufferedCharStream {

    private val buf = CharArray(8192)
    private var head = 0
    private var tail = 0
    private val size get() = tail - head
    private var eof = false

    // used in `consumeAndDecodeWithoutWhitespaceUntil`
    private val scratch = StringBuilder(200)

    override fun consume(count: Int) {
        check(count <= size)
        head += count
    }

    override fun consumeAndDecodeWithoutWhitespaceUntil(
        delimiter: Char,
    ): String {
        // if we have 1 continuous chunk of our inner buffer that we can copy straight into the resulting string,
        //  without having to alter the contents (no decoding necessary), we can skip the copy to scratch directly
        ensureBufferSize(1)
        var i = head
        while (i < tail) {
            val c = buf[i]
            if (c == delimiter) {
                // happy path: we can copy our buffer straight into the string, and consider these characters consumed
                val result = String(buf, head, i - head)
                head = i
                return result
            } else if (c.isWhitespace()) {
                // we haven't consumed the bad character yet, but we also have to highlight it, so range ends at `1`
                bail("Invalid character encountered: `$c`", -scratch.length .. 1)
            } else if (c == '\\') {
                // unhappy path: we will likely have to decode our input, so we need to use the scratch buffer
                break
            }
            ++i
        }
        // if we reached here, we're either looking at a `\\` character, or we've reached the end of the buffer
        // both cases require us to use the scratch buffer, with all characters we've read up until this point
        //  consumed
        scratch.setLength(0)
        scratch.appendRange(buf, head, i)
        // we now consider them consumed, and continue
        head = i
        ensureBufferSize(1)
        if (size == 0) {
            bail("Unexpected EOF reached! No data remaining!")
        }
        while (true) {
            var i = head
            while (i < tail) {
                val c = buf[i]
                if (c.isWhitespace()) {
                    // we haven't consumed the bad character yet, but we also have to highlight it, so range ends at `1`
                    bail("Invalid character encountered: `$c`", -scratch.length .. 1)
                } else if (c == delimiter) {
                    scratch.appendRange(buf, head, i)
                    head = i
                    return scratch.toString()
                } else if (c == '\\') {
                    // we flush here, and ensure we have more characters loaded into memory
                    scratch.appendRange(buf, head, i)
                    head = i
                    // we also need to look ahead a bit more, if possible
                    ensureBufferSize(10)
                    val next = buf[i + 1]
                    if (next == 'u') {
                        TODO()
                        repeat(4) {
                            bail("Unexpected EOF reached! Last received data: `$scratch`")
                        }
                        EscapeSequenceHelper.decodeShortNumericEscapeAtTail(scratch)
                    } else if (next == 'U') {
                        TODO()
                        repeat(8) {
                            bail("Unexpected EOF reached! Last received data: `$scratch`")
                        }
                        EscapeSequenceHelper.decodeLongNumericEscapeAtTail(scratch)
                    }
                    // as we mutated the buffer, we have to get out of this loop iteration
                    break
                }
                ++i
            }
            // flushing everything we currently have and resetting our position
            scratch.appendRange(buf, head, i)
            head = i
            ensureBufferSize(1)
            if (size == 0) {
                bail("Unexpected EOF reached! Got `$scratch`")
            }
            // the iterator gets as we're going back up top
        }
    }

    override fun peek(): Char? {
        ensureBufferSize(1)
        if (size == 0) {
            return null
        }
        return buf[head]
    }

    override fun peek(offset: Int): Char? {
        ensureBufferSize(offset + 1)
        if (size < offset) {
            return null
        }
        // TODO remove try catch
        return try {
            buf[head + offset]
        } catch (t: Throwable) {
            throw IllegalStateException("head=$head, tail=$tail, size=$size, offset=$offset", t)
        }
    }

    override fun pop(): Char? {
        val c = peek()
        if (c != null) {
            consume()
        }
        return c
    }

    override fun close() {
        source.close()
        eof = true
    }

    override fun report(index: Int): String {
        TODO("Not yet implemented")
    }

    override fun report(start: Int, end: Int): String {
        TODO("Not yet implemented")
    }

    private fun ensureBufferSize(targetSize: Int) {
        if (this.size >= targetSize) {
            return
        }
        check(targetSize <= buf.size)
        if (head == tail) {
            head = 0
            tail = 0
        } else if (head != 0 && buf.size - head < targetSize) {
            // we need to shift the head .. tail section to the front of the buffer, as otherwise the buffer is too
            //  small to fit the requested size into
            buf.copyInto(
                destination = buf,
                destinationOffset = 0,
                startIndex = head,
                endIndex = tail,
            )
            // with the buffer now updated, we can shift our pointers back
            tail -= head
            head = 0
        }
        while (!eof && this.size < targetSize) {
            val read = source.read(buf, tail, buf.size - tail)
            if (read < 0) {
                eof = true
            } else {
                tail += read
            }
        }
    }

}
