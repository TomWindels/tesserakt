@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.util.console

inline fun StylisedString.fit(exactLength: Int, overflow: String = "...", extend: String = " "): StylisedString =
    when {
        length < exactLength ->
            this + extend.repeat((exactLength - length / extend.length) + 1).substring(0, exactLength - length)
        else ->
            truncate(maxLength = exactLength, overflow = overflow)
    }

inline fun StylisedString.truncate(maxLength: Int, overflow: String = "..."): StylisedString =
    when {
        length <= maxLength -> this
        maxLength <= overflow.length -> StylisedString(overflow.substring(0, maxLength))
        else -> take(maxLength - overflow.length) + overflow
    }
