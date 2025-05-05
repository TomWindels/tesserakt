package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream
import dev.tesserakt.rdf.serialization.core.read

@InternalSerializationApi
class BufferedString(
    private val reader: DataSourceStream,
    private val bufferSize: Int = 4000
) {

    private val threshold = bufferSize / 2
    private var elapsed = 0
    private var i = 0
    private var buf: String? = reader.read(bufferSize)

    /**
     * Reads the current top + [offset] character (cannot be negative!), returning `null` if EOF has been reached
     */
    fun peek(offset: Int = 0): Char? {
        return buf?.getOrNull(i + offset)
    }

    fun consume(count: Int = 1) {
        // if it fits in the first half of what has been read in, nothing has to be done
        if (count + i < threshold) {
            i += count
            return
        }
        // if it fits in the second half of what has been read in, the buffer gets shifted halfway
        if (count + i < bufferSize) {
            // trying to read the rest
            val next = reader.read(threshold) ?: run {
                // nothing else to read, so only updating the index
                i += count
                return
            }
            // dropping half of the old buffer and reading half extra
            elapsed += threshold
            i = i - threshold + count
            buf = buf!!.drop(threshold) + next
            return
        }
        // it doesn't fit in this buffer at all, continuing to rotate until destination reached
        next()
        if (buf == null) {
            return
        }
        var remaining = count - bufferSize + i
        while (remaining >= bufferSize) {
            next()
            remaining -= bufferSize
            if (buf == null) {
                return
            }
        }
        i = remaining
        // if we ended halfway the buffer, we can already rotate once more
        if (i > threshold) {
            // trying to continue reading, and if successful, advancing the indexing state
            val next = reader.read(threshold) ?: return
            // dropping half of the old buffer and reading half extra
            elapsed += threshold
            i -= threshold
            buf = buf!!.drop(threshold) + next
        }
    }

    private fun next() {
        elapsed += bufferSize
        buf = reader.read(bufferSize)
    }

    fun report(indicator: String, message: String): String {
        val start = buf!!.lastIndexOf('\n', i)
        val end = buf!!.indexOf('\n', i)
        val line1: String
        val line2: String
        when {
            start == -1 && end == -1 -> {
                line1 = buf!!
                line2 = indicator
            }
            start == -1 -> {
                line1 = buf!!.take(end)
                line2 = indicator
            }
            end == -1 -> {
                line1 = buf!!.substring(start + 1)
                line2 = " ".repeat(i - start - 1) + indicator
            }
            else -> {
                line1 = buf!!.substring(start + 1, end)
                line2 = " ".repeat(i - start - 1) + indicator
            }
        }
        return "$line1\n$line2 - $message"
    }

    fun index(): Int {
        return elapsed + i
    }

    fun substring(start: Int, end: Int): String {
        return buf!!.substring(i + start, i + end)
    }

    fun startsWith(other: String, ignoreCase: Boolean = false): Boolean {
        var i = 0
        return if (ignoreCase) {
            other.all { it.equals(peek(i++) ?: return false, ignoreCase = true) }
        } else {
            other.all { peek(i++) == it }
        }
    }

}
