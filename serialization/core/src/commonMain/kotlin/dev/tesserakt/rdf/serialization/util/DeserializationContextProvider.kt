package dev.tesserakt.rdf.serialization.util

import kotlin.jvm.JvmInline

interface DeserializationContextProvider {

    /**
     * Represents logic that defines what to include in a context window returned from [highlight]
     */
    interface ContextWindow {

        /**
         * Defines how far before the target position should be included. This will be called for every character
         *  preceding the target, with an increased [offset] and its corresponding [value], until `false` is
         *  returned (end of context window) or the buffer's data is exhausted
         */
        fun includeBefore(offset: Int, value: Char): Boolean

        /**
         * Defines how far after the target position should be included. This will be called for every character
         *  following the target, with an increased [offset] and its corresponding [value], until `false` is
         *  returned (end of context window) or the buffer's data is exhausted
         */
        fun includeAfter(offset: Int, value: Char): Boolean

        /**
         * A simple [ContextWindow] implementation that extends the context until first newline character `\n` is
         *  encountered, or [maxLength] has been reached w.r.t. the target position.
         */
        @JvmInline
        value class LineBased(
            /**
             * The max length preceding / following the target position
             */
            val maxLength: Int
        ): ContextWindow {
            override fun includeAfter(offset: Int, value: Char): Boolean {
                return value != '\n' && offset < maxLength
            }

            override fun includeBefore(offset: Int, value: Char): Boolean {
                return value != '\n' && offset < maxLength
            }
        }

    }

    val offset: Int

    val size: Int

    val capacity: Int

    fun highlight(index: Int = 0, context: ContextWindow = ContextWindow.LineBased(capacity / 2)): String? {
        return highlight(
            start = index,
            end = index + 1,
            context = context,
        )
    }

    /**
     * Returns a string that contains (part of) this buffer, with the character region [start]`..<`[end] marked.
     * The [context] parameter allows for fine-tuning of the surrounding context shown. This context is limited to
     *  what is available in the buffer.
     */
    fun highlight(
        start: Int,
        end: Int = start + 1,
        context: ContextWindow = ContextWindow.LineBased(capacity / 2)
    ): String? {
        require(start < end) { "Invalid region to highlight: $start >= $end" }
        if (size == 0) {
            return null
        }
        val startPos = offset + start
        val endPos = offset + end

        val (contextStart, contextEnd) = findContextWindow(
            indexStart = startPos,
            indexEnd = endPos,
            context = context,
        )

        return buildString {
            var offset = 0
            var length = end - start
            (contextStart ..< startPos).forEach { i ->
                when (val c = getRaw(i)) {
                    '\n' -> {
                        ++offset
                        append("\\n")
                    }
                    '\t' -> {
                        ++offset
                        append("\\t")
                    }
                    else -> {
                        append(c)
                    }
                }
            }
            (startPos ..< endPos).forEach { i ->
                when (val c = getRaw(i)) {
                    '\n' -> {
                        ++length
                        append("\\n")
                    }
                    '\t' -> {
                        ++length
                        append("\\t")
                    }
                    else -> {
                        append(c)
                    }
                }
            }
            (endPos .. contextEnd).forEach { i ->
                when (val c = getRaw(i)) {
                    '\n' -> {
                        append("\\n")
                    }
                    '\t' -> {
                        append("\\t")
                    }
                    else -> {
                        append(c)
                    }
                }
            }
            appendLine()
            repeat(startPos - contextStart + offset) {
                append(' ')
            }
            repeat(length) {
                append('^')
            }
        }
    }

    /**
     * Used to reconstruct a context segment. Should not be used to obtain data directly!
     */
    fun getRaw(index: Int): Char

    /**
     * Finds the start and end index of the data that represents a context window according to the requirements
     *  of the [context] object. The returned bounds are inclusive, and are at least [indexStart], [indexEnd].
     */
    fun findContextWindow(indexStart: Int, indexEnd: Int, context: ContextWindow): Pair<Int, Int>

}
