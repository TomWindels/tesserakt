package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import java.io.InputStreamReader


@InternalSerializationApi
class FileCharStream(
    private val source: InputStreamReader,
): BufferedCharStream, DeserializationContextProvider {

    private val buf = CharArray(8192)
    private var head = 0
    private var tail = 0

    override val size get() = tail - head

    private var eof = false

    override val capacity: Int
        get() = buf.size

    override val offset: Int
        get() = head

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
        // we continue reading, appending to our initial value of the scratch buffer
        return consumeAndDecodeWithoutWhitespaceUntilUsingScratchBuffer(delimiter)
    }

    override fun consumeUntilWhitespace(): String {// if we have 1 continuous chunk of our inner buffer that we can copy straight into the resulting string,
        // without having to alter the contents (no decoding necessary), we can skip the copy to scratch directly
        ensureBufferSize(1)
        var i = head
        while (i < tail) {
            val c = buf[i]
            if (c.isWhitespace()) {
                // happy path: we can copy our buffer straight into the string, and consider these characters consumed
                val result = String(buf, head, i - head)
                head = i
                return result
            }
            ++i
        }
        // if we reached here, we've reached the end of the buffer
        // this requires us to use the scratch buffer, with all characters we've read up until this point
        //  consumed
        scratch.setLength(0)
        scratch.appendRange(buf, head, i)
        // we now consider them consumed, and continue
        head = i
        ensureBufferSize(1)
        // we continue reading, appending to our initial value of the scratch buffer
        return consumeUntilWhitespaceUsingScratchBuffer()
    }

    override fun consumeWhile(predicate: (Char) -> Boolean): String {
        // without having to alter the contents (no decoding necessary), we can skip the copy to scratch directly
        ensureBufferSize(1)
        var i = head
        while (i < tail) {
            val c = buf[i]
            if (!predicate(c)) {
                // happy path: we can copy our buffer straight into the string, and consider these characters consumed
                val result = String(buf, head, i - head)
                head = i
                return result
            }
            ++i
        }
        // if we reached here, we've reached the end of the buffer
        // this requires us to use the scratch buffer, with all characters we've read up until this point
        //  consumed
        scratch.setLength(0)
        scratch.appendRange(buf, head, i)
        // we now consider them consumed, and continue
        head = i
        ensureBufferSize(1)
        // we continue reading, appending to our initial value of the scratch buffer
        return consumeUntilUsingScratchBuffer(predicate)
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
        return buf[head + offset]
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
        return highlight(index)?.prependIndent("| ") ?: "No report available"
    }

    override fun report(start: Int, end: Int): String {
        return highlight(start, end)?.prependIndent("| ") ?: "No report available"
    }

    override fun getRaw(index: Int): Char {
        // we aren't circular, so direct lookup
        return buf[index]
    }

    override fun findContextWindow(indexStart: Int, indexEnd: Int, context: DeserializationContextProvider.ContextWindow): Pair<Int, Int> {
        var startOffset = 0
        var endOffset = 0

        while (
            indexStart - startOffset > 0 &&
            // making sure there's actual data here
            buf[indexStart - startOffset].code != 0 &&
            context.includeBefore(startOffset, buf[indexStart - startOffset])
        ) {
            ++startOffset
        }

        while (
            indexEnd + endOffset < tail &&
            context.includeAfter(endOffset, buf[indexEnd + endOffset])
        ) {
            ++endOffset
        }

        return (indexStart - startOffset + 1) to (indexEnd + endOffset - 1)
    }

    /* helpers */

    private fun consumeAndDecodeWithoutWhitespaceUntilUsingScratchBuffer(delimiter: Char): String {
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
                    // we also need to ensure the buffer contains enough characters for us to get the entire
                    //  code sequence into memory
                    ensureBufferSize(10)
                    val codePoint = consumeAndDecodeNumericEscape()
                    scratch.appendCodePoint(codePoint)
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

    private fun consumeUntilWhitespaceUsingScratchBuffer(): String {
        if (size == 0) {
            bail("Unexpected EOF reached! No data remaining!")
        }
        while (true) {
            var i = head
            while (i < tail) {
                if (buf[i].isWhitespace()) {
                    scratch.appendRange(buf, head, i)
                    head = i
                    return scratch.toString()
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

    private fun consumeUntilUsingScratchBuffer(predicate: (Char) -> Boolean): String {
        if (size == 0) {
            bail("Unexpected EOF reached! No data remaining!")
        }
        while (true) {
            var i = head
            while (i < tail) {
                if (!predicate(buf[i])) {
                    scratch.appendRange(buf, head, i)
                    head = i
                    return scratch.toString()
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

    /**
     * Checks the buffer for a `\u` or `\U` sequence, bailing if not found. Advances the [head] past the encoded escape
     *  sequence, returning the decoded value
     */
    private fun consumeAndDecodeNumericEscape(): Int {
        val i = head
        check(buf[i] == '\\')
        return when (buf[i + 1]) {
            'u' -> {
                check(size > 6)
                val codePoint = EscapeSequenceHelper.hexToInt(
                    one = buf[i + 2],
                    two = buf[i + 3],
                    three = buf[i + 4],
                    four = buf[i + 5],
                )
                // consumed
                head = i + 6
                codePoint
            }
            'U' -> {
                check(size > 10)
                val codePoint = EscapeSequenceHelper.hexToInt(
                    one = buf[i + 2],
                    two = buf[i + 3],
                    three = buf[i + 4],
                    four = buf[i + 5],
                    five = buf[i + 6],
                    six = buf[i + 7],
                    seven = buf[i + 8],
                    eight = buf[i + 9],
                )
                // consumed
                head = i + 10
                codePoint
            }
            else -> {
                bail("Invalid escape sequence encountered!", 0..1)
            }
        }
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
