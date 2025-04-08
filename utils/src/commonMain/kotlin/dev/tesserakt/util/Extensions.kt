@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.tesserakt.util

// TODO: move these to an `implementation()` `core` module

inline fun String.fit(exactLength: Int, overflow: String = "...", extend: String = " "): String =
    when {
        length < exactLength ->
            this + extend.repeat((exactLength - length / extend.length) + 1).substring(0, exactLength - length)
        else ->
            truncate(maxLength = exactLength, overflow = overflow)
    }

inline fun Any?.toString(exactLength: Int, overflow: String = "...", extend: String = " "): String {
    return toString().fit(exactLength = exactLength, overflow = overflow, extend = extend)
}

inline fun String.truncate(maxLength: Int, overflow: String = "..."): String =
    when {
        length <= maxLength -> this
        maxLength <= overflow.length -> overflow.substring(0, maxLength)
        else -> substring(0, maxLength - overflow.length) + overflow
    }

inline fun Any?.toTruncatedString(maxLength: Int, overflow: String = "..."): String {
    return toString().truncate(maxLength, overflow)
}

/**
 * Returns `true` when [T] is `null`, or the result when evaluating [condition] with non-null [T]
 */
inline fun <T: Any> T?.isNullOr(condition: (T) -> Boolean): Boolean = this == null || condition(this)
